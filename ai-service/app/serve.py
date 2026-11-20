"""Starts the service on one socket that accepts both IPv4 and IPv6.

`uvicorn --host ::` isn't enough: asyncio marks IPv6 sockets IPv6-only, so IPv4 clients (such as
Railway's health check) can't connect, while Railway's private network uses IPv6. Binding the
socket ourselves with IPV6_V6ONLY off serves both.
"""

import os
import socket

import uvicorn


def dual_stack_socket(port: int) -> socket.socket:
    sock = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
    sock.bind(("::", port))
    return sock


def main() -> None:
    port = int(os.environ.get("PORT", "8000"))
    server = uvicorn.Server(uvicorn.Config("app.main:app", log_level="info"))
    server.run(sockets=[dual_stack_socket(port)])


if __name__ == "__main__":  # pragma: no cover
    main()
