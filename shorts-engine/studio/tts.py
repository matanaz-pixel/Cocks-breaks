"""Text to speech with word timings.

EdgeVoice uses Microsoft's free neural voices (needs internet; works on GitHub Actions).
LocalVoice uses RHVoice offline (robotic, for development or when Edge is unreachable).
Both return (wav_path, duration_seconds, words) where words is [(word, start, end), ...].
"""
import asyncio
import re
import shutil
import subprocess
from pathlib import Path

EDGE_VOICE = "en-US-AndrewMultilingualNeural"
EDGE_RATE = "+8%"


def audio_duration(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    )
    return float(out.stdout.strip())


def to_wav(src, dst):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-ar", "44100", "-ac", "1", str(dst)], check=True)


class EdgeVoice:
    name = "edge"

    def __init__(self, voice=EDGE_VOICE, rate=EDGE_RATE, proxy=None):
        self.voice, self.rate, self.proxy = voice, rate, proxy

    def speak(self, text, out_wav):
        return asyncio.run(self._speak(text, Path(out_wav)))

    async def _speak(self, text, out_wav):
        import edge_tts

        comm = edge_tts.Communicate(text, self.voice, rate=self.rate, boundary="WordBoundary", proxy=self.proxy)
        mp3 = out_wav.with_suffix(".mp3")
        words = []
        with open(mp3, "wb") as f:
            async for chunk in comm.stream():
                if chunk["type"] == "audio":
                    f.write(chunk["data"])
                elif chunk["type"] == "WordBoundary":
                    start = chunk["offset"] / 1e7
                    words.append((chunk["text"], start, start + chunk["duration"] / 1e7))
        to_wav(mp3, out_wav)
        mp3.unlink()
        return out_wav, audio_duration(out_wav), words


class LocalVoice:
    name = "local"

    def __init__(self, voice="bdl"):
        self.voice = voice

    def speak(self, text, out_wav):
        out_wav = Path(out_wav)
        raw = out_wav.with_suffix(".raw.wav")
        subprocess.run(["RHVoice-test", "-p", self.voice, "-o", str(raw)], input=text, text=True, check=True,
                       capture_output=True)
        to_wav(raw, out_wav)
        raw.unlink()
        duration = audio_duration(out_wav)
        return out_wav, duration, estimate_word_times(text, duration)


def estimate_word_times(text, duration, lead=0.08, tail=0.15):
    """Spread words across the clip by length; punctuation adds a short pause."""
    tokens = text.split()
    weights = [len(re.sub(r"\W", "", t)) + 2 + (3 if re.search(r"[.,!?;:]$", t) else 0) for t in tokens]
    span = max(duration - lead - tail, 0.1)
    unit = span / sum(weights)
    t, words = lead, []
    for tok, w in zip(tokens, weights):
        words.append((tok, t, t + unit * w))
        t += unit * w
    return words


def pick_voice(prefer="edge"):
    """Edge when reachable, otherwise the offline voice."""
    if prefer == "edge":
        try:
            import edge_tts  # noqa: F401

            probe = EdgeVoice()
            tmp = Path("/tmp/_voice_probe.wav")
            probe.speak("Test.", tmp)
            tmp.unlink(missing_ok=True)
            return probe
        except Exception as exc:  # network blocked, package missing
            print(f"Edge voice unavailable ({type(exc).__name__}); using offline voice")
    if shutil.which("RHVoice-test"):
        return LocalVoice()
    raise RuntimeError("No text-to-speech engine available: pip install edge-tts, or apt install rhvoice rhvoice-english")
