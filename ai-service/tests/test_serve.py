import socket

from app import serve


def test_socket_accepts_ipv4_and_ipv6():
    sock = serve.dual_stack_socket(0)  # any free port
    try:
        assert sock.family == socket.AF_INET6
        assert sock.getsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY) == 0
    finally:
        sock.close()


def test_main_serves_the_app_on_the_dual_stack_socket(monkeypatch):
    seen = {}

    class FakeServer:
        def __init__(self, config):
            seen["app"] = config.app

        def run(self, sockets):
            seen["port"] = sockets[0].getsockname()[1]
            sockets[0].close()

    monkeypatch.setenv("PORT", "0")
    monkeypatch.setattr(serve.uvicorn, "Server", FakeServer)
    serve.main()
    assert seen["app"] == "app.main:app"
    assert seen["port"] > 0
