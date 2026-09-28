#!/usr/bin/env python3
"""Expose Locust p95 latency as a Prometheus gauge for metrics-adapter."""

from __future__ import annotations

import json
import os
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

NS = os.environ.get("LOCUST_METRIC_NAMESPACE", "sock-shop")
NAME = os.environ.get("LOCUST_METRIC_NAME", "front-end")
LOCUST_STATS = os.environ.get("LOCUST_STATS_URL", "http://127.0.0.1:8089/stats/requests")


def p95_seconds() -> float:
    try:
        with urllib.request.urlopen(LOCUST_STATS, timeout=2) as response:
            data = json.loads(response.read().decode())
        raw = data.get("current_response_time_percentile_95")
        if raw is None:
            for row in data.get("stats") or []:
                if row.get("name") == "Aggregated":
                    raw = row.get("avg_response_time") or 0
                    break
        return float(raw or 0) / 1000.0
    except Exception:
        return 0.0


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path.split("?")[0] != "/metrics":
            self.send_response(404)
            self.end_headers()
            return
        body = (
            "# HELP locust_p95_response_time_seconds Locust current p95 response time.\n"
            "# TYPE locust_p95_response_time_seconds gauge\n"
            f'locust_p95_response_time_seconds{{namespace="{NS}",name="{NAME}"}} {p95_seconds()}\n'
        ).encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        return


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 9646), Handler).serve_forever()
