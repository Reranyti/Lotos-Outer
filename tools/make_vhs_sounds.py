"""Makes the sounds of the endings menu out of nothing but maths: a VHS cassette sliding into a deck (and the deck taking it),
and an old TV switching on. Nothing is sampled from anywhere.

Run: python tools/make_vhs_sounds.py   (needs numpy, scipy and ffmpeg on the PATH)
Writes src/main/resources/assets/lotusblight/sounds/{vhs_insert,tv_on}.ogg
"""
import os
import subprocess
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'lotusblight', 'sounds')
rng = np.random.default_rng(1996)


def t(seconds):
    return np.arange(int(seconds * SR)) / SR


def noise(seconds):
    return rng.standard_normal(int(seconds * SR))


def band(x, lo, hi):
    b, a = butter(2, [lo / (SR / 2), hi / (SR / 2)], btype='band')
    return lfilter(b, a, x)


def lowpass(x, hz):
    b, a = butter(2, hz / (SR / 2))
    return lfilter(b, a, x)


def place(track, sound, at):
    i = int(at * SR)
    n = min(len(sound), len(track) - i)
    if n > 0:
        track[i:i + n] += sound[:n]


def thump(freq=70, length=0.25, drop=0.5):
    x = t(length)
    f = freq * (1 - drop * x / length)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-x * 22)


def click(length=0.03, hz=3200):
    x = noise(length)
    return band(x, hz * 0.6, hz * 1.4) * np.exp(-t(length) * 160) * 1.6


def hiss(seconds, level):
    return band(noise(seconds), 2500, 9000) * level


def insert():
    total = 3.2
    s = np.zeros(int(total * SR))
    # the cassette sliding in along the plastic: filtered noise that sweeps down in pitch
    slide = noise(0.55)
    sweep = np.linspace(2400, 700, len(slide))
    out = np.zeros_like(slide)
    for i in range(0, len(slide), 512):
        hz = sweep[i]
        out[i:i + 512] = band(slide[i:i + 512], hz * 0.7, hz * 1.3) if len(slide[i:i + 512]) > 32 else 0
    out *= np.hanning(len(out)) * 0.9
    place(s, out, 0.05)
    # it hits the back of the deck
    place(s, thump(65, 0.3) * 1.1, 0.62)
    place(s, click(), 0.62)
    # the mechanism takes it: a motor rising and a ratchet of little clicks
    m = t(1.0)
    freq = 150 + 130 * (m / 1.0) ** 0.8
    motor = np.sin(2 * np.pi * np.cumsum(freq) / SR) * 0.18 + band(noise(1.0), 200, 900) * 0.10
    motor *= np.minimum(1, m * 8) * np.minimum(1, (1.0 - m) * 6)
    place(s, motor, 0.85)
    for k in range(14):
        place(s, click(0.02, 2400 + 80 * k) * 0.5, 0.9 + k * 0.065)
    # the final clunk when the tape is seated
    place(s, thump(95, 0.22) * 0.9, 1.95)
    place(s, click(0.04, 2000), 1.95)
    # the head spinning up and the tape's hiss
    spin = band(noise(0.8), 300, 1400) * np.linspace(0, 0.16, int(0.8 * SR))
    place(s, spin, 2.0)
    h = hiss(total - 2.1, 0.05)
    h *= np.linspace(0, 1, len(h)) * np.linspace(1, 0.4, len(h))
    place(s, h, 2.1)
    return s


def tv_on():
    total = 1.8
    s = np.zeros(int(total * SR))
    place(s, thump(55, 0.45, 0.3) * 1.2, 0.0)                                   # the degauss thump
    place(s, band(noise(0.35), 100, 900) * np.exp(-t(0.35) * 9) * 0.5, 0.0)
    w = t(1.6)
    whine = np.sin(2 * np.pi * 7800 * w) * 0.04 * np.minimum(1, w * 6) * np.exp(-w * 1.4)   # the tube's whine, kept gentle
    place(s, whine, 0.12)
    stat = band(noise(0.5), 400, 9000) * np.exp(-t(0.5) * 5) * 0.22             # a burst of snow that settles
    place(s, stat, 0.1)
    return s


def save(name, samples):
    samples = samples / (np.max(np.abs(samples)) + 1e-9) * 0.85
    wav = tempfile.mktemp(suffix='.wav')
    wavfile.write(wav, SR, (samples * 32767).astype(np.int16))
    os.makedirs(OUT, exist_ok=True)
    target = os.path.join(OUT, name + '.ogg')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', target], check=True)
    os.remove(wav)
    print('wrote', target)


if __name__ == '__main__':
    save('vhs_insert', insert())
    save('tv_on', tv_on())
