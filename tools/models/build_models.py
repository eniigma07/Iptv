"""
Buduje pakiet modeli offline dla aplikacji Artykulacja i sprawdza, czy działają.

Wynik (katalog out/):
  phoneme.onnx        - wav2vec2 (L2-ARCTIC) rozpoznawanie głosek, int8
  phoneme_vocab.json  - słownik tokenów modelu głosek
  kokoro.onnx         - Kokoro-82M (głos wzorcowy, fp32)
  voices.bin          - wszystkie amerykańskie głosy Kokoro sklejone (float32)
  voices.json         - indeks głosów w voices.bin
  manifest.json       - lista plików z rozmiarami i sha256
"""
import hashlib
import json
import os
import shutil
import sys

import numpy as np

OUT = "out"
PHONEME_REPO = "mrrubino/wav2vec2-large-xlsr-53-l2-arctic-phoneme"
KOKORO_REPO = "onnx-community/Kokoro-82M-v1.0-ONNX"
VOICES = [
    "af_heart", "af_bella", "af_nicole", "af_aoede", "af_kore", "af_sarah",
    "af_nova", "af_sky", "af_alloy", "af_jessica", "af_river",
    "am_michael", "am_fenrir", "am_puck", "am_echo", "am_eric", "am_liam",
    "am_onyx", "am_adam",
]

os.makedirs(OUT, exist_ok=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------- głoski
def build_phoneme():
    import torch
    from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor
    from huggingface_hub import hf_hub_download
    from onnxruntime.quantization import quantize_dynamic, QuantType

    vocab_path = hf_hub_download(PHONEME_REPO, "vocab.json")
    shutil.copy(vocab_path, f"{OUT}/phoneme_vocab.json")
    print("VOCAB:", open(vocab_path).read())
    prep = hf_hub_download(PHONEME_REPO, "preprocessor_config.json")
    print("PREPROCESSOR:", open(prep).read())

    model = Wav2Vec2ForCTC.from_pretrained(PHONEME_REPO).eval()
    dummy = torch.randn(1, 16000)
    torch.onnx.export(
        model, (dummy,), "phoneme_fp32.onnx",
        input_names=["input_values"], output_names=["logits"],
        dynamic_axes={"input_values": {1: "samples"}, "logits": {1: "frames"}},
        opset_version=17, do_constant_folding=True, dynamo=False,
    )
    quantize_dynamic(
        "phoneme_fp32.onnx", f"{OUT}/phoneme.onnx",
        weight_type=QuantType.QUInt8, op_types_to_quantize=["MatMul"],
    )
    os.remove("phoneme_fp32.onnx")
    for f in os.listdir("."):
        if f.endswith(".onnx.data") or f.startswith("phoneme_fp32"):
            os.remove(f)


# ---------------------------------------------------------------- Kokoro
def build_kokoro():
    from huggingface_hub import hf_hub_download

    p = hf_hub_download(KOKORO_REPO, "onnx/model.onnx")
    shutil.copy(p, f"{OUT}/kokoro.onnx")
    index = []
    offset = 0
    with open(f"{OUT}/voices.bin", "wb") as out:
        for v in VOICES:
            vp = hf_hub_download(KOKORO_REPO, f"voices/{v}.bin")
            data = np.fromfile(vp, dtype=np.float32)
            assert data.size % 256 == 0, (v, data.size)
            rows = data.size // 256
            out.write(data.tobytes())
            index.append({"name": v, "offset": offset, "rows": rows})
            offset += data.nbytes
    json.dump(index, open(f"{OUT}/voices.json", "w"), indent=1)


# ---------------------------------------------------------------- test
def misaki_lexicon():
    import glob
    import zipfile
    import subprocess

    subprocess.check_call([sys.executable, "-m", "pip", "download", "--no-deps",
                           "misaki==0.7.4", "-d", "pipdl", "-q"])
    whl = glob.glob("pipdl/misaki-*.whl")[0]
    z = zipfile.ZipFile(whl)
    lex = {}
    for name in ["misaki/data/us_silver.json", "misaki/data/us_gold.json"]:
        lex.update(json.loads(z.read(name)))
    return lex


def sanity_test():
    import onnxruntime as ort
    from scipy.signal import resample_poly

    lex = misaki_lexicon()
    cfg_vocab = json.load(open("tools/models/kokoro_vocab.json"))

    def phonemize(text):
        out = []
        for w in text.lower().replace(".", " .").replace(",", " ,").split():
            if w in (".", ","):
                out.append(w)
                continue
            p = lex.get(w)
            if p is None:
                print("OOV:", w)
                continue
            if isinstance(p, dict):
                p = p.get("DEFAULT") or next(iter(p.values()))
            out.append(p)
        s = " ".join(out).replace(" .", ".").replace(" ,", ",")
        return s

    sess = ort.InferenceSession(f"{OUT}/kokoro.onnx")
    print("KOKORO INPUTS:", [(i.name, i.type, i.shape) for i in sess.get_inputs()])
    print("KOKORO OUTPUTS:", [(o.name, o.type, o.shape) for o in sess.get_outputs()])
    index = {v["name"]: v for v in json.load(open(f"{OUT}/voices.json"))}
    allv = np.fromfile(f"{OUT}/voices.bin", dtype=np.float32)

    psess = ort.InferenceSession(f"{OUT}/phoneme.onnx")
    print("PHONEME INPUTS:", [(i.name, i.type, i.shape) for i in psess.get_inputs()])
    vocab = json.load(open(f"{OUT}/phoneme_vocab.json"))
    inv = {i: t for t, i in vocab.items()}
    blank = vocab.get("<pad>", 0)

    for text in ["Think about the schedule.", "We need to align with the stakeholders.",
                 "Very well, this is the world.", "The ship and the sheep."]:
        ph = phonemize(text)
        ids = [cfg_vocab[c] for c in ph if c in cfg_vocab]
        v = index["af_heart"]
        rows = allv[v["offset"] // 4: v["offset"] // 4 + v["rows"] * 256].reshape(-1, 256)
        style = rows[min(len(ids), len(rows) - 1)][None, :]
        feeds = {}
        for i in sess.get_inputs():
            if i.name in ("input_ids", "tokens"):
                feeds[i.name] = np.array([[0, *ids, 0]], dtype=np.int64)
            elif i.name == "style":
                feeds[i.name] = style.astype(np.float32)
            elif i.name == "speed":
                feeds[i.name] = np.array([1.0], dtype=np.float32 if "float" in i.type else np.int32)
        audio = sess.run(None, feeds)[0].reshape(-1)
        a16 = resample_poly(audio, 2, 3).astype(np.float32)
        a16 = (a16 - a16.mean()) / (a16.std() + 1e-7)
        logits = psess.run(None, {"input_values": a16[None, :]})[0][0]
        best = logits.argmax(-1)
        seq = []
        prev = -1
        for b in best:
            if b != prev and b != blank:
                seq.append(inv.get(int(b), "?"))
            prev = b
        print(f"TEST | {text} | {ph} | sekundy={len(audio)/24000:.2f} | rozpoznane: {' '.join(seq)}")


if __name__ == "__main__":
    build_phoneme()
    build_kokoro()
    sanity_test()
    manifest = {"version": 1, "files": []}
    for f in sorted(os.listdir(OUT)):
        p = f"{OUT}/{f}"
        manifest["files"].append({"name": f, "size": os.path.getsize(p), "sha256": sha256(p)})
    json.dump(manifest, open(f"{OUT}/manifest.json", "w"), indent=1)
    print(json.dumps(manifest, indent=1))
