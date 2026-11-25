#!/usr/bin/env python3
"""最小可用的 Mock Subsonic 服务器，用于端到端联调（登录/曲库/播放/分析/下载）。

只实现 Lumen 客户端实际会调用的 rest 方法；认证按 Subsonic token 规则校验：
    t == md5(password + s)，且 u == USER

用法：
    python tools/mock-subsonic/server.py --port 8899 --media tools/mock-subsonic/media

注意：这是**测试工具**，不要用于生产，也不要在公网暴露。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

USER = "test"
PASSWORD = "testpass"
ALBUMS = [
    {
        "id": "al-1",
        "name": "Mock Album One",
        "artist": "Mock Artist",
        "cover": "cover-1",
        "year": 2024,
        "tracks": [
            ("t-101", "First Light", "mock-1.wav", 30),
            ("t-102", "Slow Drift", "mock-2.wav", 30),
            ("t-103", "Night Walk", "mock-3.wav", 30),
        ],
    },
    {
        "id": "al-2",
        "name": "Mock Album Two",
        "artist": "Mock Artist",
        "cover": "cover-2",
        "year": 2021,
        "tracks": [
            ("t-201", "Bright Chord", "mock-4.wav", 30),
            ("t-202", "Pulse", "mock-5.wav", 30),
            ("t-203", "Quiet End", "mock-6.wav", 30),
        ],
    },
]
TRACKS = {t[0]: (album, t) for album in ALBUMS for t in album["tracks"]}
MEDIA_DIR = "tools/mock-subsonic/media"


def md5(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def song_json(album, track):
    tid, title, filename, dur = track
    return {
        "id": tid,
        "title": title,
        "artist": album["artist"],
        "album": album["name"],
        "albumId": album["id"],
        "coverArt": album["cover"],
        "duration": dur,
        "suffix": "wav",
        "contentType": "audio/wav",
        "discNumber": 1,
        "track": album["tracks"].index(track) + 1,
    }


def album_json(album, with_songs: bool):
    data = {
        "id": album["id"],
        "name": album["name"],
        "artist": album["artist"],
        "coverArt": album["cover"],
        "songCount": len(album["tracks"]),
        "year": album["year"],
    }
    if with_songs:
        data["song"] = [song_json(album, t) for t in album["tracks"]]
    return data


class Handler(BaseHTTPRequestHandler):
    server_version = "MockSubsonic/1.0"

    def log_message(self, fmt, *args):  # 降噪
        pass

    # ---------------------------------------------------------------- helpers
    def _send_json(self, payload: dict, status: int = 200):
        body = json.dumps({"subsonic-response": {"status": "ok", "version": "1.16.1", **payload}}).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_error_json(self, code: int, message: str):
        payload = {"subsonic-response": {"status": "failed", "version": "1.16.1",
                                         "error": {"code": code, "message": message}}}
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _auth_ok(self, q: dict) -> bool:
        u = (q.get("u") or [""])[0]
        t = (q.get("t") or [""])[0]
        s = (q.get("s") or [""])[0]
        return u == USER and bool(s) and t.lower() == md5(PASSWORD + s).lower()

    def _file(self, path: str, content_type: str | None = None):
        if not os.path.exists(path):
            self.send_error(404)
            return
        size = os.path.getsize(path)
        ctype = content_type or mimetypes.guess_type(path)[0] or "application/octet-stream"
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(size))
        self.send_header("Accept-Ranges", "none")
        self.end_headers()
        with open(path, "rb") as f:
            while True:
                chunk = f.read(256 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)

    # ---------------------------------------------------------------- routing
    def do_HEAD(self):
        parsed = urlparse(self.path)
        if parsed.path.startswith("/rest/stream.view"):
            q = parse_qs(parsed.query)
            tid = (q.get("id") or [""])[0]
            if tid in TRACKS and self._auth_ok(q):
                path = os.path.join(MEDIA_DIR, TRACKS[tid][1][2])
                if os.path.exists(path):
                    self.send_response(200)
                    self.send_header("Content-Type", "audio/wav")
                    self.send_header("Content-Length", str(os.path.getsize(path)))
                    self.send_header("Accept-Ranges", "none")
                    self.end_headers()
                    return
        self.send_response(404)
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        q = parse_qs(parsed.query)
        path = parsed.path

        if path.startswith("/rest/stream.view"):
            tid = (q.get("id") or [""])[0]
            if not self._auth_ok(q):
                self._send_error_json(40, "Wrong username or password")
                return
            if tid not in TRACKS:
                self.send_error(404)
                return
            self._file(os.path.join(MEDIA_DIR, TRACKS[tid][1][2]), "audio/wav")
            return

        if path.startswith("/rest/getCoverArt.view"):
            self._file(os.path.join(MEDIA_DIR, "cover.png"), "image/png")
            return

        if not path.startswith("/rest/"):
            self.send_error(404)
            return
        if not self._auth_ok(q):
            self._send_error_json(40, "Wrong username or password")
            return

        method = path[len("/rest/"):].removesuffix(".view")
        if method == "ping":
            self._send_json({})
        elif method == "getAlbumList2":
            self._send_json({"albumList2": {"album": [album_json(a, False) for a in ALBUMS]}})
        elif method == "getAlbum":
            aid = (q.get("id") or [""])[0]
            album = next((a for a in ALBUMS if a["id"] == aid), None)
            self._send_json({"album": album_json(album, True) if album else {}})
        elif method == "getSong":
            tid = (q.get("id") or [""])[0]
            item = TRACKS.get(tid)
            self._send_json({"song": song_json(*item) if item else {}})
        elif method == "getArtists":
            self._send_json({"artists": {"index": [{"name": "M", "artist": [
                {"id": "ar-1", "name": "Mock Artist", "albumCount": len(ALBUMS), "coverArt": "cover-1"}
            ]}]}})
        elif method == "getArtist":
            self._send_json({"artist": {
                "id": "ar-1", "name": "Mock Artist", "albumCount": len(ALBUMS), "coverArt": "cover-1",
                "album": [album_json(a, False) for a in ALBUMS],
            }})
        elif method == "search3":
            query = ((q.get("query") or [""])[0]).lower()
            albums = [a for a in ALBUMS if query in a["name"].lower()]
            songs = [song_json(*TRACKS[t]) for t in TRACKS
                     if query in TRACKS[t][1][1].lower()]
            self._send_json({"searchResult3": {"album": [album_json(a, False) for a in albums], "song": songs}})
        elif method == "getStarred2":
            self._send_json({"starred2": {"song": [song_json(*TRACKS["t-101"])]}})
        elif method in ("scrobble", "star", "unstar"):
            self._send_json({})
        elif method == "getLyricsBySongId":
            self._send_json({"lyricsList": {"structuredLyrics": [{
                "line": [{"start": 0, "value": "mock line one"}, {"start": 5000, "value": "mock line two"}]
            }]}})
        elif method == "getLyrics":
            self._send_json({"lyrics": {"value": "mock line one\nmock line two"}})
        else:
            self._send_error_json(0, "Unsupported method: " + method)


def main() -> int:
    global MEDIA_DIR
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8899)
    parser.add_argument("--media", default=MEDIA_DIR)
    args = parser.parse_args()
    MEDIA_DIR = args.media
    httpd = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"mock subsonic on http://0.0.0.0:{args.port}  user={USER} pass={PASSWORD}", flush=True)
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
