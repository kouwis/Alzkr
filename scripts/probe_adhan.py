"""One-off probe: downloads candidate adhan recordings and transcribes them, to find which ones are
Fajr adhans (with «الصلاة خير من النوم») and where the opening takbirs end (for the short version).
Writes probe/report.json and prints a summary. Run by .github/workflows/audio.yml."""
import json, os, re, subprocess, sys, urllib.request

IDS = [54, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 230, 231, 257, 256, 150, 151,
       130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 95, 149, 148, 145]
BASE = "https://www.assabile.com/download/adhan/{}"
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"}
os.makedirs("probe", exist_ok=True)


def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.headers.get("Content-Type", ""), r.geturl(), r.read()


def download(i):
    path = f"probe/{i}.mp3"
    if os.path.exists(path) and os.path.getsize(path) > 50_000:
        return path
    ctype, final, body = fetch(BASE.format(i))
    if "html" in ctype:
        m = re.search(rb'(https?://[^"\'\s]+\.mp3)', body)
        if not m:
            print("NO MP3 LINK", i, final, body[:300]); return None
        ctype, final, body = fetch(m.group(1).decode())
    open(path, "wb").write(body)
    return path


def norm(s):
    s = re.sub(r"[ً-ْٰـ]", "", s)
    return s.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي")


from faster_whisper import WhisperModel
model = WhisperModel(os.environ.get("WHISPER", "small"), device="cpu", compute_type="int8")
report = {}
for i in IDS:
    try:
        p = download(i)
    except Exception as e:
        print("FAIL", i, e); continue
    if not p: continue
    dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                               capture_output=True, text=True).stdout.strip() or 0)
    segs, _ = model.transcribe(p, language="ar", beam_size=1, word_timestamps=True, vad_filter=False)
    segs = list(segs)
    words = [(w.start, w.end, w.word) for s in segs for w in (s.words or [])]
    text = " ".join(s.text.strip() for s in segs)
    n = norm(text)
    fajr = ("خير من النوم" in n) or ("خيرمن النوم" in n) or ("النوم" in n and "خير" in n)
    # where «أشهد» first starts: the opening takbirs end before it
    shahada = next((w[0] for w in words if "اشهد" in norm(w[2]) or "شهد" in norm(w[2])), None)
    report[i] = {"duration": round(dur, 1), "fajr": fajr, "first_shahada": shahada, "size": os.path.getsize(p),
                 "segments": [(round(s.start, 1), round(s.end, 1), s.text.strip()) for s in segs]}
    print(f"== {i}: {dur:.0f}s fajr={fajr} shahada@{shahada}\n   {text[:400]}", flush=True)
json.dump(report, open("probe/report.json", "w"), ensure_ascii=False, indent=1)
