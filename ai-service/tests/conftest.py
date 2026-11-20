"""Shared fixtures. Nothing here talks to TwelveLabs or a real database."""

import os

# Settings are read at import time, so fake values go in before the app is imported
os.environ.setdefault("TWELVELABS_API_KEY", "test-key")
os.environ.setdefault("TWELVELABS_INDEX_ID", "test-index")
os.environ.setdefault("DATABASE_URL", "postgresql://test@localhost:1/test")
os.environ.setdefault("SERVICE_KEY", "k" * 40)

import pytest  # noqa: E402

from app import store  # noqa: E402

SERVICE_KEY = os.environ["SERVICE_KEY"]


class FakeCursor:
    def __init__(self, rows=None, rowcount=0):
        self._rows = rows or []
        self.rowcount = rowcount

    def fetchall(self):
        return self._rows


class FakeConnection:
    """Records every statement instead of running it."""

    def __init__(self, rows=None, rowcount=0):
        self.statements = []
        self.rows = rows
        self.rowcount = rowcount

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql, params=None):
        self.statements.append((sql, params))
        return FakeCursor(self.rows, self.rowcount)

    def transaction(self):
        return self


@pytest.fixture
def fake_db(monkeypatch):
    conn = FakeConnection()
    monkeypatch.setattr(store.psycopg, "connect", lambda url: conn)
    return conn
