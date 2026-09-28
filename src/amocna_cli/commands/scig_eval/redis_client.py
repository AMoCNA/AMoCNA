"""Minimal RESP Redis client over TCP (no redis-cli dependency)."""

from __future__ import annotations

import socket
from typing import Any


class RedisRespClient:
    def __init__(self, host: str = "localhost", port: int = 6379, timeout_s: float = 30.0):
        self.host = host
        self.port = port
        self.timeout_s = timeout_s

    def _connect(self) -> socket.socket:
        s = socket.create_connection((self.host, self.port), timeout=self.timeout_s)
        s.settimeout(self.timeout_s)
        return s

    def _send(self, sock: socket.socket, *args: str) -> None:
        parts = [f"*{len(args)}\r\n".encode()]
        for arg in args:
            data = arg.encode("utf-8")
            parts.append(f"${len(data)}\r\n".encode())
            parts.append(data)
            parts.append(b"\r\n")
        sock.sendall(b"".join(parts))

    def _readline(self, sock: socket.socket) -> bytes:
        buf = bytearray()
        while True:
            ch = sock.recv(1)
            if not ch:
                break
            buf.extend(ch)
            if buf.endswith(b"\r\n"):
                return bytes(buf[:-2])
        return bytes(buf)

    def _read_bulk(self, sock: socket.socket, length: int) -> bytes | None:
        if length < 0:
            return None
        data = bytearray()
        while len(data) < length:
            chunk = sock.recv(length - len(data))
            if not chunk:
                break
            data.extend(chunk)
        sock.recv(2)  # CRLF
        return bytes(data)

    def _parse(self, sock: socket.socket) -> Any:
        header = self._readline(sock)
        if not header:
            return None
        prefix = chr(header[0])
        payload = header[1:].decode("utf-8", errors="replace")
        if prefix == "+":
            return payload
        if prefix == "-":
            raise RuntimeError(payload)
        if prefix == ":":
            return int(payload)
        if prefix == "$":
            return self._read_bulk(sock, int(payload))
        if prefix == "*":
            n = int(payload)
            if n < 0:
                return None
            return [self._parse(sock) for _ in range(n)]
        raise RuntimeError(f"Unknown RESP prefix: {prefix}")

    def execute(self, *args: str) -> Any:
        with self._connect() as sock:
            self._send(sock, *args)
            return self._parse(sock)

    def ping(self) -> bool:
        try:
            return self.execute("PING") == "PONG" or self.execute("PING") == b"PONG"
        except Exception:
            return False

    def keys(self, pattern: str) -> list[str]:
        result = self.execute("KEYS", pattern)
        if not result:
            return []
        out = []
        for item in result:
            if isinstance(item, bytes):
                out.append(item.decode("utf-8", errors="replace"))
            else:
                out.append(str(item))
        return out

    def get(self, key: str) -> str | None:
        result = self.execute("GET", key)
        if result is None:
            return None
        if isinstance(result, bytes):
            return result.decode("utf-8", errors="replace")
        return str(result)

    def info_memory_used(self) -> float:
        raw = self.execute("INFO", "memory")
        text = raw.decode("utf-8") if isinstance(raw, bytes) else str(raw)
        for line in text.splitlines():
            if line.startswith("used_memory:"):
                return int(line.split(":")[1]) / (1024 * 1024)
        return 0.0
