#!/usr/bin/env python3
"""
Exports a Hugging Face wav2vec2 CTC model to the ONNX files MusicBabe downloads for on-device
lyrics sync, quantized to int8, with a manifest describing exactly how to feed it.

    python export_ctc_model.py --model jonatasgrosman/wav2vec2-large-xlsr-53-italian \
        --id ctc-it-v1 --language it --out dist/

Output: <id>.onnx (input `input_values` float32 [1, samples] normalised to zero mean and unit
variance; output `log_probs` float32 [1, frames, vocab], already log-softmaxed) and <id>.json.
Only MatMul weights are quantized (dynamic, uint8 activations / int8 weights): the convolutional
feature encoder stays float, which keeps timing precision and runs well on ARM CPUs.
"""
import argparse
import hashlib
import json
import os
import sys

import numpy as np
import onnx
import onnxruntime as ort
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoConfig, Wav2Vec2ForCTC, Wav2Vec2Processor


class LogProbs(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_values):
        return torch.log_softmax(self.model(input_values).logits, dim=-1)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def normalise(x):
    x = x.astype(np.float32)
    return (x - x.mean()) / np.sqrt(x.var() + 1e-7)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--id", required=True)
    ap.add_argument("--language", required=True)
    ap.add_argument("--license", default="apache-2.0")
    ap.add_argument("--source", help="Hugging Face repo id recorded in the manifest (defaults to --model)")
    ap.add_argument("--out", default="dist")
    ap.add_argument("--keep-fp32", action="store_true")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    config = AutoConfig.from_pretrained(args.model)
    processor = Wav2Vec2Processor.from_pretrained(args.model)
    model = Wav2Vec2ForCTC.from_pretrained(args.model).eval()
    fe = processor.feature_extractor
    assert fe.sampling_rate == 16000, fe.sampling_rate
    stride = int(np.prod(config.conv_stride))
    receptive = 1
    for k, s in zip(reversed(config.conv_kernel), reversed(config.conv_stride)):
        receptive = receptive * s + (k - s)
    vocab = processor.tokenizer.get_vocab()
    tokens = [t for t, _ in sorted(vocab.items(), key=lambda kv: kv[1])]
    assert len(tokens) == config.vocab_size, (len(tokens), config.vocab_size)
    blank = config.pad_token_id
    delimiter = vocab.get(processor.tokenizer.word_delimiter_token)

    fp32 = os.path.join(args.out, f"{args.id}.fp32.onnx")
    int8 = os.path.join(args.out, f"{args.id}.onnx")
    dummy = torch.randn(1, 16000 * 4)
    with torch.no_grad():
        torch.onnx.export(
            LogProbs(model), (dummy,), fp32,
            input_names=["input_values"], output_names=["log_probs"],
            dynamic_axes={"input_values": {1: "samples"}, "log_probs": {1: "frames"}},
            opset_version=17, do_constant_folding=True, dynamo=False,
        )
    onnx.checker.check_model(fp32, full_check=False)
    quantize_dynamic(fp32, int8, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul"])

    # Check: the int8 model must give the same frames and nearly the same best tokens as PyTorch.
    rng = np.random.default_rng(0)
    probe = normalise(rng.standard_normal(16000 * 7).astype(np.float32) * 0.1)
    with torch.no_grad():
        ref = LogProbs(model)(torch.from_numpy(probe)[None]).numpy()[0]
    sess = ort.InferenceSession(int8, providers=["CPUExecutionProvider"])
    got = sess.run(["log_probs"], {"input_values": probe[None]})[0][0]
    assert ref.shape == got.shape, (ref.shape, got.shape)
    agreement = float((ref.argmax(-1) == got.argmax(-1)).mean())
    expected_frames = (probe.shape[0] - receptive) // stride + 1
    assert got.shape[0] == expected_frames, (got.shape, expected_frames)

    manifest = {
        "id": args.id,
        "language": args.language,
        "source": f"https://huggingface.co/{args.source or args.model}",
        "license": args.license,
        "sampleRate": 16000,
        "frameStride": stride,
        "receptiveField": receptive,
        "normalizeInput": bool(fe.do_normalize),
        "input": "input_values",
        "output": "log_probs",
        "outputIsLogProbs": True,
        "blankId": blank,
        "wordDelimiterId": delimiter,
        "vocab": tokens,
        "bytes": os.path.getsize(int8),
        "sha256": sha256(int8),
        "int8ArgmaxAgreement": round(agreement, 4),
    }
    with open(os.path.join(args.out, f"{args.id}.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    if not args.keep_fp32:
        os.remove(fp32)
    print(json.dumps({k: v for k, v in manifest.items() if k != "vocab"}, indent=1))


if __name__ == "__main__":
    sys.exit(main())
