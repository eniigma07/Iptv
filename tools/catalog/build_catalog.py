"""
Buduje katalog short dram z OFICJALNYCH kanałów YouTube studiów.
Pobiera tylko metadane (tytuły, czas trwania, miniatury). Filmów nie pobiera:
aplikacja odtwarza je oficjalnym odtwarzaczem YouTube.

Wynik: out/catalog.json, out/report.txt
"""
import json
import os
import re
import sys
import time

import yt_dlp

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("OUT", "out")
MAX_VIDEOS = int(os.environ.get("MAX_VIDEOS", "1500"))
MAX_PLAYLISTS = int(os.environ.get("MAX_PLAYLISTS", "120"))
os.makedirs(OUT, exist_ok=True)
report = []


def log(*a):
    s = " ".join(str(x) for x in a)
    print(s, flush=True)
    report.append(s)


def ydl(extra=None):
    o = {"quiet": True, "no_warnings": True, "extract_flat": "in_playlist", "skip_download": True,
         "ignoreerrors": True, "socket_timeout": 30}
    if extra:
        o.update(extra)
    return yt_dlp.YoutubeDL(o)


def listing(url, limit):
    with ydl({"playlistend": limit}) as y:
        info = y.extract_info(url, download=False)
    return info or {}


def thumb(e):
    t = e.get("thumbnails") or []
    if t:
        best = max(t, key=lambda x: (x.get("width") or 0))
        return best.get("url")
    vid = e.get("id")
    return f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg" if vid else None


FULL_WORDS = re.compile(r"\b(full|complete|all episodes|full movie|full episodes?|full series|ep\.?\s*1\s*[-~–]|episodes?\s*1\s*[-~–]|1\s*[-–]\s*\d{2,3})\b", re.I)
TRAILER_WORDS = re.compile(r"\b(trailer|teaser|preview|clip|recap|highlights?|#shorts?)\b", re.I)
PART = re.compile(r"\b(?:part|pt\.?)\s*(\d+)\b|\((\d+)\s*/\s*\d+\)|\b(\d+)\s*/\s*\d+\b", re.I)
JUNK = re.compile(
    r"(\[[^\]]*\]|\([^)]*(full|ep|episode|part|movie|dub|eng|sub)[^)]*\)|【[^】]*】|#\S+|\|.*$|"
    r"\b(full (movie|episodes?|series)|full|complete|all episodes|english dubbed|eng ?sub|dubbed|new drama|"
    r"short drama|drama ?box|reel ?short|net ?short|good ?short|short ?max|ep\.?\s*\d+\s*[-~–]\s*\d+|"
    r"episodes?\s*\d+\s*[-~–]\s*\d+|part\s*\d+|pt\.?\s*\d+)\b)", re.I)


def clean_title(t):
    s = JUNK.sub(" ", t or "")
    s = re.sub(r"[\s\-–—:|,!.]+$", "", re.sub(r"\s+", " ", s)).strip(" -–—:|")
    return s or (t or "").strip()


def key_of(t):
    return re.sub(r"[^a-z0-9]+", "", clean_title(t).lower())


def part_no(t):
    m = PART.search(t or "")
    if not m:
        return None
    return int(next(g for g in m.groups() if g))


def channel(ch):
    base = f"https://www.youtube.com/{ch['handle']}"
    info = listing(base + "/videos", MAX_VIDEOS)
    if not info or not info.get("entries"):
        log(f"[pomijam] {ch['handle']}: kanał niedostępny")
        return None
    meta = {
        "handle": ch["handle"], "studio": ch["studio"],
        "title": info.get("channel") or info.get("uploader") or info.get("title"),
        "channel_id": info.get("channel_id"),
        "followers": info.get("channel_follower_count"),
        "description": (info.get("description") or "")[:300].replace("\n", " "),
    }
    videos = []
    for e in info.get("entries") or []:
        if not e or not e.get("id"):
            continue
        videos.append({"id": e["id"], "title": e.get("title") or "", "duration": int(e.get("duration") or 0),
                       "views": e.get("view_count") or 0, "thumb": thumb(e)})
    playlists = []
    pinfo = listing(base + "/playlists", MAX_PLAYLISTS)
    for p in (pinfo.get("entries") or [])[:MAX_PLAYLISTS]:
        if not p or not p.get("id"):
            continue
        playlists.append({"id": p["id"], "title": p.get("title") or ""})
    log(f"[kanał] {ch['handle']} | {meta['title']} | obserwujący {meta['followers']} | filmy {len(videos)} | playlisty {len(playlists)}")
    log(f"        opis: {meta['description'][:160]}")
    return meta, videos, playlists


