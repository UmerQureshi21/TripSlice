"""Retrieval, storage and the TwelveLabs wrapper, with the SDK and database faked out."""

from types import SimpleNamespace

import pytest

from app import config, rag, store, twelvelabs_client

from .conftest import FakeConnection

# --- config ---


def test_short_service_keys_are_refused(monkeypatch):
    monkeypatch.setenv("SERVICE_KEY", "too-short")
    with pytest.raises(RuntimeError, match="at least 32"):
        config.Settings()


def test_empty_service_key_is_refused(monkeypatch):
    monkeypatch.setenv("SERVICE_KEY", "")
    with pytest.raises(RuntimeError):
        config.Settings()


# --- chunking ---


def test_short_text_is_one_chunk():
    assert rag.chunk("  A beach.  ") == ["A beach."]
    assert rag.chunk("   ") == []


def test_long_text_splits_on_sentences_and_cuts_huge_ones(monkeypatch):
    monkeypatch.setattr(rag.settings, "max_chunk_chars", 20)
    chunks = rag.chunk("One two three. Four five six. " + "x" * 45)
    assert all(len(c) <= 20 for c in chunks)
    assert chunks[0] == "One two three."
    assert "".join(chunks).replace(" ", "").endswith("x" * 45)


# --- indexing and search ---


def test_index_document_stores_every_chunk(monkeypatch):
    stored = {}
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [1.0, 0.0])
    monkeypatch.setattr(store, "replace_document", lambda *args: stored.setdefault("args", args))
    assert rag.index_document(1, "VIDEO", 7, "A beach.") == (1, 2)
    assert stored["args"] == (1, "VIDEO", 7, [("A beach.", [1.0, 0.0])])


def test_index_document_keeps_old_data_when_embedding_fails(monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: None)
    monkeypatch.setattr(store, "replace_document", lambda *args: pytest.fail("should not store"))
    assert rag.index_document(1, "VIDEO", 7, "A beach.") == (0, 0)
    assert rag.index_document(1, "VIDEO", 7, "  ") == (0, 0)


def test_search_drops_results_far_behind_the_best(monkeypatch):
    # The real case: a food query matched the food clip at 0.77 and bridge clips at 0.43 and 0.34
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [1.0, 0.0])
    def vec(score):
        return [score, (1 - score**2) ** 0.5]  # cosine similarity with [1, 0] is exactly `score`
    monkeypatch.setattr(store, "load_for_owner", lambda owner, kind: [
        (1, "pancakes", vec(0.77)), (2, "bridge", vec(0.43)), (3, "castle", vec(0.34)), (4, "crepes", vec(0.60)),
    ])
    assert [ref for ref, _, _ in rag.search(1, "VIDEO", "street food", 10)] == [1, 4]


def test_search_ranks_by_best_chunk_and_drops_noise(monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [1.0, 0.0])
    monkeypatch.setattr(store, "load_for_owner", lambda owner, kind: [
        (1, "close", [0.9, 0.1]),
        (1, "closer", [1.0, 0.0]),     # same document, better chunk wins
        (2, "far", [0.0, 1.0]),        # below min_score
        (3, "zero", [0.0, 0.0]),       # no direction, skipped
        (4, "other model", [1.0, 0.0, 0.0]),  # different dimension, skipped
        (5, "ok", [0.7, 0.7]),
    ])
    hits = rag.search(1, "VIDEO", "beach", 10)
    assert [(ref, text) for ref, text, _ in hits] == [(1, "closer"), (5, "ok")]
    assert rag.search(1, "VIDEO", "beach", 1)[0][0] == 1


def test_search_handles_missing_pieces(monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: None)
    assert rag.search(1, "VIDEO", "q", 5) == []

    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [1.0])
    monkeypatch.setattr(store, "load_for_owner", lambda owner, kind: [])
    assert rag.search(1, "VIDEO", "q", 5) == []

    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [0.0])
    monkeypatch.setattr(store, "load_for_owner", lambda owner, kind: [(1, "t", [1.0])])
    assert rag.search(1, "VIDEO", "q", 5) == []


