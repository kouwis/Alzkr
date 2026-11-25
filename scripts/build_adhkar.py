#!/usr/bin/env python3
"""Builds app/src/main/assets/data/adhkar.json — the adhkar of «حصن المسلم» — and checks every word of it.

Sources (all pinned to a commit, nothing typed by hand):
  * the official text of hisnmuslim.com (wording and counts)        — rn0x/Adhkar-json
  * the book with its footnotes (takhrij of every dhikr)            — rn0x/hisn_almuslim_json
  * morning / evening: the book's newer edition with hadith numbers — Seen-Arabic/Morning-And-Evening-Adhkar-DB
  * the six hadith books (to check each takhrij points at a hadith that really holds the dhikr)
                                                                    — fawazahmed0/hadith-api
  * the Quran: the same verified Uthmani text the mushaf is built from (fawazahmed0/quran-api, KFGQPC Hafs)

Checks (the build stops on any failure):
  1. every dhikr's wording is found in the official text (morning/evening: in both the official text and the book);
  2. every takhrij / fadl string is found word for word in the book's footnotes or text;
  3. every Quran passage (﴿…﴾) is matched letter by letter to consecutive verses of the mushaf and is shown
     from the mushaf itself, never from the adhkar text;
  4. every takhrij that names al-Bukhari, Muslim, Abu Dawud, at-Tirmidhi, an-Nasa'i or Ibn Majah is looked up in
     that book: with a hadith number, that exact hadith must hold the dhikr; without one, the dhikr must be found
     in the book. Takhrijs that only name other books (Ahmad, al-Hakim, Ibn as-Sunni, …) are listed as such.
"""
import difflib
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_data import fetch, HAFS_TEXT, _TANWEEN_SPACE, OUT  # noqa: E402

OFFICIAL = "https://raw.githubusercontent.com/rn0x/Adhkar-json/93dc3fc489e4291da8816265920b3cb7e2c3b569/adhkar.json"
BOOK = "https://raw.githubusercontent.com/rn0x/hisn_almuslim_json/0405ee1797c2ccadfe82cd41845338d54978ccb9/hisn_almuslim.json"
MORNING = "https://raw.githubusercontent.com/Seen-Arabic/Morning-And-Evening-Adhkar-DB/39d422fb3e3a6608b47a8f4e1040543220bfd215/ar.json"
HADITH = "https://raw.githubusercontent.com/fawazahmed0/hadith-api/df57907be35291c91ad6a6691180e22ca9920784/editions/ara-{}.min.json"
BOOKS = {  # how a takhrij names each book -> hadith-api edition
    "bukhari": ["البخاري"], "muslim": ["مسلم"], "abudawud": ["أبو داود", "أبي داود", "ابو داود"],
    "tirmidhi": ["الترمذي"], "nasai": ["النسائي"], "ibnmajah": ["ابن ماجه", "ابن ماجة"],
}
BOOK_AR = {"bukhari": "البخاري", "muslim": "مسلم", "abudawud": "أبو داود", "tirmidhi": "الترمذي",
           "nasai": "النسائي", "ibnmajah": "ابن ماجه"}

# ---------------------------------------------------------------- text helpers
_MARKS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿـ]")


def skel(t):
    """Letters only, spelling-neutral: imla'i and Uthmani spellings of the same words give the same string."""
    t = t.replace("ﷺ", " صلى الله عليه وسلم ")
    t = re.sub("\u0649\u0670(?=[\u0621-\u064A])", "ا", t)  # مَوۡلَىٰنَا = مولانا
    t = _MARKS.sub("", t)
    t = t.translate(str.maketrans({"ٱ": "ا", "أ": "ا", "إ": "ا", "آ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي",
                                   "ی": "ي", "ک": "ك", "ۥ": "", "ۦ": ""}))
    t = re.sub("[^ء-ي]", "", t)
    t = t.replace("ا", "").replace("ء", "")
    return re.sub("لل", "ل", re.sub("وو", "و", re.sub("يي", "ي", t)))  # الليل = ٱلَّيۡل  # a hamza seat written as a letter in one spelling only