def playlist_entries(pid):
    info = listing(f"https://www.youtube.com/playlist?list={pid}", 400)
    out = []
    for e in info.get("entries") or []:
        if e and e.get("id") and (e.get("title") or "") not in ("[Private video]", "[Deleted video]"):
            out.append({"id": e["id"], "title": e.get("title") or "", "duration": int(e.get("duration") or 0),
                        "views": e.get("view_count") or 0, "thumb": thumb(e)})
    return out


def build():
    src = json.load(open(os.path.join(HERE, "sources.json")))
    series = {}
    studios = []

    def add(studio, key, title, video, kind, order, playlist=None):
        s = series.setdefault(key, {"id": key[:60], "title": title, "studio": studio, "kind": kind, "playlist": playlist,
                                    "videos": [], "views": 0, "thumb": video.get("thumb")})
        if any(v["id"] == video["id"] for v in s["videos"]):
            return
        s["videos"].append({"id": video["id"], "title": video["title"], "duration": video["duration"], "order": order})
        s["views"] += video["views"] or 0
        if kind == "full" and s["kind"] != "full":
            s["kind"] = "full"

    for ch in src["channels"]:
        try:
            r = channel(ch)
        except Exception as e:  # noqa
            log(f"[błąd] {ch['handle']}: {e}")
            continue
        if not r:
            continue
        meta, videos, playlists = r
        studios.append(meta)
        studio = ch["studio"]
        in_playlists = set()
        # playlisty z odcinkami = seria
        for p in playlists:
            if TRAILER_WORDS.search(p["title"]) or re.search(r"\bshorts?\b", p["title"], re.I):
                continue
            eps = playlist_entries(p["id"])
            eps = [e for e in eps if e["duration"] == 0 or e["duration"] >= 40]
            if len(eps) < 3:
                continue
            total = sum(e["duration"] for e in eps)
            key = f"{studio}:{key_of(p['title'])}"
            kind = "episodes" if total >= 20 * 60 else "partial"
            for i, e in enumerate(eps):
                add(studio, key, clean_title(p["title"]), e, kind, i + 1, playlist=p["id"])
                in_playlists.add(e["id"])
            time.sleep(0.5)
        # pojedyncze długie filmy = pełne serie / części
        for v in videos:
            if v["id"] in in_playlists:
                continue
            d = v["duration"]
            t = v["title"]
            if d and d < 6 * 60:
                continue  # zwiastuny, klipy
            if TRAILER_WORDS.search(t) and d < 20 * 60:
                continue
            full = d >= 40 * 60 or (d >= 20 * 60 and FULL_WORDS.search(t))
            kind = "full" if full else "partial"
            key = f"{studio}:{key_of(t)}"
            add(studio, key, clean_title(t), v, kind, part_no(t) or 1)

    out = []
    for s in series.values():
        s["videos"].sort(key=lambda v: (v["order"], v["title"]))
        s["duration"] = sum(v["duration"] for v in s["videos"])
        s["count"] = len(s["videos"])
        if s["duration"] and s["duration"] < 15 * 60 and s["kind"] != "episodes":
            continue
        if s["kind"] == "partial" and s["duration"] >= 60 * 60:
            s["kind"] = "full"
        out.append(s)
    rank = {"full": 0, "episodes": 1, "partial": 2}
    out.sort(key=lambda s: (rank[s["kind"]], -s["views"]))
    cat = {"version": 1, "generated": int(time.time()), "studios": studios, "series": out}
    json.dump(cat, open(os.path.join(OUT, "catalog.json"), "w"), ensure_ascii=False, separators=(",", ":"))
    by = {}
    for s in out:
        by.setdefault((s["studio"], s["kind"]), 0)
        by[(s["studio"], s["kind"])] += 1
    log("\n== PODSUMOWANIE ==")
    for k in sorted(by):
        log(f"{k[0]:12s} {k[1]:9s} {by[k]}")
    log(f"razem serii: {len(out)}, pełnych: {sum(1 for s in out if s['kind']=='full')}")
    log("\nPrzykłady:")
    for s in out[:40]:
        log(f"  [{s['kind']}] {s['studio']}: {s['title']} | {s['count']} wideo | {s['duration']//60} min | {s['views']} wyśw.")
    open(os.path.join(OUT, "report.txt"), "w").write("\n".join(report))
    if not out:
        sys.exit("Pusty katalog")


if __name__ == "__main__":
    build()
