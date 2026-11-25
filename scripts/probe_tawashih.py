"""What is said in an uploaded adhan, with times and the loudness per second (to find the takbirs' end and any «الصلاة خير من النوم»)."""
import subprocess, numpy as np
from faster_whisper import WhisperModel
f = "scripts/audio-src/gazy.mp3"
pcm = np.frombuffer(subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"], capture_output=True).stdout, np.float32)
secs = len(pcm) // 16000
print("rms: " + " ".join(f"{i}:{int(20*np.log10(np.sqrt(np.mean(pcm[i*16000:(i+1)*16000]**2))+1e-9))}" for i in range(secs)))
for s in WhisperModel("small", compute_type="int8").transcribe(pcm, language="ar")[0]:
    print(f"   {s.start:6.1f}-{s.end:6.1f}  {s.text.strip()}", flush=True)
