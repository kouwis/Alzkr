"""Looking for Tobar's «إن ضل قلبي» on archive.org: by name, then inside his albums (what is sung, with times)."""
import json, os, re, subprocess, urllib.parse, urllib.request
import numpy as np
UA = {"User-Agent": "Mozilla/5.0"}
def get(u): return json.load(urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60))
hits = []
for q in ["ان ضل قلبي", "إن ضل قلبي", "ضل قلبي", "ان ضل قلبى", "\"ضل قلبى\"", "طوبار"]:
    u = "https://archive.org/advancedsearch.php?" + urllib.parse.urlencode({"q": f"({q}) AND mediatype:audio", "fl[]": ["identifier", "title"], "rows": 100, "output": "json"}, doseq=True)
    try: docs = get(u)["response"]["docs"]
    except Exception as e: print("ERR", q, e); continue
    for d in docs:
        try: files = get(f"https://archive.org/metadata/{d['identifier']}/files")["result"]
        except Exception: continue
        for f in files:
            n = f.get("name", "") + " " + str(f.get("title", ""))
            if f.get("name", "").lower().endswith(".mp3") and re.search(r"ضل\s*قلب", n):
                hits.append(f"{d['identifier']}/{f['name']}"); print("HIT", d["identifier"], "|", f["name"], "|", f.get("length"), flush=True)
from faster_whisper import WhisperModel
wm = WhisperModel("small", compute_type="int8")
# by name, else the long albums and collections (first 25 minutes of each)
albums = hits or [
 "tubar/tubar.mp3", "doaaelfajr/دعاء الفجر.mp3",
 "nasr_el_din_tobar_ya_ragaa_altaaebin/" , "nasr_el_din_tobar_ya_hannan_ya_mannan/mix_57m59s (audio-joiner.com).mp3",
 "nasr_el_din_tobar_ya_naseer_al_mazloom/mix_56m17s (audio-joiner.com).mp3", "nasr_el_din_tobar_amry_elaik/mix_44m15s (audio-joiner.com).mp3",
 "nasr_el_din_tobar_alhot_walankabout/الحوت والعكبوت.mp3", "nasr_el_din_tobar_beka_astajeer/بك أستجير.mp3",
 "nasr-el-din-tobar/40 دقيقة اجمل ابتهالات الشيخ نصر الدين طوبار مكتوبة و مصحوبة بمناظر طبيعية HD🌹 (192 kbps).mp3",
]
os.makedirs("tw", exist_ok=True)
for path in albums:
    if path.endswith("/"):
        try:
            fs = get(f"https://archive.org/metadata/{path.strip('/')}/files")["result"]
            path = path + next(f["name"] for f in fs if f["name"].lower().endswith(".mp3"))
        except Exception as e: print("ERR", path, e); continue
    f = "tw/a.mp3"
    try: urllib.request.urlretrieve("https://archive.org/download/" + urllib.parse.quote(path), f)
    except Exception as e: print("ERR", path, e, flush=True); continue
    pcm = np.frombuffer(subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-t", "1500", "-ac", "1", "-ar", "16000", "-f", "f32le", "-"], capture_output=True).stdout, np.float32)
    print(f"\n#### {path}  {len(pcm)/16000:.0f}s", flush=True)
    segs, _ = wm.transcribe(pcm, language="ar", vad_filter=False)
    for s in segs:
        flag = "  <<<" if re.search(r"ضل|قلبي|ظل", s.text) else ""
        print(f"   {s.start:7.1f}-{s.end:7.1f}  {s.text.strip()}{flag}", flush=True)
