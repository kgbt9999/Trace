"""High-quality TRACE adaptive icons from the attached JPG artwork.

Uses actual pixels (wave + wordmark) вЂ” no crude vector approximation.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageFilter, ImageEnhance
import numpy as np

SRC = Path(
    r"../design/icons"
    r"\C__Users_HONOR_.cursor_projects_c-Users-HONOR-moodlife-android_assets"
    r"_c__Users_HONOR_AppData_Roaming_Cursor_User_workspaceStorage"
    r"_64fe75f38578880242612d3d37fa66f9_images"
    r"_Gemini_Generated_Image_n5ka91n5ka91n5ka-8d7df015-4e6a-4c7b-988b-cc21d5384800.jpg"
)
RES = Path(r"../android\app\src\main\res")


def to_rgba_transparent_bg(im: Image.Image, threshold: int = 232) -> Image.Image:
    """Make near-white background transparent with soft edge."""
    rgba = im.convert("RGBA")
    arr = np.array(rgba).astype(np.float32)
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]
    # brightness / neutrality of background
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    chroma = mx - mn
    # near-white, low-chroma в†’ transparent
    whiteness = (r + g + b) / 3.0
    bg = (whiteness >= threshold) & (chroma < 18)
    soft = (whiteness >= threshold - 18) & (chroma < 28) & ~bg
    alpha = a.copy()
    alpha[bg] = 0
    # soft fade near edges of logo
    fade = np.clip((threshold - whiteness[soft]) / 18.0, 0, 1) * 255
    alpha[soft] = np.minimum(alpha[soft], fade)
    arr[:, :, 3] = alpha
    out = Image.fromarray(arr.astype(np.uint8), "RGBA")
    return out


def content_bbox(im: Image.Image, alpha_min: int = 8) -> tuple[int, int, int, int]:
    arr = np.array(im)
    mask = arr[:, :, 3] > alpha_min
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return 0, 0, im.width, im.height
    pad = 4
    return (
        max(0, int(xs.min()) - pad),
        max(0, int(ys.min()) - pad),
        min(im.width, int(xs.max()) + pad + 1),
        min(im.height, int(ys.max()) + pad + 1),
    )


def paste_centered(canvas: Image.Image, layer: Image.Image, scale: float, y_bias: float = 0.0) -> None:
    tw = int(canvas.width * scale)
    ratio = tw / layer.width
    th = int(layer.height * ratio)
    resized = layer.resize((tw, th), Image.Resampling.LANCZOS)
    x = (canvas.width - tw) // 2
    y = int((canvas.height - th) / 2 + y_bias * canvas.height)
    canvas.paste(resized, (x, y), resized)


def split_wave_and_wordmark(full: Image.Image) -> tuple[Image.Image, Image.Image]:
    """Heuristic: wordmark is the dark navy text in the lower portion."""
    w, h = full.size
    # Wave occupies roughly upper 55вЂ“62%; TRACE text below
    wave = full.crop((0, 0, w, int(h * 0.58)))
    word = full.crop((0, int(h * 0.52), w, h))
    # Trim each to content
    wb = content_bbox(wave)
    wave = wave.crop(wb)
    ub = content_bbox(word)
    word = word.crop(ub)
    return wave, word


def make_adaptive_fg(wave: Image.Image, size: int = 1024) -> Image.Image:
    """Wave-only mark inside Android adaptive safe zone (~66%)."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # ~72% of canvas в†’ sits inside safe zone with margin
    paste_centered(canvas, wave, scale=0.78, y_bias=-0.02)
    return canvas


def make_brand_mark(wave: Image.Image, size: int = 512) -> Image.Image:
    """Circular-ish header mark: wave on transparent (UI clips via Image)."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    paste_centered(canvas, wave, scale=0.88, y_bias=0.0)
    return canvas


def make_full_brand(full: Image.Image, size: int = 1024) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    paste_centered(canvas, full, scale=0.90, y_bias=0.0)
    return canvas


def write_densities(fg: Image.Image, full_rgb_bg: tuple[int, int, int] = (247, 248, 249)) -> None:
    densities = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, size in densities.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        fg_s = fg.resize((size, size), Image.Resampling.LANCZOS)
        fg_s.save(d / "ic_launcher_foreground.png", "PNG", optimize=True)
        bg = Image.new("RGBA", (size, size), (*full_rgb_bg, 255))
        composed = Image.alpha_composite(bg, fg_s).convert("RGB")
        composed.save(d / "ic_launcher.png", "PNG", optimize=True)
        composed.save(d / "ic_launcher_round.png", "PNG", optimize=True)
        print(folder, size, "ok", (d / "ic_launcher_foreground.png").stat().st_size)


def main() -> None:
    assert SRC.exists(), f"missing source: {SRC}"
    raw = Image.open(SRC)
    print("source", raw.size, raw.mode)

    full = to_rgba_transparent_bg(raw)
    # Slight contrast for teal/navy separation on white
    rgb = full.convert("RGB")
    rgb = ImageEnhance.Contrast(rgb).enhance(1.05)
    rgb = ImageEnhance.Color(rgb).enhance(1.08)
    full = Image.merge(
        "RGBA",
        (*rgb.split(), full.split()[-1]),
    )

    # Trim full artwork
    bbox = content_bbox(full)
    full_trim = full.crop(bbox)
    wave, word = split_wave_and_wordmark(full_trim)
    print("wave", wave.size, "word", word.size, "full", full_trim.size)

    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)

    # Full branding (wave + TRACE) for splash / wide header
    full_brand = make_full_brand(full_trim, 1024)
    full_brand.save(drawable / "brand_trace_full.png", "PNG", optimize=True)

    # Wave-only adaptive foreground
    fg = make_adaptive_fg(wave, 1024)
    fg.save(drawable / "ic_launcher_foreground_bitmap.png", "PNG", optimize=True)

    # In-app brand mark (wave raster, replaces crude vector usage)
    mark = make_brand_mark(wave, 512)
    mark.save(drawable / "ic_brand_mark.png", "PNG", optimize=True)

    # Also keep a wider header lockup
    header = Image.new("RGBA", (1024, 320), (0, 0, 0, 0))
    # wave on top, wordmark below
    paste_centered(header, wave, scale=0.72, y_bias=-0.18)
    paste_centered(header, word, scale=0.55, y_bias=0.28)
    header.save(drawable / "brand_trace_header.png", "PNG", optimize=True)

    write_densities(fg)
    print("done вЂ” sizes:")
    for name in [
        "brand_trace_full.png",
        "brand_trace_header.png",
        "ic_brand_mark.png",
        "ic_launcher_foreground_bitmap.png",
    ]:
        p = drawable / name
        print(f"  {name}: {p.stat().st_size} bytes")


if __name__ == "__main__":
    main()
