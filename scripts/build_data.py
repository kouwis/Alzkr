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
LAYOUT_URL = "https://raw.githubusercontent.com/zonetecde/mushaf-layout/72116ce4d405d67823804f0eed795c1e6409b4af/mushaf/page-{:03d}.json"
ALQURAN = "https://api.alquran.cloud/v1/"
FONTS = "https://raw.githubusercontent.com/nuqayah/qpc-fonts/8a4f39d563ea69c994416a1692827e38156c548d/"
# quran.com's own database for the same QCF V1 fonts: every glyph with its page, line and verse.
QURANCOM_DB = "https://raw.githubusercontent.com/quran/quran.com-images/dbda5689691defc7e3b28314cc2d035ff027795c/sql/02-database.sql"
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


TAJWEED_FONT = "https://raw.githubusercontent.com/mahmoud123-karash/quran_fonts/775af44fe73a53a2e7ea78573390fc557b16290c/assets/fonts/qcf_tajweed/QCF4{:03d}_COLOR-Regular.ttf.gz"

# Glyphs the source layout file is missing. Found by the continuity check below (inside a page
# the KFGQPC glyph codes run without gaps, so a skipped code is a missing word/ayah marker) and
# confirmed by rendering each glyph:
#   V1 p177  U+FBA5 = ayah marker ٦ after 8:6        V1 p254  U+FB9C = «وَلَقَدْ», first word of 13:38
#   V2 p177  U+FC95 = ayah marker ٦ after 8:6        V2 p254  U+FC8C = ayah marker ٣٧ after 13:37
GLYPH_FIXES = [
    ("qpcV1", "8:6:11", "append", 0xFBA5),
    ("qpcV1", "13:38:1", "prepend", 0xFB9C),
    ("qpcV2", "8:6:11", "append", 0xFC95),
    ("qpcV2", "13:37:19", "append", 0xFC8C),
    # end-of-verse marker of 2:181 (last glyph of page 27), missing in both prints' data
    ("qpcV1", "2:181:13", "append", 0xFBFE),
    ("qpcV2", "2:181:13", "append", 0xFCCD),
]


# On page 254 the source file's V1 codes for 13:38-13:42 are shifted by one glyph against
# their verse labels (because «وَلَقَدْ» was missing) and the page ends with an empty box
# glyph (U+FC15). Verse ranges below are the KFGQPC reference (nuqayah/qpc-fonts mushaf.txt).
P254_V1_VERSES = [(38, 0xFB9C, 0xFBD4), (39, 0xFBD5, 0xFBDE), (40, 0xFBDF, 0xFBEC), (41, 0xFBED, 0xFBFF), (42, 0xFC00, 0xFC14)]

# Verses where the reference files themselves are wrong (duplicated / truncated / "None"
# entries); everything else must match glyph for glyph.
REF_V1_KNOWN_BAD = {"2:79", "68:42", "69:8"}  # quran.com's data agrees with ours on these
REF_V2_KNOWN_BAD = {"2:1", "7:1", "19:1", "20:1", "26:1", "28:1", "29:1", "30:1", "31:1", "32:1",
                    "36:1", "40:1", "41:1", "42:1", "42:2", "43:1", "44:1", "45:1", "46:1", "70:15", "85:18"}


def v1_units(p, loc, g):
    """V1 glyphs of one word as (location, glyph) units, with page 254's verse labels corrected."""
    s_, a_, _ = loc.split(":")
    if p == 254 and s_ == "13" and 38 <= int(a_) <= 42:
        out = []
        for c in codes(g):
            if c == 0xFC15:
                continue  # empty box glyph at the end of the page
            ayah = next(a for a, lo, hi in P254_V1_VERSES if lo <= c <= hi)
            out.append((f"13:{ayah}", chr(c)))
        return out
    return [(f"{s_}:{a_}", g)]


def verify_against_reference(verse_codes, name, url, known_bad):
    """Second, independent check: per verse, the glyph codes must equal the KFGQPC reference."""
    import urllib.request as ur
    raw = ur.urlopen(ur.Request(url, headers={"User-Agent": "alzkr-build"}), timeout=60).read().decode("utf-8")
    ref = [ln.split(",", 1)[1].replace(" ", "") for ln in raw.splitlines() if ln.strip()]
    keys = list(verse_codes.keys())
    assert len(ref) == len(keys) == 6236, (len(ref), len(keys))
    bad = [k for k, r in zip(keys, ref) if "".join(chr(c) for c in verse_codes[k]) != r and k not in known_bad]
    if bad:
        raise SystemExit(f"{name}: verses differ from the KFGQPC reference: {bad[:30]}")
    print(f"{name}: all 6236 verses match the KFGQPC reference (except {len(known_bad)} known reference typos)", file=sys.stderr)