# --- store (SQL is recorded, not run) ---


def test_store_statements(fake_db):
    store.ensure_schema()
    store.replace_document(1, "VIDEO", 7, [("a", [1.0]), ("b", [2.0])])
    assert store.delete_document("VIDEO", 7) == 0
    sqls = [sql for sql, _ in fake_db.statements]
    assert "CREATE TABLE IF NOT EXISTS embeddings" in sqls[0]
    assert sqls[2].startswith("DELETE FROM embeddings")
    assert sum(1 for s in sqls if s.startswith("INSERT")) == 2
    # Values are always passed as parameters, never formatted into the SQL
    assert fake_db.statements[3][1] == (1, "VIDEO", 7, 0, "a", [1.0], 1)


def test_load_for_owner(monkeypatch):
    conn = FakeConnection(rows=[(7, "t", [1.0])])
    monkeypatch.setattr(store.psycopg, "connect", lambda url: conn)
    assert store.load_for_owner(1, "VIDEO") == [(7, "t", [1.0])]
    assert conn.statements[0][1] == (1, "VIDEO")


# --- TwelveLabs wrapper (SDK replaced by a fake) ---


class Boom:
    def __getattr__(self, name):
        raise RuntimeError("api down")


@pytest.fixture
def fake_sdk(monkeypatch):
    calls = {}

    def analyze(**kwargs):
        calls["analyze"] = kwargs
        return SimpleNamespace(data="  00:01-00:02  ")

    sdk = SimpleNamespace(
        tasks=SimpleNamespace(create=lambda **kw: SimpleNamespace(video_id=None, id="task-1")),
        indexes=SimpleNamespace(videos=SimpleNamespace(
            retrieve=lambda index, vid: SimpleNamespace(asset_id="asset-1"),
            delete=lambda index, vid: None,
        )),
        analyze=analyze,
        embed=SimpleNamespace(v_2=SimpleNamespace(
            create=lambda **kw: SimpleNamespace(data=[SimpleNamespace(embedding=(0.1, 0.2))]))),
    )
    monkeypatch.setattr(twelvelabs_client, "client", sdk)
    return calls


def test_sdk_calls(fake_sdk):
    assert twelvelabs_client.index_video("https://s3/x") == "task-1"
    assert twelvelabs_client.get_asset_id("v") == "asset-1"
    assert twelvelabs_client.delete_video("v") is True
    assert twelvelabs_client.summarize("asset-1") == "00:01-00:02"
    assert twelvelabs_client.find_intervals("asset-1", "eiffel tower") == "00:01-00:02"
    assert "eiffel tower" in fake_sdk["analyze"]["prompt"]
    assert twelvelabs_client.embed_text("hi") == [0.1, 0.2]


def test_sdk_failures_return_nothing(monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "client", Boom())
    assert twelvelabs_client.index_video("u") is None
    assert twelvelabs_client.get_asset_id("v") is None
    assert twelvelabs_client.delete_video("v") is False
    assert twelvelabs_client.summarize("a") is None
    assert twelvelabs_client.embed_text("t") is None


def test_empty_sdk_answers(monkeypatch):
    sdk = SimpleNamespace(
        analyze=lambda **kw: SimpleNamespace(data=None),
        embed=SimpleNamespace(v_2=SimpleNamespace(create=lambda **kw: SimpleNamespace(data=[]))),
    )
    monkeypatch.setattr(twelvelabs_client, "client", sdk)
    assert twelvelabs_client.summarize("a") is None
    assert twelvelabs_client.embed_text("t") is None


def test_search_with_nothing_relevant_returns_nothing(monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "embed_text", lambda text: [1.0, 0.0])
    monkeypatch.setattr(store, "load_for_owner", lambda owner, kind: [(1, "unrelated", [0.1, 1.0])])
    assert rag.search(1, "VIDEO", "q", 5) == []
