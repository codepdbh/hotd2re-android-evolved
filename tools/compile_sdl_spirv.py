"""Compile the existing SDL GPU HLSL sources for the Android Vulkan backend."""

import argparse
from pathlib import Path
import re
import subprocess


SHADERS = (
    ("model_vs", "kModelShaderHlsl", "vert", "vsmain", ()),
    ("prelit_vs", "kModelShaderHlsl", "vert", "vsmain", ("NAOMI_PRELIT=1",)),
    ("model_ps", "kModelShaderHlsl", "frag", "psmain", ()),
    ("sprite_vs", "kSpriteShaderHlsl", "vert", "vsmain", ("SDLGPU_UBYTE4=1",)),
    ("sprite_ps", "kSpriteShaderHlsl", "frag", "psmain", ()),
    ("present_vs", "kPresentShaderHlsl", "vert", "vsmain", ()),
    ("present_ps", "kPresentShaderHlsl", "frag", "psmain", ()),
)

FRAGMENT_BINDINGS = (
    "TEX_REG=register(t0, space2)", "SAMP_REG=register(s0, space2)",
    "DEPTH_TEX_REG=register(t1, space2)", "DEPTH_SAMP_REG=register(s1, space2)",
    "MATERIAL_TEX_REG=register(t2, space2)", "MATERIAL_SAMP_REG=register(s2, space2)",
)


def compile_shaders(render_dir, output, glslc, validator):
    hlsl = (render_dir / "hlsl_shaders.h").read_text(encoding="utf-8")
    opacity = (render_dir / "material_opacity_hlsl.h").read_text(encoding="utf-8")
    common = re.search(r'#define HOTD2_MATERIAL_OPACITY_HLSL R"\((.*?)\)"', opacity, re.S)
    if not common:
        raise ValueError("Could not find the material opacity shader source")
    work_dir = output.parent / "spirv"
    work_dir.mkdir(parents=True, exist_ok=True)
    generated = ["#pragma once", "#include <cstdint>", "namespace port::vulkan_shader {"]

    for name, symbol, stage, entry, overrides in SHADERS:
        source = re.search(
            rf'\b{symbol}\[\]\s*=\s*(HOTD2_MATERIAL_OPACITY_HLSL\s+)?R"\((.*?)\)";',
            hlsl, re.S,
        )
        if not source:
            raise ValueError(f"Could not find {symbol}")
        source_file = work_dir / f"{name}.hlsl"
        source_text = re.sub(r'\)"\s+R"\(', "\n", source[2])
        source_file.write_text((common[1] if source[1] else "") + source_text, encoding="utf-8")
        spirv = work_dir / f"{name}.spv"
        macros = [f"CB_REG=register(b0, space{1 if stage == 'vert' else 3})", *overrides]
        if stage == "frag":
            macros.extend(FRAGMENT_BINDINGS)
        arguments = [
            glslc, "-x", "hlsl", "--target-env=vulkan1.0", "-fhlsl-iomap",
            f"-fshader-stage={stage}", f"-fentry-point={entry}",
        ]
        if stage == "frag":
            arguments.append("-fauto-combined-image-sampler")
        subprocess.run([*arguments, *(f"-D{macro}" for macro in macros), str(source_file), "-o", str(spirv)], check=True)
        subprocess.run([validator, "--target-env", "vulkan1.0", str(spirv)], check=True)
        generated.append(f"alignas(4) inline constexpr std::uint8_t {name}[] = {{")
        code = spirv.read_bytes()
        for offset in range(0, len(code), 16):
            generated.append("    " + ", ".join(f"0x{byte:02x}" for byte in code[offset:offset + 16]) + ",")
        generated.append("};")

    generated.append("} // namespace port::vulkan_shader\n")
    output.write_text("\n".join(generated), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--render-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--glslc", required=True)
    parser.add_argument("--validator", required=True)
    args = parser.parse_args()
    compile_shaders(args.render_dir, args.output, args.glslc, args.validator)
