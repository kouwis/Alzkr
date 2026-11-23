"""Builds the adhan sounds from scripts/audio_spec.json and publishes them on the release «audio-v1».

For each recording: the whole adhan and its opening takbirs (cut where the spec says, with a short
fade), loudness-matched and encoded as mono AAC. Bundled ones (inside the APK, so the adhan works
offline from the first day) are named "b-…"; the rest are downloaded by the app when chosen.
Writes out/audio/catalog.json, which the APK build also bundles."""
import json, os, re, subprocess, urllib.request

SPEC = json.load(open("scripts/audio_spec.json", encoding="utf-8"))
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"}
SRC = "src-audio"; OUT = "out/audio"
os.makedirs(SRC, exist_ok=True); os.makedirs(OUT, exist_ok=True)


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
        return r.headers.get("Content-Type", ""), r.read()


def download(n):
    path = f"{SRC}/{n}.mp3"
    if os.path.exists(path) and os.path.getsize(path) > 50_000:
        return path
    ctype, body = fetch(f"https://www.assabile.com/download/adhan/{n}")
    if "html" in ctype:
        m = re.search(rb'(https?://[^"\'\s]+\.mp3)', body)
        ctype, body = fetch(m.group(1).decode())
    open(path, "wb").write(body)
    return path


def duration(p):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())


def encode(src, dst, start=0.0, end=None):
    af = "loudnorm=I=-16:TP=-1.5:LRA=11"
    args = ["ffmpeg", "-y", "-v", "error", "-i", src, "-ss", f"{start:.2f}"]
    if end is not None:
        args += ["-t", f"{end - start:.2f}"]
        af = f"afade=t=out:st={max(0.0, end - start - 1.5):.2f}:d=1.5," + af
    # trim the silence at the start of the recording
    af = "silenceremove=start_periods=1:start_threshold=-45dB," + af
    args += ["-af", af, "-ac", "1", "-ar", "44100", "-c:a", "aac", "-b:a", "64k", "-movflags", "+faststart", dst]
    subprocess.run(args, check=True)


catalog = []
for e in SPEC:
    src = download(e["assabile"])
    prefix = "b-" if e.get("bundled") else ""
    full = f"{prefix}{e['id']}.m4a"
    encode(src, f"{OUT}/{full}")
    item = {"id": e["id"], "name": e["name"], "place": e.get("place", ""), "fajr": e.get("fajr", False),
            "full": full, "fullSize": os.path.getsize(f"{OUT}/{full}"), "bundled": bool(e.get("bundled")),
            "seconds": round(duration(f"{OUT}/{full}"))}
    if e.get("short_end"):
        short = f"{prefix}{e['id']}-s.m4a"
        encode(src, f"{OUT}/{short}", 0.0, float(e["short_end"]))
        item["short"] = short; item["shortSize"] = os.path.getsize(f"{OUT}/{short}")
    catalog.append(item)
    print(item, flush=True)
json.dump(catalog, open(f"{OUT}/catalog.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