def apply_glyph_fixes(pages_raw):
    done = 0
    for d in pages_raw:
        for ln in d["lines"]:
            for w in ln.get("words", []):
                for field, loc, how, code in GLYPH_FIXES:
                    if w["location"] == loc and chr(code) not in w[field]:
                        w[field] = (w[field] + " " + chr(code)) if how == "append" else (chr(code) + " " + w[field])
                        done += 1
    assert done == len(GLYPH_FIXES), f"glyph fixes applied: {done}"


def codes(g):
    return [ord(c) for c in g if c != " "]


def check_run(label, page, seq, first, cmap):
    """Every code on a page must exist in that page's font, start at the font's first code and
    increase by exactly one (KFGQPC skips only U+FBB2..U+FBD2): no word missing, doubled or
    out of order."""
    errs = []
    if seq and seq[0] != first:
        errs.append(f"{label} p{page}: starts at {hex(seq[0])}")
    for x, y in zip(seq, seq[1:]):
        if y != x + 1 and not (x == 0xFBB1 and y == 0xFBD3):
            errs.append(f"{label} p{page}: {hex(x)} -> {hex(y)}")
    for c in seq:
        if c not in cmap:
            errs.append(f"{label} p{page}: {hex(c)} not in font")
    return errs


# The layout source left out the surah frames and basmala on two pages: page 586 (start of
# at-Takwir, and the frame of al-Infitar at its foot) and page 590 (al-Buruj, and the frame of
# at-Tariq). Each of those pages is 12 full text lines + frame + basmala + frame, like the print.
MISSING_HEADERS = {586: (81, 82), 590: (85, 86)}
# On page 207 the source puts Yunus's frame on the first line, above the last verses of
# at-Tawbah; in the print it is the last line of the page (Yunus starts on 208 with its basmala).
HEADER_TO_FOOT = {207}


def fix_headers(pages_raw):
    for p, (top, bottom) in MISSING_HEADERS.items():
        ls = pages_raw[p - 1]["lines"]
        if any(ln["type"] != "text" for ln in ls):
            raise SystemExit(f"page {p}: source changed, MISSING_HEADERS needs a look")
        ls[:0] = [{"type": "surah-header", "surah": str(top)}, {"type": "basmala"}]
        ls.append({"type": "surah-header", "surah": str(bottom)})
    for p in HEADER_TO_FOOT:
        ls = pages_raw[p - 1]["lines"]
        if ls[0]["type"] != "surah-header" or pages_raw[p]["lines"][0]["type"] != "basmala":
            raise SystemExit(f"page {p}: source changed, HEADER_TO_FOOT needs a look")
        ls.append(ls.pop(0))
    # A frame at the foot of a page belongs to the surah that starts on the next page, but the
    # source labels it with the surah that just ended. Every frame takes the surah of the
    # first verse after it.
    flat = [ln for pg in pages_raw for ln in pg["lines"]]
    for i, ln in enumerate(flat):
        if ln["type"] == "surah-header":
            nxt = next(x for x in flat[i + 1:] if x["type"] == "text")
            ln["surah"] = nxt["words"][0]["location"].split(":")[0]