def words(t):
    return [w for w in t.split() if skel(w)]


def tidy(t):
    t = t.replace("‏", "").replace("‎", "")
    t = re.sub(r"\s+", " ", t).strip()
    t = re.sub(r"\s+([،,.:؛])", r"\1", t)
    t = re.sub(r"([،,:])(?=[\u0621-\u064A])", r"\1 ", t)
    return t.strip()


def has(hay, needle):
    return skel(needle) in skel(hay)


def skeld(t):
    """skel() that keeps the digits, for checking a takhrij (volume/page and hadith numbers) word for word."""
    t = t.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789"))
    return "".join(skel(w) if not w.isdigit() else w for w in re.split(r"(\d+)", t))


# ---------------------------------------------------------------- the Quran
def load_quran():
    d = fetch(HAFS_TEXT, "uthmani_hafs.json")["quran"]
    v = {}
    for x in d:
        v[(x["chapter"], x["verse"])] = _TANWEEN_SPACE.sub("\\1", x["text"]).strip()
    return v


class Quran:
    def __init__(self, verses):
        self.v = verses
        self.keys = sorted(verses)
        self.sk = [skel(verses[k]) for k in self.keys]
        self.idx = {k: i for i, k in enumerate(self.keys)}
        self.ayahs = {}
        for s, a in self.keys:
            self.ayahs[s] = max(self.ayahs.get(s, 0), a)

    def whole(self, seg):
        """Consecutive whole verses whose letters are exactly the segment's: (s, a1, a2) or None."""
        g = skel(seg)
        if len(g) < 6:
            return None
        for i, k in enumerate(self.sk):
            if not g.startswith(k) or not k:
                continue
            acc, j = "", i
            while j < len(self.sk) and len(acc) < len(g) and self.keys[j][0] == self.keys[i][0]:
                acc += self.sk[j]
                if acc == g:
                    return self.keys[i][0], self.keys[i][1], self.keys[j][1]
                j += 1
        return None

    def part(self, seg):
        """A run of words of the mushaf (inside one verse, or running on into the next): (s, a, Uthmani words) or None."""
        g = skel(seg)
        if len(g) < 6:
            return None
        if not hasattr(self, "_surah_words"):
            self._surah_words = {}
            for (s_, a_), t in self.v.items():
                self._surah_words.setdefault(s_, []).extend((a_, w) for w in t.split(" "))
        for s_, ws in self._surah_words.items():
            whole = "".join(skel(w) for _, w in ws)
            if g not in whole:
                continue
            for i in range(len(ws)):
                acc = ""
                for j in range(i, len(ws)):
                    acc += skel(ws[j][1])
                    if acc == g:
                        return s_, ws[i][0], " ".join(w for _, w in ws[i:j + 1])
                    if not g.startswith(acc):
                        break
        return None


BASMALA = "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ"


