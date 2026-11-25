"""Tawashih candidates: is there music under the voice (YAMNet's instrument classes, with known controls),
what is sung in the first minutes (with times), and the loudness per second (to cut at a line's end)."""
import csv, io, os, re, subprocess, urllib.parse, urllib.request
import numpy as np
A = "https://archive.org/download/"
C = {
 "CTRL_music_mawlay": "naqshabandi_mawlay/mix_42m34s (audio-joiner.com).mp3",
 "CTRL_music_hobak": "naqshabandi_fi_hobak_rabbi/mix_40m34s (audio-joiner.com).mp3",
 "n_aghib": "20230816_20230816_1516/سيد نقشبندي - ابتهالات مصري/أغيب وذو اللطائف لا يغيب.mp3",
 "n_sayyidi": "nsr20100_yahoo_201401/سيد نقشبندي . سيدي يا رسول الله.mp3",
 "n_004": "sayyid-al-naqshbandi/004.mp3",
 "n_adaook": "naqshabandi_adaook/mix_41m37s (audio-joiner.com).mp3",
 "n_eido": "Sheikh-Sayed-Al-Naqshbandi-Ibtihalat/الشيخ سيد النقشبندى    عيدوا عليا الوصال _ ابتهال نادر من سوريا 1958 م.mp3",
 "t_ilah": "YaElahAlalminEbthalNasrEldinTobar/YaElahAlalmin.mp3",
 "t_yaman": "Nasr_aldin_Toubar/Nasr_aldin_Toubar.mp3",
 "t_sobhanak": "nasr_el_din_tobar_sobhanak_ya_ghafer_alzunub/mix_55m14s (audio-joiner.com).mp3",
 "t_tuyur": "nasr-el-din-tobar_202209/سبحت لله في العش الطيور للشيخ نصر الدين طوبار جودة عالية (192 kbps).mp3",
 "t_monadi": "20211026_20211026_1058/نصر الدين طوبار - جل المنادي.mp3",
 "t_malik": "20211026_20211026_1058/نصر الدين طوبار - يا مالك الملك.mp3",
 "t_mujib": "20211026_20211026_1058/نصر الدين طوبار - مجيب السائلين.mp3",
 "t_subh": "20211026_20211026_1058/نصر الدين طوبار - حين يهدي الصبح.mp3",
 "t_wujud": "20211026_20211026_1058/نصر الدين طوبار  - هوه الله رب الوجود.mp3",
 "t_ghafir": "20211026_20211026_1058/نصر الدين طوبار __ يا غافر الذنب العظيم.mp3",
 "t_salikin": "20211026_20211026_1058/نصر الدين طوبار - ياسالكين الدرب.mp3",
 "t_yaman2": "20201209_20201209_0854/سبحانك يا من-نصر الدين طوبار.mp3",
 "t_munqidhi": "20201209_20201209_0854/يا منقذي في شدتي-نصر الدين طوبار.mp3",
 "t_khushui": "20201209_20201209_0854/الشيخ طوبار اليك خشوعي.mp3",
 "t_fada": "20201209_20201209_0854/يسبح لك الفظاء.mp3",
 "t_wadud": "20201209_20201209_0854/يا ودودا-نصر الدين طوبار.mp3",
 "t_dhatak": "nasr-el-din-tobar/إبتهال سبحانك ذاتك ـ من روائع العملاق الشيخ نصر الدين طوبار خاشع جدا (192 kbps).mp3",
}
import tensorflow_hub as hub, tensorflow as tf
yam = hub.load("https://tfhub.dev/google/yamnet/1")
names = [r["display_name"] for r in csv.DictReader(io.StringIO(tf.io.read_file(yam.class_map_path()).numpy().decode()))]
INST = [i for i, n in enumerate(names) if re.search(r"instrument|Violin|String|Orchestra|Guitar|Drum|Percussion|Flute|Piano|Keyboard|Zither|Accordion|Synthesizer|Tabla|Plucked|Bowed|Brass|Harp|Cello|Mandolin|Sitar|Organ|Saxophone|Clarinet|Oboe|Trumpet|Cymbal|Tambourine", n)]
from faster_whisper import WhisperModel
wm = WhisperModel("small", compute_type="int8")
os.makedirs("tw", exist_ok=True)
for k, path in C.items():
    f = f"tw/{k}.mp3"
    try: urllib.request.urlretrieve(A + urllib.parse.quote(path), f)
    except Exception as e: print("ERR", k, e, flush=True); continue
    pcm = np.frombuffer(subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-t", "300", "-ac", "1", "-ar", "16000", "-f", "f32le", "-"], capture_output=True).stdout, np.float32)
    scores = yam(pcm)[0].numpy()  # frames × 521
    inst = scores[:, INST].max(axis=1)
    top = np.argsort(-scores.mean(axis=0))[:6]
    print(f"\n#### {k}  dur={len(pcm)/16000:.0f}s  instruments: mean={inst.mean():.3f} frames>0.2={np.mean(inst > 0.2):.2f} frames>0.4={np.mean(inst > 0.4):.2f}  top: " +
          ", ".join(f"{names[i]} {scores[:, i].mean():.2f}" for i in top), flush=True)
    secs = len(pcm) // 16000
    rms = [20 * np.log10(np.sqrt(np.mean(pcm[i*16000:(i+1)*16000] ** 2)) + 1e-9) for i in range(secs)]
    print("   rms: " + " ".join(f"{i}:{int(v)}" for i, v in enumerate(rms)), flush=True)
    segs, _ = wm.transcribe(pcm, language="ar", vad_filter=False)
    for s in segs: print(f"   {s.start:6.1f}-{s.end:6.1f}  {s.text.strip()}", flush=True)