def verify_structure(pages_raw):
    """Every surah has exactly one frame, right before its basmala (none for at-Tawbah; al-Fatiha's
    basmala is its first verse) and then its first verse; every page has 15 lines (8 on 1-2)."""
    errs = []
    flat = [(p, ln) for p, pg in enumerate(pages_raw, start=1) for ln in pg["lines"]]
    heads = [i for i, (_, ln) in enumerate(flat) if ln["type"] == "surah-header"]
    if [int(flat[i][1]["surah"]) for i in heads] != list(range(1, 115)):
        errs.append("frames are not exactly surahs 1..114 in order")
    for i in heads:
        sn = int(flat[i][1]["surah"])
        j = i + 1
        if sn not in (1, 9):
            if flat[j][1]["type"] != "basmala":
                errs.append(f"surah {sn}: no basmala after its frame")
            j += 1
        first = flat[j][1].get("words", [{}])[0].get("location", "")
        if first != f"{sn}:1:1":
            errs.append(f"surah {sn}: frame not followed by its first verse ({first})")
    nb = sum(1 for _, ln in flat if ln["type"] == "basmala")
    if nb != 112:
        errs.append(f"{nb} basmalas, expected 112")
    for p, pg in enumerate(pages_raw, start=1):
        want = 8 if p <= 2 else 15
        if len(pg["lines"]) != want:
            errs.append(f"page {p}: {len(pg['lines'])} lines")
    if errs:
        raise SystemExit("MUSHAF STRUCTURE CHECK FAILED:\n" + "\n".join(errs[:50]))
    print("structure ok: 114 frames on the right surahs, 112 basmalas, 15 lines on every page", file=sys.stderr)


def qurancom_db():
    """quran.com's QCF V1 database: glyph codes per verse, and per page line (with line kinds)."""
    path = os.path.join(CACHE, "qurancom", "qurancom-02-database.sql")
    fetch_file(QURANCOM_DB, path)
    sql = open(path, encoding="utf-8").read()

    def rows(tbl):
        for m in re.finditer(r"INSERT INTO `%s` VALUES (.*?);\n" % tbl, sql, re.S):
            for r in re.finditer(r"\(([^()]*)\)", m.group(1)):
                yield [x.strip().strip("'") for x in r.group(1).split(",")]

    glyph = {int(r[0]): (r[1], int(r[2])) for r in rows("glyph")}
    verses = {}
    for r in rows("glyph_ayah"):
        f, code = glyph[int(r[1])]
        if f.startswith("QCF_P"):
            verses.setdefault(f"{int(r[2])}:{int(r[3])}", []).append((int(r[4]), code))
    verses = {k: [c for _, c in sorted(v)] for k, v in verses.items()}
    raw = {}
    for r in rows("glyph_page_line"):
        f, code = glyph[int(r[1])]
        raw.setdefault((int(r[2]), int(r[3])), [r[5], []])
        if f.startswith("QCF_P") and not (int(r[2]) == 254 and code == 0xFC15):
            raw[(int(r[2]), int(r[3]))][1].append((int(r[4]), code))
    lines = {p: [(raw[(p, ln)][0], [c for _, c in sorted(raw[(p, ln)][1])]) for ln in range(1, 16) if (p, ln) in raw]
             for p in range(1, 605)}
    if len(verses) != 6236:
        raise SystemExit(f"quran.com data: {len(verses)} verses")
    return {"verses": verses, "lines": lines}


def verify_against_qurancom(pages_raw, qc):
    """Third, independent check of the normal mushaf: every verse glyph for glyph, every page's
    glyph stream, and the kind of every line (frame / basmala / text)."""
    errs = []
    ours = {}
    for p, d in enumerate(pages_raw, start=1):
        stream = []
        for ln in d["lines"]:
            for w in ln.get("words", []):
                for key, g in v1_units(p, w["location"], w["qpcV1"]):
                    ours.setdefault(key, []).extend(codes(g))
                    stream += codes(g)
        if stream != [c for _, cs in qc["lines"][p] for c in cs]:
            errs.append(f"page {p}: glyphs differ")
        kinds = [{"surah-header": "sura", "basmala": "bismillah"}.get(ln["type"], "ayah") for ln in d["lines"]]
        if kinds != [k for k, _ in qc["lines"][p]]:
            errs.append(f"page {p}: line kinds {kinds} vs {[k for k, _ in qc['lines'][p]]}")
    for key, cs in qc["verses"].items():
        if ours.get(key) != cs:
            errs.append(f"{key}: verse glyphs differ")
    if errs:
        raise SystemExit("QURAN.COM CHECK FAILED:\n" + "\n".join(errs[:50]))
    print("quran.com check: all 6236 verses and all 604 pages identical, glyph for glyph and line kind by line kind",
          file=sys.stderr)


