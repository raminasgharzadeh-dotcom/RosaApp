import os
import wave

import sherpa_onnx

# Three female Persian voices (single-speaker VITS models) from the Hugging Face
# repository karim23657/persian-tts-vits. Each folder has model.onnx and tokens.txt.
VOICES = [
    "persian-tts-female1-vits-coqui",
    "female-female-coqui-vits",
    "female-GPTInformal-coqui-vits",
]

SENTENCES = [
    ("greeting", "روزت بخیر، چه کمکی می‌تونم بکنم؟"),
    ("alarm", "آلارم ساعت هفت صبح را تنظیم کردم."),
    ("weather", "امروز هوای رشت ابری است و دمای هوا هجده درجه است."),
]


def write_wav(path, samples, sample_rate):
    frames = bytearray()
    for value in samples:
        value = max(-1.0, min(1.0, value))
        frames += int(value * 32767).to_bytes(2, "little", signed=True)
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(bytes(frames))


def make_samples(folder):
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=os.path.join("models", folder, "model.onnx"),
                tokens=os.path.join("models", folder, "tokens.txt"),
                lexicon="",
                data_dir="",
                dict_dir="",
            ),
            provider="cpu",
            debug=False,
            num_threads=2,
        ),
        max_num_sentences=1,
    )
    tts = sherpa_onnx.OfflineTts(config)
    for name, text in SENTENCES:
        audio = tts.generate(text, sid=0, speed=1.0)
        path = os.path.join("samples", folder + "_" + name + ".wav")
        write_wav(path, audio.samples, audio.sample_rate)
        print("OK", path, audio.sample_rate, len(audio.samples))


def main():
    os.makedirs("samples", exist_ok=True)
    problems = 0
    for folder in VOICES:
        try:
            make_samples(folder)
        except Exception as error:
            problems += 1
            print("FAILED", folder, repr(error))
    print("voices with problems:", problems)


main()