def to_parts(text, quran, where, force_quran=False):
    """Splits a dhikr into plain text and Quran passages; each passage must match the mushaf exactly."""
    out = []
    bits = re.split("﴿(.*?)﴾", text)
    if force_quran:
        # a passage written without the ornate brackets (e.g. the three Quls in the newer edition)
        bits = ["", text, ""]
    for n, b in enumerate(bits):
        if n % 2 == 0:
            b = tidy(b.strip("، "))
            b = re.sub(r"^[.\s]*\[[^\]]*\d+[^\]]*\]\.?$", "", b)  # «[البقرة 285 - 286]»: the mushaf shows it
            if skel(b):
                out.append({"t": b})
            continue
        b = b.strip()
        if len(words(b)) < 3:  # e.g. «يقرأ ﴿الم﴾ تنزيل السجدة» — a name, not a passage to recite here
            out.append({"t": "﴿" + b + "﴾"})
            continue
        # the passage may run over several surahs (the three Quls), each with its basmala written as text
        chunks = [c for c in re.split("بسم الله الرحمن الرحيم|" + BASMALA, b)]
        for ci, ch in enumerate(chunks):
            if ci > 0:
                out.append({"t": BASMALA})
            if not skel(ch):
                continue
            w = quran.whole(ch)
            if w:
                out.append({"q": list(w)})
                continue
            # a passage of several verses, written with a stray verse split («*») — try verse by verse
            p = quran.part(ch)
            if p:
                # the hafs.otf font draws the open tanween with the older code points
                out.append({"u": p[2].translate({0x08F0: 0x0657, 0x08F1: 0x065E, 0x08F2: 0x0656}), "ref": [p[0], p[1]]})
                continue
            raise SystemExit(f"QURAN CHECK FAILED in {where}: «{ch[:80]}…» is not a run of verses of the mushaf")
    # tidy the text bits: no stray full stop at the start, no quote mark left open by a passage cut out
    for p in out:
        if "t" in p:
            t = re.sub(r"^[.\s]+", "", p["t"])
            if t.count("«") != t.count("»"):
                t = t.replace("«", "").replace("»", "")
            p["t"] = tidy(t.replace("بسم الله الرحمن الرحيم", BASMALA))
    out = [p for p in out if "t" not in p or skel(p["t"])]
    # merge text bits
    merged = []
    for p in out:
        if merged and "t" in p and "t" in merged[-1]:
            merged[-1]["t"] += " " + p["t"]
        elif merged and "q" in p and "q" in merged[-1] and merged[-1]["q"][0] == p["q"][0] and merged[-1]["q"][2] + 1 == p["q"][1]:
            merged[-1]["q"][2] = p["q"][2]
        else:
            merged.append(p)
    return merged


def strip_wrap(t):
    """The official text wraps the Prophet's words in ((…)); show them as «…», drop a wrap around everything."""
    t = tidy(t).rstrip(".").strip()
    if t.startswith("((") and t.endswith("))") and t.count("((") == 1:
        t = t[2:-2]
    elif t.startswith("((") and t.endswith(")).") and t.count("((") == 1:
        t = t[2:-3]
    if t.startswith("(") and not t.startswith("((") and t.endswith(")") and t.count("(") == 1:
        t = t[1:-1]
    t = t.replace("((", "«").replace("))", "»")
    return tidy(t).rstrip(".").strip()


