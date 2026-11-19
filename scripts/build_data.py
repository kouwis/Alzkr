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
    fetch_file(FONTS + "various/UthmanicHafs1%20Ver09.otf", os.path.join(ASSETS, "fonts", "hafs.otf"))


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


import unicodedata

TASHKEEL = re.compile(r"[ؐ-ًؚ-ٰٟۖ-ۭـ]")


def normalize(s):
    s = TASHKEEL.sub("", s)
    s = re.sub("[إأآٱ]", "ا", s)
    s = s.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def page_weights(target=0.19):
    """Per-page stroke (as a fraction of the font size) that brings every QCF page
    font to the same ink density as the bolder pages, so all pages look equally bold."""
    from fontTools.ttLib import TTFont
    from fontTools.pens.areaPen import AreaPen
    from fontTools.pens.perimeterPen import PerimeterPen
    out = []
    for p in range(1, 605):
        f = TTFont(os.path.join(ASSETS, "qcf", f"p{p}.ttf"))
        gs = f.getGlyphSet()
        upm = f["head"].unitsPerEm
        area = per = adv = 0.0
        for cp, g in f.getBestCmap().items():
            if cp < 0xFB00:
                continue
            ap = AreaPen(gs); gs[g].draw(ap)
            pp = PerimeterPen(gs); gs[g].draw(pp)
            area += abs(ap.value); per += pp.value; adv += gs[g].width
        if adv <= 0 or per <= 0:
            out.append(0.0)
            continue
        ratio = area / (adv * upm)
        stroke_units = max(0.0, 2 * (target - ratio) * upm * adv / per)
        out.append(round(stroke_units / upm, 5))
    return out


def line_em(pages):
    """Typical width (in em) of a full mushaf line, measured over all page fonts.
    The app draws every page at the size where this width fills the screen, so all
    pages share one text size (a few slightly longer lines are condensed a hair)."""
    from fontTools.ttLib import TTFont
    full = []
    for p, lines in enumerate(pages, start=1):
        if p <= 2:
            continue
        f = TTFont(os.path.join(ASSETS, "qcf", f"p{p}.ttf"))
        cmap = f.getBestCmap(); hm = f["hmtx"]; upm = f["head"].unitsPerEm
        widths = []
        for ln in lines:
            if ln["t"] != "x":
                continue
            tot = sum(hm[cmap[ord(ch)]][0] for _, g in ln["w"] for ch in g if ch != " " and ord(ch) in cmap)
            widths.append(tot / upm)
        if widths:
            top = max(widths)
            full += [x for x in widths if x >= 0.9 * top]
    full.sort()
    return round(full[int(len(full) * 0.85)], 4)


TAJWEED_FONT = "https://raw.githubusercontent.com/mahmoud123-karash/quran_fonts/main/assets/fonts/qcf_tajweed/QCF4{:03d}_COLOR-Regular.ttf.gz"


def tajweed_pages(pages_raw):
    """Tajweed mushaf (KFGQPC QCF V4, colours inside the font, 1421 print) layout.

    The V4 fonts follow the 1421 print, whose line breaks differ slightly from the 1405
    print our layout file uses. Same pages, same surah headers, same number of lines, so
    for every run of text lines between headers we re-split its words into that many lines
    so the line widths are as even as possible: in the print every line is full, and this
    recovers its breaks (lines come out within ~1% of each other)."""
    import gzip
    from fontTools.ttLib import TTFont
    out, full = [], []
    for p, d in enumerate(pages_raw, start=1):
        path = os.path.join(CACHE, "tajweed", f"p{p}.ttf")
        if not (os.path.exists(path) and os.path.getsize(path) > 0):
            fetch_file(TAJWEED_FONT.format(p), path + ".gz")
            with gzip.open(path + ".gz", "rb") as src, open(path, "wb") as dst:
                dst.write(src.read())
        f = TTFont(path)
        cm = f.getBestCmap(); hm = f["hmtx"]; upm = f["head"].unitsPerEm

        def width(g):
            return sum(hm[cm[ord(ch)]][0] for ch in g if ch != " " and ord(ch) in cm) / upm

        lines = []
        segment = []  # pending text lines

        def flush():
            if not segment:
                return
            words = [[w["location"], w["qpcV2"]] for ln in segment for w in ln["words"]]
            k = len(segment)
            if p <= 2 or k == 1:
                for ln in segment:
                    lines.append({"t": "x", "w": [[w["location"], w["qpcV2"]] for w in ln["words"]]})
            else:
                ws = [width(g) for _, g in words]
                n = len(ws)
                pre = [0.0]
                for x in ws:
                    pre.append(pre[-1] + x)
                target = pre[-1] / k
                inf = float("inf")
                dp = [[inf] * (k + 1) for _ in range(n + 1)]
                back = [[0] * (k + 1) for _ in range(n + 1)]
                dp[0][0] = 0.0
                for j in range(1, k + 1):
                    for i in range(j, n + 1):
                        best, arg = inf, 0
                        for st in range(j - 1, i):
                            c = dp[st][j - 1] + (pre[i] - pre[st] - target) ** 2
                            if c < best:
                                best, arg = c, st
                        dp[i][j], back[i][j] = best, arg
                cuts, i = [], n
                for j in range(k, 0, -1):
                    st = back[i][j]
                    cuts.append((st, i))
                    i = st
                for a, b in reversed(cuts):
                    lines.append({"t": "x", "w": words[a:b]})
                    full.append(pre[b] - pre[a])
            segment.clear()

        for ln in d["lines"]:
            t = ln.get("type")
            if t in ("surah-header", "basmala"):
                flush()
                lines.append({"t": "h", "s": int(ln["surah"])} if t == "surah-header" else {"t": "b"})
            else:
                segment.append(ln)
        flush()
        out.append(lines)
    full.sort()
    em = round(full[int(len(full) * 0.85)], 4) if full else 0
    return out, em


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
    pages_raw = []
    verse_page = {}
    verse_glyphs = {}
    for p in range(1, 605):
        d = fetch(LAYOUT_URL.format(p), f"layout/page-{p:03d}.json")
        pages_raw.append(d)
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
        # NFC first so alef + maddah becomes "آ" (آل عمران) before the marks are stripped
        name = TASHKEEL.sub("", unicodedata.normalize("NFC", r["name"])).replace("ٱ", "ا").strip()
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
    tj_pages, tj_em = tajweed_pages(pages_raw)
    print("tajweed line em", tj_em, file=sys.stderr)
    dump("pages_tajweed.json", tj_pages)
    lem = line_em(pages)
    print("line em", lem, file=sys.stderr)
    bold = page_weights()
    print("bold range", min(bold), max(bold), file=sys.stderr)
    dump("meta.json", {"surahs": surahs, "juz": juz, "hizb": hizb, "rub": rub, "bold": bold, "lineEm": lem, "lineEmTajweed": tj_em})
    dump("verses.json", {"page": vpage, "simple": simple, "clean": clean_norm})
    dump("glyphs.json", verse_glyphs)
    dump("tafsir_muyassar.json", tafsir)
    dump("translation_en.json", trans)
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)), file=sys.stderr)


if __name__ == "__main__":
    main()