def verify_plain(pages_raw):
    from fontTools.ttLib import TTFont
    errs = []
    for p, d in enumerate(pages_raw, start=1):
        cmap = TTFont(os.path.join(ASSETS, "qcf", f"p{p}.ttf")).getBestCmap()
        seq = [c for ln in d["lines"] for w in ln.get("words", []) for c in codes(w["qpcV1"])]
        errs += check_run("V1", p, seq, 0xFB51, cmap)
    if errs:
        raise SystemExit("PLAIN MUSHAF CHECK FAILED:\n" + "\n".join(errs[:50]))
    print("plain mushaf verified: every glyph of all 604 pages present, in order, in its font", file=sys.stderr)


def tajweed_pages(pages_raw):
    """Tajweed mushaf (KFGQPC QCF V4 colour fonts, 1421 print).

    The 1421 print breaks pages a little differently from the 1405 print our layout file
    follows (361 glyphs sit on a neighbouring page), so its pages are rebuilt from the V2
    glyph codes themselves: each page's codes start again at U+FC41, so a code that goes down
    means a new page. The result is checked like the plain mushaf (all 604 pages, every code
    present, in order, in its page font). Lines: each run of text between surah headers gets
    the number of lines its width needs (15 lines minus headers and basmalas per page), then
    its words are split so the lines are as even as possible (the last line of a surah may
    be short), which reproduces the printed line breaks."""
    import gzip
    from fontTools.ttLib import TTFont

    words = [(p, w["location"], w["qpcV2"]) for p, d in enumerate(pages_raw, start=1)
             for ln in d["lines"] for w in ln.get("words", [])]
    v2 = {}
    n, prev = 1, None
    for p, loc, g in words:
        cs = codes(g)
        if prev is not None and cs[0] < prev:
            n += 1
        prev = cs[-1]
        v2.setdefault(n, []).append((loc, g))
    assert n == 604, f"tajweed pages: {n}"

    fonts = {}

    def font(q):
        if q not in fonts:
            path = os.path.join(CACHE, "tajweed", f"p{q}.ttf")
            if not (os.path.exists(path) and os.path.getsize(path) > 0):
                fetch_file(TAJWEED_FONT.format(q), path + ".gz")
                with gzip.open(path + ".gz", "rb") as src, open(path, "wb") as dst:
                    dst.write(src.read())
            f = TTFont(path)
            fonts[q] = (f.getBestCmap(), f["hmtx"], f["head"].unitsPerEm)
        return fonts[q]

    errs = []
    for q in range(1, 605):
        cm = font(q)[0]
        errs += check_run("V2", q, [c for _, g in v2[q] for c in codes(g)], 0xFC41, cm)
    if errs:
        raise SystemExit("TAJWEED MUSHAF CHECK FAILED:\n" + "\n".join(errs[:50]))
    print("tajweed mushaf verified: every glyph of all 604 pages present, in order, in its font", file=sys.stderr)

    def width(q, g):
        cm, hm, upm = font(q)
        return sum(hm[cm[c]][0] for c in codes(g)) / upm

    # typical full-line width, from pages whose words are the same in both prints
    v1_locs = {p: [w["location"] for ln in d["lines"] for w in ln.get("words", [])] for p, d in enumerate(pages_raw, start=1)}
    tot = lines_n = 0
    for q in range(3, 605):
        if [loc for loc, _ in v2[q]] == v1_locs[q]:
            tot += sum(width(q, g) for _, g in v2[q])
            lines_n += sum(1 for ln in pages_raw[q - 1]["lines"] if ln["type"] == "text")
    line_em = tot / lines_n

    def partition(ws, k, target):
        n = len(ws)
        pre = [0.0]
        for x in ws:
            pre.append(pre[-1] + x)
        inf = float("inf")
        dp = [[inf] * (k + 1) for _ in range(n + 1)]
        back = [[0] * (k + 1) for _ in range(n + 1)]
        dp[0][0] = 0.0
        for j in range(1, k + 1):
            last = j == k
            for i in range(j, n + 1):
                best, arg = inf, 0
                for st in range(j - 1, i):
                    w = pre[i] - pre[st]
                    c = (4 * max(0.0, w - target) ** 2) if last else (w - target) ** 2
                    c += dp[st][j - 1]
                    if c < best:
                        best, arg = c, st
                dp[i][j], back[i][j] = best, arg
        cuts, i = [], n
        for j in range(k, 0, -1):
            st = back[i][j]
            cuts.append((st, i))
            i = st
        return list(reversed(cuts))

    out = []
    for q in range(1, 605):
        if q <= 2:  # the two opening pages: same words and lines in both prints
            lines = []
            for ln in pages_raw[q - 1]["lines"]:
                t = ln.get("type")
                if t == "surah-header":
                    lines.append({"t": "h", "s": int(ln["surah"])})
                elif t == "basmala":
                    lines.append({"t": "b"})
                else:
                    lines.append({"t": "x", "w": [[w["location"], w["qpcV2"]] for w in ln["words"]]})
            out.append(lines)
            continue
        segs, heads, extras = [], [], 0
        for loc, g in v2[q]:
            s_, a_, w_ = map(int, loc.split(":"))
            if a_ == 1 and w_ == 1:
                heads.append(s_)
                extras += 1 if s_ in (1, 9) else 2
                segs.append([])
            elif not segs:
                heads.append(None)
                segs.append([])
            segs[-1].append((loc, g))
        total = 15 - extras
        raw = [sum(width(q, g) for _, g in seg) / line_em for seg in segs]
        k = [max(1, round(r + 0.35)) for r in raw]
        while sum(k) < total:
            i = max(range(len(k)), key=lambda i: raw[i] - k[i]); k[i] += 1
        while sum(k) > total:
            i = max(range(len(k)), key=lambda i: (k[i] - raw[i]) if k[i] > 1 else -9); k[i] -= 1
        lines = []
        for head, seg, kk in zip(heads, segs, k):
            if head is not None:
                lines.append({"t": "h", "s": head})
                if head not in (1, 9):
                    lines.append({"t": "b"})
            ws = [width(q, g) for _, g in seg]
            for a, b in partition(ws, kk, line_em):
                lines.append({"t": "x", "w": [[loc, g] for loc, g in seg[a:b]]})
        out.append(lines)
    full = []
    for q, lines in enumerate(out, start=1):
        if q > 2:
            for ln in lines:
                if ln["t"] == "x":
                    full.append(sum(width(q, g) for _, g in ln["w"]))
    full.sort()
    em = round(full[int(len(full) * 0.85)], 4) if full else 0
    return out, em


