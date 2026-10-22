import os
import traceback
import wave

import sherpa_onnx

# Three female Persian voices (single-speaker VITS models) from the Hugging Face
# repository karim23657/persian-tts-vits. Each folder has model.onnx and tokens.txt.
# These models read the text through the espeak-ng pronunciation data.
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

ESPEAK_DATA = "espeak-ng-data"


def write_wav(path, samples, sample_rate):
    # Make the loudest point of the sentence reasonably loud.
    peak = max((abs(value) for value in samples), default=0.0)
    scale = 0.9 / peak if peak > 0 else 1.0
    frames = bytearray()
    for value in samples:
        value = max(-1.0, min(1.0, value * scale))
        frames += int(value * 32767).to_bytes(2, "little", signed=True)
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(bytes(frames))


def vits_config(model, tokens):
    try:
        return sherpa_onnx.OfflineTtsVitsModelConfig(
            model=model,
            lexicon="",
            tokens=tokens,
            data_dir=ESPEAK_DATA,
            dict_dir="",
            noise_scale=0.667,
            noise_scale_w=0.8,
            length_scale=1.0,
        )
    except TypeError:
        # Older versions only accept the values in this order.
        return sherpa_onnx.OfflineTtsVitsModelConfig(
            model, "", tokens, ESPEAK_DATA, "", 0.667, 0.8, 1.0
        )


def make_samples(folder):
    model = os.path.join("models", folder, "model.onnx")
    tokens = os.path.join("models", folder, "tokens.txt")
    model_config = sherpa_onnx.OfflineTtsModelConfig()
    model_config.vits = vits_config(model, tokens)
    config = sherpa_onnx.OfflineTtsConfig(model=model_config, max_num_sentences=2)
    tts = sherpa_onnx.OfflineTts(config)
    for name, text in SENTENCES:
        audio = tts.generate(text)
        path = os.path.join("samples", folder + "_" + name + ".wav")
        write_wav(path, audio.samples, audio.sample_rate)
        print("OK", path, audio.sample_rate, len(audio.samples))


def main():
    print("sherpa-onnx version:", getattr(sherpa_onnx, "__version__", "unknown"))
    print("espeak data folder exists:", os.path.isdir(ESPEAK_DATA))
    os.makedirs("samples", exist_ok=True)
    problems = 0
    for folder in VOICES:
        try:
            make_samples(folder)
        except Exception as error:
            problems += 1
            print("FAILED", folder, repr(error))
            traceback.print_exc()
    print("voices with problems:", problems)


main()
