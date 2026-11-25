"""Nasr al-Din Tobar's ibtihalat on archive.org: every item and its mp3 files (names, lengths)."""
import json, urllib.request, urllib.parse
UA = {"User-Agent": "Mozilla/5.0"}
def get(u): return json.load(urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60))
qs = ["طوبار", "نصرالدين طوبار", "\"نصر الدين طوبار\"", "toubar", "tobar ebtehalat", "\"nasr eldin tobar\"", "\"nasr el din tobar\"", "\"nasr al din tobar\"",
      "\"nasreldin tobar\"", "tobar ibtihalat", "ابتهالات طوبار", "يسبح الطير", "قلبي انت تعرفه", "ابتهالات الاذاعة", "ابتهالات نادرة", "مبتهلين"]
seen = set()
for q in qs:
    u = "https://archive.org/advancedsearch.php?" + urllib.parse.urlencode({"q": f"({q}) AND mediatype:audio", "fl[]": ["identifier", "title"], "rows": 100, "output": "json"}, doseq=True)
    try: docs = get(u)["response"]["docs"]
    except Exception as e: print("ERR", q, e); continue
    for d in docs:
        i = d["identifier"]
        if i in seen: continue
        seen.add(i)
        try: files = get(f"https://archive.org/metadata/{i}/files")["result"]
        except Exception: continue
        mp3 = [f for f in files if f.get("name", "").lower().endswith(".mp3") and f.get("source") == "original"]
        hit = lambda s: any(k in s.lower() for k in ["طوبار", "tobar", "toubar", "توبار", "يسبح", "قلبي"])
        rows = [f for f in mp3 if hit(f["name"] + " " + str(f.get("title", "")))] or (mp3 if hit(str(d.get("title")) + i) else [])
        if not rows: continue
        print(f"\n## {i} | {d.get('title')}")
        for f in rows[:150]:
            print(f"   {f['name']} | {f.get('length', '?')} | {f.get('title', '')}")