# ---------- Uthmani text (copy / share) ----------
# KFGQPC "Uthmani Hafs" text, the same publisher as the mushaf fonts. The mirror replaced the
# open-tanween marks with their Unicode-standard code points but left a stray space after
# some of them ("هُدࣰ ى"); those spaces are removed before anything else.
HAFS_TEXT = "https://raw.githubusercontent.com/fawazahmed0/quran-api/93e6f64925ae76273520d636fcaaf061129f2316/editions/ara-quranuthmanihaf.json"
_TANWEEN_SPACE = re.compile("([\u08F0-\u08F2]) (?=[\u0627\u0649][\u064B-\u065F\u06D6-\u06ED]*(?: |$))")
_LET = set("ءابةتثجحخدذرزسشصضطظعغفقكلمنهويوى") | set("ٱأإآئؤ")
_SK = {"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ئ": "ي", "ؤ": "و"}
_WAQF = re.compile("[\u06D6-\u06DC\u06DE\u06E9\u0640\u200f\u0660-\u0669]")
_MARKS = set("\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0653\u0654\u0655\u0670\u06E5\u06E6\u06E7"
             "\u06E8\u065C\u06EC\u06DCSM\u06E0")
_TAN = {"\u064E": "\u064B", "\u064F": "\u064C", "\u0650": "\u064D"}
# The second text the KFGQPC one is checked against is the per-word text of the layout source
# (Tanzil-style encoding, prepared independently). Differences that are only spelling
# conventions between the two encodings are normalised away; everything listed below was
# checked by hand.
# - layout text errors (the KFGQPC text is right): 2:72 stray sukun, 11:13 «افتراه» in plain
#   spelling, 80:25 missing hamza
# - same reading, different encoding: tanween written around a hamza seat (4:92, 12:31, 52:19,
#   69:24, 77:43), iqlab tanween with hamza below (27:22, 52:21), small seen code (52:37),
#   hamza seat on waw (56:23), 17:7 small waw + madd that Tanzil adds and KFGQPC does not
UTHMANI_TASHKEEL_KNOWN = {"2:72", "11:13", "80:25", "4:92", "12:31", "52:19", "69:24", "77:43",
                          "27:22", "52:21", "52:37", "56:23", "17:7"}