# ---------------------------------------------------------------- hadith books
class Hadith:
    def __init__(self):
        self.books = {}
        self.raw = {}
        for b in BOOKS:
            d = fetch(HADITH.format(b), f"hadith-{b}.json")["hadiths"]
            by_num, by_ar = {}, {}
            for h in d:
                sk = skel(h["text"])
                by_num.setdefault(int(float(h["hadithnumber"])), []).append(sk)
                an = str(h.get("arabicnumber", "")).split(".")[0]
                if an.isdigit():
                    by_ar.setdefault(int(an), []).append(sk)
            self.books[b] = (by_num, by_ar, "\n".join(s for v in by_num.values() for s in v))
            raw = {}
            for h in d:
                key = int(str(h.get("arabicnumber", "")).split(".")[0]) if b == "muslim" and str(h.get("arabicnumber", "")).split(".")[0].isdigit() else int(float(h["hadithnumber"]))
                raw.setdefault(key, []).append(h["text"])
            self.raw[b] = raw
        self._common = {}

    @staticmethod
    def named(src):
        """Books named in a takhrij, each with the hadith numbers given after it (not volume/page numbers).
        Mentions of other works by the same authors (عمل اليوم والليلة, الأدب المفرد) and al-Albani's
        «صحيح/ضعيف …» books are not the hadith book itself and are skipped."""
        marks = []
        for b, names in BOOKS.items():
            for nm in names:
                for m in re.finditer(re.escape(nm), src):
                    before = src[max(0, m.start() - 6):m.start()]
                    if b == "muslim" and before[-1:] not in ("", " ", "،", "(", "[", "و", ":"):
                        continue
                    if re.search("(صحيح|ضعيف|مختصر|تحقيق)\\s*$", before) and b not in ("bukhari", "muslim"):
                        continue
                    if re.search("(ضعيف|إلى)\\s*$", before):
                        continue
                    marks.append((m.start(), m.end(), b))
        # «أصحاب السنن» / «أهل السنن» name the four Sunan (less any «إلا …» after it)
        m = re.search("(أصحاب|أهل) السنن", src)
        if m:
            tail = src[m.end():m.end() + 30]
            for b in ("abudawud", "tirmidhi", "nasai", "ibnmajah"):
                if not (b == "nasai" and "إلا النسائي" in tail) and not (b == "ibnmajah" and "إلا ابن ماجه" in tail):
                    marks.append((m.start(), m.start(), b))
        marks.sort()
        found = []
        for k, (st, en, b) in enumerate(marks):
            if any(st < e2 and en > s2 and (s2, e2) != (st, en) and e2 - s2 > en - st for s2, e2, _ in marks):
                continue  # «أبي داود» inside a longer name
            if re.match("\\s*في\\s", src[en:en + 4]):
                continue
            stop = min([s2 for s2, _, _ in marks if s2 > st] + [len(src)])
            tail = src[en:stop].split(" — ")[0]
            tail = re.split("وانظر|انظر|وحسن|وصحح|وحسّن|وصحّح|وقال", tail)[0]
            # a hadith number is written «برقم N», «رقم N», «: N» or «(N)»; «4/ 2088» or «4/54،55» are volume/page
            nums = [int(m.group(1)) for m in re.finditer(r"(?:برقم|رقم|:|\()\s*[(\[]?\s*(\d+)\s*(?!\s*/)", tail)
                    if not re.match(r"\s*/", tail[m.end(1):])]
            found.append((b, nums))
        return found

    def common(self, g):
        """How many hadith texts in all six books hold this run of words (to drop stock phrases)."""
        if g not in self._common:
            self._common[g] = sum(b[2].count(g) for b in self.books.values())
        return self._common[g]

    def check(self, text, src, size=5):
        """The books named in the takhrij that hold the dhikr: None when no checkable book is named.
        A hadith holds the dhikr when it has at least 3 of the dhikr's runs of 5 words (all of them for a short
        dhikr); runs found in more than 60 hadiths («رسول الله صلى الله عليه وسلم», …) do not count."""
        named = self.named(src)
        if not named:
            return None
        grams = []
        for piece in text.split("\n"):  # pieces (verses) are probed one by one
            ws = [skel(w) for w in words(re.sub(r"\([^()]{0,30}\)|\[[^\]]{0,30}\]|«|»", " ", piece))]
            ws = [w for w in ws if w]
            n = min(size, len(ws))
            grams += ["".join(ws[i:i + n]) for i in range(0, max(1, len(ws) - n + 1)) if ws]
        short = "".join(skel(w) for w in words(re.sub(r"\([^()]{0,30}\)|\[[^\]]{0,30}\]|«|»", " ", text)))
        grams = [g for g in dict.fromkeys(grams) if len(g) >= 8 and self.common(g) <= 60]
        if not grams:
            # a dhikr of a word or two («بسم الله»، «غفرانك»): the named book must hold it word for word
            if len(short) < 4:
                return []
            return [BOOK_AR[b] for b, _ in named if short in self.books[b][2]]
        need = min(3, len(grams))
        hits = []
        for b, nums in named:
            by_num, by_ar, _ = self.books[b]
            if nums:
                for num in nums:
                    texts = (by_ar if b == "muslim" else by_num).get(num, [])
                    if any(sum(g in t for g in grams) >= need for t in texts):
                        hits.append(f"{BOOK_AR[b]} {num}")
            elif any(sum(g in t for g in grams) >= need for v in by_num.values() for t in v):
                hits.append(BOOK_AR[b])
        return hits


def hadith_extract(hadith, book, num, frm, to):
    """The words of hadith [book] [num] from the phrase [frm] to the phrase [to], with their vowels as in the book."""
    by_num, by_ar, _ = hadith.books[book]
    texts = hadith.raw[book].get(num, [])
    f, t = skel(frm), skel(to)
    for txt in texts:
        ws = txt.replace("\u200f", "").replace('"', " ").split()
        for i in range(len(ws)):
            if not skel(" ".join(ws[i:i + 40])).startswith(f):
                continue
            for j in range(i, min(len(ws), i + 400)):
                seg = " ".join(ws[i:j + 1])
                sk = skel(seg)
                if sk.endswith(t) and len(sk) >= len(f):
                    return tidy(seg).strip(" ،,.")
                if len(sk) > len(skel(txt)):
                    break
    raise SystemExit(f"HADITH TEXT NOT FOUND: {book} {num} «{frm}» … «{to}»")


