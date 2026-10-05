"""Finds the beat, the kicks and the loud and quiet parts of a track, so a scene can be cut to it.

Run: python tools/analyze_music.py <track.ogg> <out.json>
Writes {"duration", "bpm", "beat0", "kicks": [seconds...], "energy": [per second 0..1], "drops": [seconds...]}.
Needs numpy, scipy and ffmpeg.
"""
import json
import subprocess
import sys
import tempfile
import os

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter, find_peaks

src, out = sys.argv[1], sys.argv[2]
wav = tempfile.mktemp(suffix='.wav')
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ac', '1', '-ar', '22050', wav], check=True)
sr, x = wavfile.read(wav)
os.remove(wav)
x = x.astype(np.float64) / 32768
dur = len(x) / sr

# onset strength: spectral flux in 1024-sample frames, hop 256
hop, n = 256, 1024
win = np.hanning(n)
frames = (len(x) - n) // hop
prev = None
flux = np.zeros(frames)
low = np.zeros(frames)
for i in range(frames):
    seg = x[i * hop:i * hop + n] * win
    mag = np.abs(np.fft.rfft(seg))
    if prev is not None:
        flux[i] = np.sum(np.maximum(0, mag - prev))
    low[i] = np.sum(mag[1:12])      # below about 250 Hz: kicks
    prev = mag
t = (np.arange(frames) * hop + n / 2) / sr

# kicks: peaks of the low band's rise
rise = np.maximum(0, np.diff(low, prepend=low[0]))
rise = rise / (rise.max() + 1e-9)
peaks, _ = find_peaks(rise, height=0.22, distance=int(0.09 * sr / hop))
kicks = [round(float(t[p]), 3) for p in peaks]

# tempo: autocorrelation of the onset curve between 70 and 220 BPM
f = flux - flux.mean()
ac = np.correlate(f, f, mode='full')[len(f) - 1:]
lo, hi = int(60 / 220 * sr / hop), int(60 / 70 * sr / hop)
lag = lo + int(np.argmax(ac[lo:hi]))
bpm = 60 * sr / hop / lag
# fold into 80..180 (breakcore is fast, but one beat is what a cut lands on)
while bpm > 180:
    bpm /= 2
while bpm < 80:
    bpm *= 2

# energy per second
energy = []
for s in range(int(dur) + 1):
    seg = x[int(s * sr):int((s + 1) * sr)]
    energy.append(float(np.sqrt(np.mean(seg ** 2))) if len(seg) else 0.0)
m = max(energy) or 1
energy = [round(e / m, 3) for e in energy]

# drops: where the loudness jumps up a lot against the seconds before it
drops = []
for s in range(4, len(energy)):
    before = np.mean(energy[s - 4:s])
    if energy[s] - before > 0.28 and (not drops or s - drops[-1] > 6):
        drops.append(s)

# the beat grid starts at the first strong kick
beat0 = kicks[0] if kicks else 0.0
json.dump({'duration': round(dur, 3), 'bpm': round(bpm, 2), 'beat0': beat0, 'kicks': kicks, 'energy': energy, 'drops': drops},
          open(out, 'w'))
print('duration', round(dur, 2), 'bpm', round(bpm, 1), 'kicks', len(kicks), 'drops', drops)
print('energy per 4 s:', ' '.join(f'{np.mean(energy[i:i + 4]):.2f}' for i in range(0, len(energy), 4)))
