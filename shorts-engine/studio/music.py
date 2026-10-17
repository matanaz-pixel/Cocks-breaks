"""Original background bed generated from scratch, so there is no copyright claim risk.

A soft minor pad (Am F C G) with a quiet pulse. Different seeds give slightly different voicings.
"""
import numpy as np

SR = 44100
CHORDS = [  # semitones from A2
    [0, 3, 7, 12], [-4, 0, 3, 8], [3, 7, 10, 15], [-2, 2, 5, 10],
]


def _note(freq, n, rng):
    t = np.arange(n) / SR
    detune = 1 + rng.uniform(-0.003, 0.003)
    wave = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2 * detune * t)
    wave += 0.15 * np.sin(2 * np.pi * freq * 3 * t)
    return wave


def bed(seconds, seed=0, bpm=96):
    rng = np.random.default_rng(seed)
    total = int(seconds * SR)
    out = np.zeros(total)
    bar = int(SR * 60 / bpm * 4)
    a2 = 110.0
    i = 0
    for start in range(0, total, bar):
        n = min(bar, total - start)
        chord = CHORDS[(i + seed) % len(CHORDS)]
        seg = sum(_note(a2 * 2 ** (s / 12), n, rng) for s in chord) / len(chord)
        env = np.minimum(1, np.arange(n) / (0.35 * SR)) * np.minimum(1, (n - np.arange(n)) / (0.25 * SR))
        out[start:start + n] += seg * env
        i += 1
    # Quiet pulse on every beat: a short low thump.
    beat = int(SR * 60 / bpm)
    k = np.arange(int(0.18 * SR))
    thump = np.sin(2 * np.pi * 55 * k / SR) * np.exp(-k / (0.05 * SR))
    for start in range(0, total - len(k), beat):
        out[start:start + len(k)] += 0.6 * thump
    out /= np.max(np.abs(out)) + 1e-9
    fade = int(1.0 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out.astype(np.float32)
