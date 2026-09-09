"""Generate TRACE adaptive icon mipmaps from the provided logo JPEG."""
from PIL import Image
import os

src = r"../design/icons"
out_dir = r"../android\app\src\main\res"

im = Image.open(src).convert("RGBA")
w, h = im.size
pixels = im.load()
for y in range(h):
    for x in range(w):
        r, g, b, a = pixels[x, y]
        if r > 230 and g > 230 and b > 230:
            pixels[x, y] = (r, g, b, 0)
        elif r > 210 and g > 210 and b > 210 and abs(r - g) < 12 and abs(g - b) < 12:
            alpha = int(max(0, (230 - min(r, g, b)) * 255 / 20))
            pixels[x, y] = (r, g, b, alpha)

os.makedirs(os.path.join(out_dir, "drawable"), exist_ok=True)
im.save(os.path.join(out_dir, "drawable", "brand_trace_full.png"), "PNG")

wave = im.crop((0, int(h * 0.08), w, int(h * 0.62)))
canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
target_w = 780
ratio = target_w / wave.width
target_h = int(wave.height * ratio)
wave_r = wave.resize((target_w, target_h), Image.Resampling.LANCZOS)
x0 = (1024 - target_w) // 2
y0 = (1024 - target_h) // 2 - 40
canvas.paste(wave_r, (x0, y0), wave_r)
canvas.save(os.path.join(out_dir, "drawable", "ic_launcher_foreground_bitmap.png"), "PNG")

densities = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}
for folder, size in densities.items():
    d = os.path.join(out_dir, folder)
    os.makedirs(d, exist_ok=True)
    fg = canvas.resize((size, size), Image.Resampling.LANCZOS)
    fg.save(os.path.join(d, "ic_launcher_foreground.png"), "PNG")
    bg = Image.new("RGBA", (size, size), (245, 247, 250, 255))
    composed = Image.alpha_composite(bg, fg)
    rgb = composed.convert("RGB")
    rgb.save(os.path.join(d, "ic_launcher.png"), "PNG")
    rgb.save(os.path.join(d, "ic_launcher_round.png"), "PNG")
    print(folder, size, "ok")

print("done")