UTHMANI_SKELETON_KNOWN = {"11:13"}
# words written joined in one text and apart in the other («بعد ما», «لوما», «مال», «إل ياسين»)
UTHMANI_WORDS_KNOWN = {"2:181", "8:6", "13:37", "15:7", "27:20", "36:22", "37:130"}


def _skel(t):
    return "".join(_SK.get(c, c) for c in unicodedata.normalize("NFC", t) if c in _LET)


def _common(t):
    t = unicodedata.normalize("NFD", t)
    t = t.replace("\u0627\u0654\u0653", "\u064E\u0654\u0627")
    t = re.sub("([\u064E\u064F\u0650])(\u0651?)([\u06E2\u06ED])", lambda m: _TAN[m.group(1)] + m.group(2), t)
    t = t.replace("\u06EA", "\u065C").replace("\u06EB", "\u06EC").replace("\u06E3", "\u06DC")
    t = re.sub("\u064A([\u064D\u0650])\u0655", "\u064A\\1\u0654", t)
    t = re.sub("[\u06E2\u06ED]", "", t)
    return unicodedata.normalize("NFD", t)


def _sig(t, kfgqpc):
    t = _WAQF.sub("", t)
    if kfgqpc:
        t = t.replace("\u0652", "S").replace("\u06E1", "\u0652")
        t = t.replace("\u08F0", "\u064B").replace("\u08F1", "\u064C").replace("\u08F2", "\u064D")
    else:
        t = t.replace("\u06DF", "S")
    t = _common(t)
    out = []
    for w in t.split():
        if any(c in _LET for c in w):
            out.append("".join("_" if c in _LET else c for c in w if c in _LET or c in _MARKS))
    return "".join(out)


def _words(t):
    return [w for w in t.split() if any(c in _LET for c in w)]


