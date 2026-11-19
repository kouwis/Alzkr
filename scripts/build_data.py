#!/usr/bin/env python3
"""Builds the app's bundled Quran data into app/src/main/assets/data/.

Sources (all public, fetched at build time — no server of our own):
- Madani 15-line page layout + QCF V1 (1405 print) glyph codes: github.com/zonetecde/mushaf-layout
- KFGQPC fonts (604 QCF V1 page fonts, QCF_BSML ornaments, Uthman Taha Naskh): github.com/nuqayah/qpc-fonts
- Surah/juz/hizb metadata, Quran text, التفسير الميسر, Sahih International: api.alquran.cloud
"""
import json
import os
import re
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".data-cache")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "data")
LAYOUT_URL = "https://raw.githubusercontent.com/zonetecde/mushaf-layout/main/mushaf/page-{:03d}.json"
ALQURAN = "https://api.alquran.cloud/v1/"
FONTS = "https://raw.githubusercontent.com/nuqayah/qpc-fonts/master/"
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")


def fetch_file(url, target):
    cache = os.path.join(CACHE, "files", os.path.basename(target))
    if not (os.path.exists(cache) and os.path.getsize(cache) > 0):
        last = None
        for attempt in range(6):
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "alzkr-build"})
                with urllib.request.urlopen(req, timeout=60) as r:
                    raw = r.read()
                os.makedirs(os.path.dirname(cache), exist_ok=True)
                with open(cache, "wb") as f:
                    f.write(raw)
                break
            except Exception as e:  # noqa: BLE001
                last = e
                time.sleep(2 * (attempt + 1))
        else:
            raise RuntimeError(f"failed {url}: {last}")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(cache, "rb") as src, open(target, "wb") as dst:
        dst.write(src.read())


def fetch_fonts():
    for p in range(1, 605):
        fetch_file(FONTS + f"mushaf/QCF_P{p:03d}.TTF", os.path.join(ASSETS, "qcf", f"p{p}.ttf"))
    fetch_file(FONTS + "mushaf/QCF_BSML.TTF", os.path.join(ASSETS, "fonts", "bsml.ttf"))
    fetch_file(FONTS + "various/UthmanTN1%20Ver10.otf", os.path.join(ASSETS, "fonts", "naskh.otf"))
    fetch_file(FONTS + "various/UthmanTN1B%20Ver10.otf", os.path.join(ASSETS, "fonts", "naskh_bold.otf"))


def fetch(url, cache_name):
    path = os.path.join(CACHE, cache_name)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        with open(path, "rb") as f:
            return json.loads(f.read().decode("utf-8"))
    last = None
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "alzkr-build"})
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read()
            data = json.loads(raw.decode("utf-8"))
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(raw)
            return data
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"failed {url}: {last}")


TASHKEEL = re.compile(r"[ؐ-ًؚ-ٰٟۖ-ۭـ]")


def normalize(s):
    s = TASHKEEL.sub("", s)
    s = re.sub("[إأآٱ]", "ا", s)
    s = s.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def edition(name):
    d = fetch(ALQURAN + "quran/" + name, f"alquran/{name}.json")
    out = []
    for s in d["data"]["surahs"]:
        out.append([a["text"] for a in sorted(s["ayahs"], key=lambda a: a["numberInSurah"])])
    assert len(out) == 114, name
    return out


def strip_basmala(texts, clean_texts):
    # alquran.cloud prefixes ayah 1 of every surah (except 1 and 9) with the basmala.
    for i in range(114):
        sn = i + 1
        if sn in (1, 9):
            continue
        for arr in (texts, clean_texts):
            first = arr[i][0]
            words = first.split(" ")
            if len(words) > 4 and normalize(words[0]).startswith("بسم"):
                arr[i][0] = " ".join(words[4:])


def main():
    os.makedirs(OUT, exist_ok=True)
    fetch_fonts()
    print("fonts ok", file=sys.stderr)

    # ---------- page layout ----------
    pages = []
    verse_page = {}
    verse_glyphs = {}
    for p in range(1, 605):
        d = fetch(LAYOUT_URL.format(p), f"layout/page-{p:03d}.json")
        lines = []
        for ln in d["lines"]:
            t = ln.get("type")
            if t == "surah-header":
                lines.append({"t": "h", "s": int(ln["surah"])})
            elif t == "basmala":
                lines.append({"t": "b"})
            else:
                ws = []
                for w in ln.get("words", []):
                    loc = w["location"]
                    g = w["qpcV1"]
                    ws.append([loc, g])
                    s, a, _ = loc.split(":")
                    key = f"{s}:{a}"
                    verse_page.setdefault(key, p)
                    chunks = verse_glyphs.setdefault(key, [])
                    if chunks and chunks[-1]["p"] == p:
                        chunks[-1]["g"] += " " + g
                    else:
                        chunks.append({"p": p, "g": g})
                lines.append({"t": "x", "w": ws})
        pages.append(lines)
    assert len(pages) == 604
    print("layout ok, verses:", len(verse_page), file=sys.stderr)

    # ---------- metadata ----------
    meta = fetch(ALQURAN + "meta", "alquran/meta.json")["data"]
    surahs = []
    for r in meta["surahs"]["references"]:
        n = r["number"]
        name = TASHKEEL.sub("", r["name"]).replace("ٱ", "ا").strip()
        name = re.sub(r"^سورة\s+", "", name)
        surahs.append({
            "n": n,
            "name": name,
            "ayahs": r["numberOfAyahs"],
            "type": "مكية" if r["revelationType"] == "Meccan" else "مدنية",
            "page": verse_page[f"{n}:1"],
        })

    simple = edition("quran-simple")
    clean = edition("quran-simple-clean")
    strip_basmala(simple, clean)
    tafsir = edition("ar.muyassar")
    trans = edition("en.sahih")

    def words_start(s, a, k=4):
        return " ".join(clean[s - 1][a - 1].split(" ")[:k])

    juz = []
    for i, r in enumerate(meta["juzs"]["references"]):
        juz.append({"n": i + 1, "s": r["surah"], "a": r["ayah"], "page": verse_page[f"{r['surah']}:{r['ayah']}"]})
    rub = []
    for i, r in enumerate(meta["hizbQuarters"]["references"]):
        rub.append({
            "n": i + 1, "s": r["surah"], "a": r["ayah"],
            "page": verse_page[f"{r['surah']}:{r['ayah']}"],
            "text": words_start(r["surah"], r["ayah"]),
        })
    hizb = [dict(r, n=i + 1) for i, r in enumerate(rub[::4])]
    assert len(juz) == 30 and len(rub) == 240 and len(hizb) == 60

    vpage = [[verse_page[f"{s + 1}:{a + 1}"] for a in range(len(simple[s]))] for s in range(114)]
    clean_norm = [[normalize(t) for t in sura] for sura in clean]

    def dump(name, obj):
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))

    dump("pages.json", pages)
    dump("meta.json", {"surahs": surahs, "juz": juz, "hizb": hizb, "rub": rub})
    dump("verses.json", {"page": vpage, "simple": simple, "clean": clean_norm})
    dump("glyphs.json", verse_glyphs)
    dump("tafsir_muyassar.json", tafsir)
    dump("translation_en.json", trans)
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)), file=sys.stderr)


if __name__ == "__main__":
    main()
