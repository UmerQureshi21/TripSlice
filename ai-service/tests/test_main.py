"""HTTP layer: auth, validation and routing. The modules behind it are stubbed."""

import pytest
from fastapi.testclient import TestClient

from app import main, rag, store, twelvelabs_client

from .conftest import SERVICE_KEY

AUTH = {"X-Service-Key": SERVICE_KEY}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(store, "ensure_schema", lambda: None)
    with TestClient(main.app) as c:
        yield c


def test_health_is_open(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_docs_are_not_exposed(client):
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


@pytest.mark.parametrize("headers", [{}, {"X-Service-Key": "wrong"}, {"X-Service-Key": SERVICE_KEY + "x"}])
def test_every_route_needs_the_service_key(client, headers):
    assert client.post("/rag/search", json={"owner_id": 1, "kind": "VIDEO", "query": "q"}, headers=headers).status_code == 401
    assert client.get("/videos/v1/asset", headers=headers).status_code == 401


def test_video_endpoints(client, monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "index_video", lambda url: "vid-1")
    monkeypatch.setattr(twelvelabs_client, "get_asset_id", lambda vid: "asset-1")
    monkeypatch.setattr(twelvelabs_client, "delete_video", lambda vid: True)
    monkeypatch.setattr(twelvelabs_client, "summarize", lambda asset: "a summary")
    monkeypatch.setattr(twelvelabs_client, "find_intervals", lambda asset, topic: f"00:01-00:02 {topic}")

    assert client.post("/videos/index", json={"video_url": "https://s3/x"}, headers=AUTH).json() == {"video_id": "vid-1"}
    assert client.get("/videos/vid-1/asset", headers=AUTH).json() == {"asset_id": "asset-1"}
    assert client.delete("/videos/vid-1", headers=AUTH).json() == {"deleted": True}
    assert client.post("/videos/asset-1/summary", headers=AUTH).json() == {"summary": "a summary"}
    assert client.post("/videos/asset-1/intervals", json={"topic": "beach"}, headers=AUTH).json() == {
        "data": "00:01-00:02 beach"
    }


def test_index_failure_is_a_502(client, monkeypatch):
    monkeypatch.setattr(twelvelabs_client, "index_video", lambda url: None)
    assert client.post("/videos/index", json={"video_url": "https://s3/x"}, headers=AUTH).status_code == 502


def test_inputs_are_size_limited(client):
    assert client.post("/videos/a/intervals", json={"topic": "x" * 101}, headers=AUTH).status_code == 422
    assert client.post("/videos/a/intervals", json={"topic": ""}, headers=AUTH).status_code == 422
    body = {"owner_id": 1, "kind": "VIDEO", "query": "x" * 201}
    assert client.post("/rag/search", json=body, headers=AUTH).status_code == 422
    body = {"owner_id": 1, "kind": "OTHER", "query": "q"}
    assert client.post("/rag/search", json=body, headers=AUTH).status_code == 422


def test_rag_endpoints(client, monkeypatch):
    monkeypatch.setattr(rag, "index_document", lambda owner, kind, ref, text: (2, 512))
    monkeypatch.setattr(store, "delete_document", lambda kind, ref: 3)
    monkeypatch.setattr(rag, "search", lambda owner, kind, query, limit: [(7, "text", 0.8)])

    doc = {"owner_id": 1, "kind": "VIDEO", "ref_id": 7, "text": "hello"}
    assert client.post("/rag/documents", json=doc, headers=AUTH).json() == {"embedded": True, "chunks": 2, "dim": 512}
    assert client.delete("/rag/documents/VIDEO/7", headers=AUTH).json() == {"deleted": 3}
    found = client.post("/rag/search", json={"owner_id": 1, "kind": "VIDEO", "query": " beach "}, headers=AUTH)
    assert found.json() == {"hits": [{"ref_id": 7, "text": "text", "score": 0.8}]}
    blank = client.post("/rag/search", json={"owner_id": 1, "kind": "VIDEO", "query": "   "}, headers=AUTH)
    assert blank.json() == {"hits": []}


def test_startup_creates_the_schema(monkeypatch):
    calls = []
    monkeypatch.setattr(store, "ensure_schema", lambda: calls.append(1))
    with TestClient(main.app):
        pass
    assert calls == [1]
