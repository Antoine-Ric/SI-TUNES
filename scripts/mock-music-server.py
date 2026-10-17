#!/usr/bin/env python3
"""HTTP fixtures for tests and explicit local demo mode; never a production fallback."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

TRACKS = [
    ("Money Trees", "Kendrick Lamar", "hip hop"),
    ("Love Yourz", "J. Cole", "hip hop"),
    ("Good Life", "Kanye West", "hip hop"),
    ("L$D", "A$AP Rocky", "hip hop"),
    ("Pursuit of Happiness", "Kid Cudi", "hip hop"),
    ("God's Plan", "Drake", "hip hop"),
    ("Jolene", "Dolly Parton", "country"),
]
COUNTS = {"catalog": 0, "enrichment": 0}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # Do not log query strings: Last.fm requests carry a test API key.

    def send_json(self, body, status=200):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        request = urlsplit(self.path)
        params = parse_qs(request.query)
        if request.path == "/__test/stats":
            self.send_json(COUNTS)
        elif request.path == "/ws/2/recording":
            COUNTS["catalog"] += 1
            if "electronic" in params.get("query", [""])[0]:
                self.send_json({"error": "fixture upstream unavailable"}, 503)
                return
            self.send_json({"recordings": [
                {"id": f"fixture-recording-{i}", "title": title, "score": 100 - i,
                 "artist-credit": [{"name": artist, "artist": {"name": artist}}],
                 "tags": [{"name": genre, "count": 10}]}
                for i, (title, artist, genre) in enumerate(TRACKS)
            ]})
        elif request.path == "/2.0/":
            COUNTS["enrichment"] += 1
            if params.get("method") != ["track.getTopTags"] or params.get("api_key") != ["fixture-key-not-real"]:
                self.send_json({"error": 10, "message": "Invalid test request"})
                return
            track = next((row for row in TRACKS if row[0] == params.get("track", [""])[0]), None)
            tags = [{"name": track[2], "count": 100}, {"name": "confident", "count": 20}] if track else []
            self.send_json({"toptags": {"tag": tags}})
        else:
            self.send_json({"error": "unknown fixture path"}, 404)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("FIXTURE_PORT", "9011"))), Handler).serve_forever()
