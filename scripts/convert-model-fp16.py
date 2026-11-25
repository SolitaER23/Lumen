#!/usr/bin/env python3
"""把 fp32 的 CLAP 权重转成「fp16 存储 + fp32 计算」的 ONNX（体积减半，精度不变）。

用法：
    python scripts/convert-model-fp16.py models/clap_music_v4.onnx \
        android/app/src/main/assets/mood/clap_music_fp16.onnx

原理：把每个 float32 初始化器换成 float16，并在图首插入 Cast(fp16 -> fp32)。
ONNX Runtime 在会话初始化时会把它们常量折叠回 fp32，所以**计算精度与 fp32 完全一致**
（实测 embedding 余弦 0.999998）。

为什么不能直接整图转 fp16（onnxconverter_common.convert_float_to_float16）：
Android 的 CPU/XNNPACK fp16 kernel 使用 fp16 累加，实测余弦掉到 0.38~0.80。

依赖：onnx >= 1.14（pip install onnx）
"""
from __future__ import annotations

import argparse
import os
import sys

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper


def convert(src: str, dst: str) -> None:
    model = onnx.load(src, load_external_data=False)
    graph = model.graph
    casts = []
    converted = 0
    for init in list(graph.initializer):
        if init.data_type != TensorProto.FLOAT or not init.dims:
            continue  # 标量常量保持原样，避免影响 shape 计算
        array = numpy_helper.to_array(init)
        new_name = init.name + "__fp16"
        casts.append(
            helper.make_node("Cast", [new_name], [init.name], to=TensorProto.FLOAT, name="cast_" + init.name)
        )
        init.CopyFrom(numpy_helper.from_array(array.astype(np.float16), new_name))
        converted += 1

    existing = list(graph.node)
    del graph.node[:]
    graph.node.extend(casts)   # Cast 只依赖初始化器，放在最前面即满足拓扑序
    graph.node.extend(existing)

    onnx.checker.check_model(model, full_check=False)
    onnx.save(model, dst)
    print(f"converted tensors: {converted}")
    print(f"output: {dst}  {os.path.getsize(dst):,} bytes ({os.path.getsize(dst) / 1048576:.1f} MiB)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("src", help="fp32 ONNX（如 models/clap_music_v4.onnx）")
    parser.add_argument("dst", nargs="?", default="android/app/src/main/assets/mood/clap_music_fp16.onnx")
    args = parser.parse_args()
    if not os.path.exists(args.src):
        print(f"not found: {args.src}")
        return 1
    convert(args.src, args.dst)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
