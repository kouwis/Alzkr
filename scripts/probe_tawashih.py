"""Lists archive.org recordings of Sayed al-Naqshbandi's and Nasr al-Din Tobar's tawashih / ibtihalat (names, lengths)."""
import json, urllib.request, urllib.parse
UA = {"User-Agent": "Mozilla/5.0"}
def get(u):
    return json.load(urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60))
qs = ["النقشبندي", "نقشبندي", "naqshabandi", "naqshbandi", "nakshabandy", "طوبار", "tobar", "toubar"]
seen = set()
for q in qs:
    u = "https://archive.org/advancedsearch.php?" + urllib.parse.urlencode({"q": f"({q}) AND mediatype:audio", "fl[]": ["identifier", "title"], "rows": 60, "output": "json"}, doseq=True)
    try: docs = get(u)["response"]["docs"]
    except Exception as e: print("ERR", q, e); continue
    for d in docs:
        i = d["identifier"]
        if i in seen: continue
        seen.add(i)
        try: files = get(f"https://archive.org/metadata/{i}/files")["result"]
        except Exception: continue
        mp3 = [f for f in files if f.get("name", "").lower().endswith(".mp3") and f.get("source") == "original"]
        if not mp3: continue
        print(f"\n## {i} | {d.get('title')}")
        for f in mp3[:80]:
            print(f"   {f['name']} | {f.get('length', '?')} | {f.get('title', '')}")
