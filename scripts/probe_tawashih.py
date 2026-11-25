"""Tawashih candidates: for each recording, what is sung in its first minutes (with times) and where its pauses fall."""
import os, re, subprocess, urllib.parse, urllib.request
C = {
 "n_mawlay": "naqshabandi_mawlay/mix_42m34s (audio-joiner.com).mp3",
 "n_omati": "naqshabandi_aqoul_omati/mix_32m39s (audio-joiner.com).mp3",
 "n_hamdan": "naqshabandi_hamdan_laka_allah/mix_39m19s (audio-joiner.com).mp3",
 "n_hobak": "naqshabandi_fi_hobak_rabbi/mix_40m34s (audio-joiner.com).mp3",
 "n_adaook": "naqshabandi_adaook/mix_41m37s (audio-joiner.com).mp3",
 "n_short": "Sayyed_Naqshabandi/Sayyed_Naqshabandi.mp3",
 "n_sayyidi": "nsr20100_yahoo_201401/سيد نقشبندي . سيدي يا رسول الله.mp3",
 "n_aghib": "20230816_20230816_1516/سيد نقشبندي - ابتهالات مصري/أغيب وذو اللطائف لا يغيب.mp3",
 "t_ilah": "YaElahAlalminEbthalNasrEldinTobar/YaElahAlalmin.mp3",
 "t_yaman": "Nasr_aldin_Toubar/Nasr_aldin_Toubar.mp3",
 "t_sobhanak": "nasr_el_din_tobar_sobhanak_ya_ghafer_alzunub/mix_55m14s (audio-joiner.com).mp3",
}
from faster_whisper import WhisperModel
m = WhisperModel("small", compute_type="int8")
os.makedirs("tw", exist_ok=True)
for k, path in C.items():
    url = "https://archive.org/download/" + urllib.parse.quote(path)
    f = f"tw/{k}.mp3"
    try:
        urllib.request.urlretrieve(url, f)
    except Exception as e:
        print("ERR", k, e, flush=True); continue
    clip = f"tw/{k}.wav"
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", f, "-t", "300", "-ac", "1", "-ar", "16000", clip], check=True)
    sd = subprocess.run(["ffmpeg", "-i", clip, "-af", "silencedetect=n=-32dB:d=0.7", "-f", "null", "-"], capture_output=True, text=True).stderr
    pauses = [(float(a), float(b)) for a, b in zip(re.findall(r"silence_start: ([\d.]+)", sd), re.findall(r"silence_end: ([\d.]+)", sd))]
    print(f"\n#### {k}  pauses: " + "  ".join(f"{a:.1f}-{b:.1f}" for a, b in pauses), flush=True)
    import numpy as np
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", clip, "-f", "f32le", "-"], capture_output=True).stdout
    # where the sound dips (music between lines counts as sound; these are its quietest moments)
    lv = subprocess.run(["ffmpeg", "-i", clip, "-af", "astats=metadata=1:reset=16000,ametadata=print:key=lavfi.astats.Overall.RMS_level", "-f", "null", "-"], capture_output=True, text=True).stderr
    rms = [float(x) if x not in ("-inf",) else -99 for x in re.findall(r"RMS_level=(-?[\d.]+|-inf)", lv)]
    print("   rms/s: " + " ".join(f"{i}:{int(v)}" for i, v in enumerate(rms) if i >= 120 and i <= 240), flush=True)
    segs, _ = m.transcribe(np.frombuffer(pcm, np.float32), language="ar", vad_filter=False)
    for s in segs:
        print(f"   {s.start:6.1f}-{s.end:6.1f}  {s.text.strip()}", flush=True)