def uthmani_text(pages_raw):
    d = fetch(HAFS_TEXT, "uthmani_hafs.json")["quran"]
    if len(d) != 6236:
        raise SystemExit(f"uthmani text: {len(d)} verses")
    kf = {f"{x['chapter']}:{x['verse']}": _TANWEEN_SPACE.sub("\\1", x["text"]).strip() for x in d}
    lay = {}
    for pg in pages_raw:
        for ln in pg["lines"]:
            for w in ln.get("words", []):
                s_, a_, _ = w["location"].split(":")
                lay.setdefault(f"{s_}:{a_}", []).append(w["word"])
    errs = []
    for key, words in lay.items():
        L, K = " ".join(words), kf[key]
        if _skel(L) != _skel(K) and key not in UTHMANI_SKELETON_KNOWN:
            errs.append(f"{key} letters")
        if _sig(L, False) != _sig(K, True) and key not in UTHMANI_TASHKEEL_KNOWN:
            errs.append(f"{key} tashkeel")
        if len(words) != len(_words(K)) and key not in UTHMANI_WORDS_KNOWN:
            errs.append(f"{key} words {len(words)}/{len(_words(K))}")
    if len(lay) != 6236 or errs:
        raise SystemExit(f"UTHMANI TEXT CHECK FAILED ({len(lay)} verses): {errs[:40]}")
    print("uthmani text: all 6236 verses match the second source letter by letter and mark by mark "
          f"(except {len(UTHMANI_TASHKEEL_KNOWN)} hand-checked encoding/typo cases)", file=sys.stderr)
    out = []
    for s_ in range(1, 115):
        out.append([kf[f"{s_}:{a_}"] for a_ in range(1, 1000) if f"{s_}:{a_}" in kf])
    return out


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
    v1_codes = {}
    verse_page = {}
    verse_glyphs = {}
    for p in range(1, 605):
        pages_raw.append(fetch(LAYOUT_URL.format(p), f"layout/page-{p:03d}.json"))
    apply_glyph_fixes(pages_raw)
    fix_headers(pages_raw)
    verify_structure(pages_raw)
    verify_plain(pages_raw)
    qc = qurancom_db()
    verify_against_qurancom(pages_raw, qc)
    for p, d in enumerate(pages_raw, start=1):
        # the glyphs of the page in reading order, as drawing units (word / waqf mark / ayah marker
        # groups exactly as the source spaces them); words stay whole, a unit never spans two lines
        units = []
        for ln in d["lines"]:
            for w in ln.get("words", []):
                for key, g in v1_units(p, w["location"], w["qpcV1"]):
                    for part in g.split(" "):
                        units.append((key, part))
        # one glyph at a time, remembering which drawing unit it came from
        stream = [(key, c, u) for u, (key, g) in enumerate(units) for c in codes(g)]
        # the line breaks of the print, from quran.com's line data (the layout source moves the
        # ayah marker that starts a line in the print to the end of the line before it)
        lines, i = [], 0
        for kind, line_codes in qc["lines"][p]:
            if kind == "sura":
                lines.append({"t": "h", "s": 0})
                continue
            if kind == "bismillah":
                lines.append({"t": "b"})
                continue
            seg = stream[i:i + len(line_codes)]
            if [c for _, c, _ in seg] != line_codes:
                raise SystemExit(f"page {p}: glyph stream does not match the print's lines")
            i += len(line_codes)
            ws = []
            for key, c, u in seg:
                if ws and ws[-1][2] == u:
                    ws[-1][1] += chr(c)
                else:
                    ws.append([key, chr(c), u])
            ws = [[key + ":0", g] for key, g, _ in ws]
            for kk, g in ws:
                key = kk[:-2]
                verse_page.setdefault(key, p)
                v1_codes.setdefault(key, []).extend(codes(g))
                chunks = verse_glyphs.setdefault(key, [])
                if chunks and chunks[-1]["p"] == p:
                    chunks[-1]["g"] += " " + g
                else:
                    chunks.append({"p": p, "g": g})
            lines.append({"t": "x", "w": ws})
        if i != len(stream):
            raise SystemExit(f"page {p}: {len(stream) - i} glyphs left over after the print's lines")
        pages.append(lines)
    # each frame carries the surah whose first verse follows it
    flat = [ln for pg in pages for ln in pg]
    for k, ln in enumerate(flat):
        if ln["t"] == "h":
            ln["s"] = int(next(x for x in flat[k + 1:] if x["t"] == "x")["w"][0][0].split(":")[0])
    if [ln["s"] for ln in flat if ln["t"] == "h"] != list(range(1, 115)):
        raise SystemExit("frames are not surahs 1..114 in order")
    assert len(pages) == 604
    # the exact glyphs the app will draw: same continuity check on the final pages
    from fontTools.ttLib import TTFont
    errs = []
    for p, lines in enumerate(pages, start=1):
        seq = [c for ln in lines if ln["t"] == "x" for _, g in ln["w"] for c in codes(g)]
        errs += check_run("V1-out", p, seq, 0xFB51, TTFont(os.path.join(ASSETS, "qcf", f"p{p}.ttf")).getBestCmap())
    if errs:
        raise SystemExit("PLAIN MUSHAF OUTPUT CHECK FAILED:\n" + "\n".join(errs[:50]))
    verify_against_reference(v1_codes, "plain mushaf (V1)", FONTS + "mushaf.txt", REF_V1_KNOWN_BAD)
    v2_codes = {}
    for d in pages_raw:
        for ln in d["lines"]:
            for w in ln.get("words", []):
                s_, a_, _ = w["location"].split(":")
                v2_codes.setdefault(f"{s_}:{a_}", []).extend(codes(w["qpcV2"]))
    verify_against_reference(v2_codes, "tajweed mushaf (V2)", FONTS + "mushaf-v2.txt", REF_V2_KNOWN_BAD)
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
    # the app downloads these fonts itself; it keeps a download only if it is byte-for-byte the
    # font checked above
    import hashlib
    dump("tajweed_sha256.json", [hashlib.sha256(open(os.path.join(CACHE, "tajweed", f"p{q}.ttf"), "rb").read()).hexdigest()
                                 for q in range(1, 605)])
    lem = line_em(pages)
    print("line em", lem, file=sys.stderr)
    bold = page_weights()
    print("bold range", min(bold), max(bold), file=sys.stderr)
    dump("meta.json", {"surahs": surahs, "juz": juz, "hizb": hizb, "rub": rub, "bold": bold, "lineEm": lem, "lineEmTajweed": tj_em})
    uthmani = uthmani_text(pages_raw)
    assert [len(x) for x in uthmani] == [len(x) for x in simple]
    dump("verses.json", {"page": vpage, "simple": uthmani, "clean": clean_norm})
    dump("glyphs.json", verse_glyphs)
    dump("tafsir_muyassar.json", tafsir)
    dump("translation_en.json", trans)
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)), file=sys.stderr)


if __name__ == "__main__":
    main()