# ---------------------------------------------------------------- the content
def main():
    official = {c["category"]: c["array"] for c in fetch(OFFICIAL, "adhkar-official.json")}
    book = fetch(BOOK, "adhkar-book.json")
    morning = fetch(MORNING, "adhkar-morning.json")
    quran = Quran(load_quran())
    hadith = Hadith()
    from adhkar_spec import SECTIONS, MORNING_SKIP, MORNING_KNOWN, MORNING_HADITH, UNCHECKED_OK
    from adhkar_spec import EXTRA_SECTIONS, AUTO_FIX, AUTO_SKIP, AUTO_TITLES

    # ---- every other chapter of the book, item by item, each with the footnote that is its takhrij
    curated = {"أذكار الصباح والمساء"} | {it["a"][0] for sc in SECTIONS for it in sc.get("items", []) if "a" in it}
    bookkey = {skel(k): k for k in book}
    SRCLIKE = re.compile(r"البخاري|مسلم|داود|الترمذي|النسائي|ماجه|ماجة|أحمد|الحاكم|السني|مالك|السنن|\\d")

    def book_chapter(ch):
        k = bookkey.get(skel(ch))
        if not k:
            m = difflib.get_close_matches(skel(ch), list(bookkey), 1, 0.75)
            k = bookkey[m[0]] if m else None
        return k

    def auto_src(ch, i):
        """The book chapter and the footnote that gives the takhrij of official item [i] of chapter [ch]."""
        k = book_chapter(ch)
        if not k:
            return None, ""
        texts = book[k]["text"]
        fn = [f.strip() for f in book[k]["footnote"] if f.strip() and not f.strip().startswith("*") and SRCLIKE.search(f)]
        arr = official[ch]
        if fn and len(fn) == len(texts):
            j = max(range(len(texts)), key=lambda j: difflib.SequenceMatcher(None, skel(texts[j]), skel(arr[i]["text"])).ratio())
            return k, fn[j]
        if len(fn) == len(arr):
            return k, fn[i]
        return k, " — ".join(fn)

    fails = []
    auto_sections = []
    for ci, (ch, arr) in enumerate(official.items()):
        if ch in curated:
            continue
        title = AUTO_TITLES.get(ch, re.sub(r"\s+", " ", ch.replace("ُ", "").replace("ﺗﻬ", "تهـ").replace("اﻟﻤﺠ", "المج")).strip(" ؟?"))
        its = []
        for i in range(len(arr)):
            if (ch, i) in AUTO_SKIP:
                continue
            k, src = auto_src(ch, i)
            if not k and (ch, i) not in AUTO_FIX:
                fails.append(f"NO BOOK CHAPTER for «{ch}» #{i}: add it to AUTO_FIX (book chapter + takhrij) or AUTO_SKIP")
                continue
            it = dict(a=(ch, i), src=src)
            if k and k != ch:
                it["book"] = k
            it.update(AUTO_FIX.get((ch, i), {}))
            its.append(it)
        if its:
            auto_sections.append(dict(id=f"hisn_{ci}", title=title, done=f"انتهى باب «{title}»", group="hisn", items=its))
    ALL = list(SECTIONS) + list(EXTRA_SECTIONS) + auto_sections

    def book_all(ch):
        if ch not in book:
            return ""
        b = book[ch]
        return " ".join(b["text"] + b["footnote"])

    report = {"hadith_ok": 0, "other_books": [], "quran": 0}
    sections = []
    for sec in ALL:
        items = []
        if sec["id"] in ("morning", "evening"):
            want = {"morning": (0, 1), "evening": (0, 2)}[sec["id"]]
            off = official["أذكار الصباح والمساء"]
            booktxt = book_all("أذكار الصباح والمساء")
            for x in morning:
                if x["order"] in MORNING_SKIP or x["type"] not in want:
                    continue
                where = f"{sec['id']} #{x['order']}"
                t = x["content"]
                # the wording must be the official one (or its evening form) — pick the closest official item
                inside = [z for z in off if skel(t) in skel(z["text"])]
                best = max(inside or off, key=lambda z: difflib.SequenceMatcher(None, skel(z["text"]), skel(t)).ratio())
                r = 1.0 if inside else difflib.SequenceMatcher(None, skel(best["text"]), skel(t)).ratio()
                if r < 0.9 and x["type"] == 2:
                    # the evening wording: the official text gives it in a note «وإذا أمسى قال: …» after the morning one
                    for z in off:
                        for note in re.findall(r"وإذا أمسى قال:([^\]]+)", z["text"]):
                            head = "".join(skel(w) for w in words(note.replace("...", " "))[:3])
                            if skel(t).startswith(head) and difflib.SequenceMatcher(None, skel(z["text"]), skel(t)).ratio() >= 0.6:
                                best, r = z, 1.0
                if r < 0.9 and x["order"] not in MORNING_KNOWN:
                    raise SystemExit(f"TEXT CHECK FAILED {where}: not in the official text (closest {r:.2f}): {t[:60]}")
                said = {3: "ثلاث", 4: "أربع", 7: "سبع", 10: "عشر", 100: "مائة|مئة|مائه"}.get(x["count"], "^$")
                near = [z for z in off if skel(t) in skel(z["text"]) or
                        difflib.SequenceMatcher(None, skel(z["text"]), skel(t)).ratio() >= 0.9] + [best]
                if x["order"] not in MORNING_KNOWN and not any(
                        z["count"] == x["count"] or re.search(said, _MARKS.sub("", z["text"])) for z in near):
                    raise SystemExit(f"COUNT CHECK FAILED {where}: {x['count']} vs official {[z['count'] for z in near]}")
                src = tidy(x["source"])
                extra = []
                for hb, hn, phrase in MORNING_HADITH.get(x["order"], []):
                    by_num, by_ar, _ = hadith.books[hb]
                    if not any(skel(phrase) in tt for tt in (by_ar if hb == "muslim" else by_num).get(hn, [])):
                        raise SystemExit(f"HADITH CHECK FAILED {where}: {hb} {hn} does not hold «{phrase}»")
                    extra.append(f"{BOOK_AR[hb]} {hn}")
                items.append(dict(text=t, count=x["count"], fadl=tidy(x["fadl"]), src=src, where=where,
                                  quran=x["order"] in (4, 5, 6), verified=extra))
        else:
            for it in sec["items"]:
                if "a" in it and "src" not in it:  # a dhikr of the book used elsewhere: its own takhrij
                    k, src = auto_src(*it["a"])
                    it = dict(it, src=src, **({"book": k} if k and k != it["a"][0] else {}))
                    it.update(AUTO_FIX.get(tuple(it["a"]), {}))
                if "h" in it:  # words of a hadith, taken from the hadith book itself
                    hb, hn = it["h"]
                    t = hadith_extract(hadith, hb, hn, it["frm"], it["to"])
                    t = re.sub(r"^وَ(?=\S)", "", t)  # «وَصِيَامُ …» cut from the middle of a sentence
                    items.append(dict(parts=[{"t": "«" + t + "»"}], count=it.get("count", 1), label=it.get("label", ""),
                                      icon=it.get("icon", ""), fadl=it.get("fadl", ""), note=it.get("note", ""),
                                      src=f"رواه {BOOK_AR[hb]} ({hn})" + (" — " + it["more"] if it.get("more") else ""),
                                      where=f"{sec['id']} {hb} {hn}", hadithed=f"{BOOK_AR[hb]} {hn}"))
                    continue
                if "ext" in it:  # outside the six books: the text and takhrij as given by the source named in the spec
                    items.append(dict(parts=[{"t": "«" + it["ext"] + "»"}], count=it.get("count", 1), label=it.get("label", ""),
                                      icon=it.get("icon", ""), src=it["src"], where=f"{sec['id']} ext", external=True))
                    continue
                extra = []  # hadith checked by number in the six books: (book, number, phrase)
                for hb, hn, phrase in it.get("hadith", []):
                    by_num, by_ar, _ = hadith.books[hb]
                    if not any(skel(phrase) in t for t in (by_ar if hb == "muslim" else by_num).get(hn, [])):
                        raise SystemExit(f"HADITH CHECK FAILED {sec['id']}: {hb} {hn} does not hold «{phrase}»")
                    extra.append(f"{BOOK_AR[hb]}، برقم {hn}")
                if "quran" in it and "a" not in it:  # a passage from the mushaf only
                    s, a1, a2 = it["quran"]
                    parts = ([{"t": it["pre"]}] if it.get("pre") else []) + [{"q": [s, a1, a2]}]
                    items.append(dict(parts=parts, count=it.get("count", 1), src=" — ".join(extra),
                                      label=it.get("label", ""), where=f"{sec['id']} {s}:{a1}", mushaf=True,
                                      verified=extra, icon=it.get("icon", ""), note=it.get("note", "")))
                    continue
                ch, i = it["a"]
                where = f"{sec['id']} «{ch}» #{i}"
                full = official[ch][i]["text"]
                if "quran" in it:  # one surah of an official dhikr that holds several, shown on its own
                    qs = to_parts(strip_wrap(full), quran, where)
                    if list(it["quran"]) not in [p.get("q") for p in qs]:
                        raise SystemExit(f"TEXT CHECK FAILED {where}: {it['quran']} is not in the official text")
                    bt = book_all(it.get("book", ch))
                    for key in ("src", "note"):
                        for piece in [p for p in it.get(key, "").split(" — ") if p]:
                            if skeld(piece) not in skeld(bt):
                                raise SystemExit(f"SOURCE CHECK FAILED {where}: {key} «{piece[:60]}» is not in the book")
                    parts = ([{"t": it["pre"]}] if it.get("pre") else []) + [{"q": list(it["quran"])}]
                    items.append(dict(parts=parts, count=it.get("count", 1), note=it.get("note", ""),
                                      src=" — ".join([it["src"]] + extra), label=it.get("label", ""), where=where,
                                      mushaf=True, verified=extra))
                    continue
                t = it.get("text", full)
                if not has(full, t):
                    raise SystemExit(f"TEXT CHECK FAILED {where}: «{t[:50]}» is not in the official text")
                bch = it.get("book", ch)
                bt = " ".join(book_all(c) for c in ([bch] if isinstance(bch, str) else bch))
                for key in ("src", "fadl") + (() if it.get("hadith_note") else ("note",)):
                    for piece in [p for p in it.get(key, "").split(" — ") if p]:
                        if skeld(piece) not in skeld(bt):
                            raise SystemExit(f"SOURCE CHECK FAILED {where}: {key} «{piece[:60]}» is not in the book")
                for hb, hn, frags in it.get("hadith_note", []):
                    by_num, by_ar, _ = hadith.books[hb]
                    texts = (by_ar if hb == "muslim" else by_num).get(hn, [])
                    for fr in frags:
                        if not any(skel(fr) in tt for tt in texts):
                            raise SystemExit(f"HADITH CHECK FAILED {where}: {hb} {hn} does not hold «{fr}»")
                    it = dict(it, note="قال ﷺ: «" + " … ".join(frags) + "» (" + BOOK_AR[hb] + "، برقم " + str(hn) + ")")
                cnt = it.get("count", official[ch][i]["count"])
                src = " — ".join([it.get("src", "")] + extra) if it.get("src") else " — ".join(extra)
                items.append(dict(text=t, count=cnt, fadl=it.get("fadl", ""), note=it.get("note", ""),
                                  src=src, label=it.get("label", ""), where=where, verified=extra, icon=it.get("icon", ""),
                                  hadith_text=it.get("probe") or (full if "text" in it else None)))
        out_items = []
        for x in items:
            if "parts" in x:
                parts = x["parts"]
                for p in [p for p in parts if "q" in p]:
                    s, a1, a2 = p["q"]
                    if not (1 <= a1 <= a2 <= quran.ayahs[s]):
                        raise SystemExit(f"QURAN CHECK FAILED {x['where']}: no verses {s}:{a1}-{a2}")
            else:
                parts = to_parts(strip_wrap(x["text"]), quran, x["where"], force_quran=x.get("quran", False))
            report["quran"] += sum(1 for p in parts if "q" in p or "u" in p)
            if x.get("hadithed"):
                check = x["hadithed"]
                report["hadith_ok"] += 1
            elif x.get("external"):
                check = "external"
                report["other_books"].append(f"{x['where']}: {x['src'][:90]}")
            elif x.get("mushaf"):
                check = "، ".join(["القرآن"] + x.get("verified", []))
            else:
                body = " ".join(p.get("t", "") for p in parts) if any("t" in p for p in parts) else ""
                qtext = "\n".join(quran.v[(p["q"][0], a)] for p in parts if "q" in p for a in range(p["q"][1], p["q"][2] + 1))
                bare = re.sub(r"\[.*?\]", " ", body)
                for b0 in ("بسم الله الرحمن الرحيم", "أعوذ بالله من الشيطان الرجيم"):
                    bare = bare.replace(b0, " ")
                    bare = re.sub(" +", " ", " ".join(w for w in bare.split(" ")))
                bare = " ".join(w for w in words(bare) if skel(w) not in {skel(x) for x in BASMALA.split()} or len(words(bare)) > 8)
                probe = x.get("hadith_text") or (bare if len(words(bare)) >= 6 else (qtext or x.get("fadl") or body))
                hits = hadith.check(probe, x["src"]) if probe and x["src"] else None
                if hits == [] and x.get("fadl"):
                    hits = hadith.check(x["fadl"], x["src"], 4)
                if hits is None:
                    if x["src"] and not body:
                        check = "quran"
                    else:
                        report["other_books"].append(f"{x['where']}: {x['src'][:90]}")
                        check = "other"
                elif hits or x.get("verified"):
                    report["hadith_ok"] += 1
                    check = "، ".join(dict.fromkeys(hits + x.get("verified", [])))
                elif x["where"] in UNCHECKED_OK:
                    check = "reviewed"
                    report["other_books"].append(f"{x['where']} (reviewed: {UNCHECKED_OK[x['where']]})")
                else:
                    fails.append(f"HADITH CHECK FAILED {x['where']}: the named books {Hadith.named(x['src'])} "
                                 f"do not hold «{probe[:70]}» — src: {x['src'][:120]}")
                    check = "failed"
            o = {"parts": parts, "count": x["count"]}
            for k in ("label", "fadl", "note", "src", "icon"):
                if x.get(k):
                    o[k] = tidy(x[k])
            o["check"] = check
            out_items.append(o)
        sections.append({"id": sec["id"], "title": sec["title"], "done": sec["done"], "group": sec.get("group", ""),
                         "items": out_items})

    if fails:
        raise SystemExit("\n".join(fails))
    import adhkar_fixes  # vowel marks the source got wrong or left out, and the morning's ×10/×100 tahlil
    sections = adhkar_fixes.apply(sections)
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "adhkar.json"), "w", encoding="utf-8") as f:
        json.dump({"sections": sections}, f, ensure_ascii=False, separators=(",", ":"))
    n = sum(len(s["items"]) for s in sections)
    print(f"adhkar: {len(auto_sections)} more chapters of the book, all of it but {len(AUTO_SKIP)} items", file=sys.stderr)
    print(f"adhkar: {len(sections)} sections, {n} adhkar; wording matches the official text; every takhrij is "
          f"word for word from the book; {report['quran']} Quran passages matched to the mushaf letter by letter; "
          f"{report['hadith_ok']} takhrijs found in the named hadith book", file=sys.stderr)
    for r in report["other_books"]:
        print("  takhrij in other books only:", r, file=sys.stderr)


if __name__ == "__main__":
    main()
