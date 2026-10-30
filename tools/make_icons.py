#!/usr/bin/env python3
"""生成扫雷 App 的启动图标：自适应图标前景 + 传统方/圆图标。"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

SS = 4  # 超采样倍数
BG_TOP = "#1B2E49"
BG_BOTTOM = "#070D18"
AMBER = "#F7A93F"
AMBER_DARK = "#A56012"
FUSE = "#8A5510"
SPARK = "#FFF0C9"

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def quadratic(p0, p1, p2, n=28):
    points = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        points.append((x, y))
    return points


def glow(size, cx, cy, radius, color=(64, 110, 170, 85)):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.45))


def draw_bomb(img, cx, cy, r, fuse_tip=(1.45, -1.75)):
    size = img.size[0]
    # 阴影
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.ellipse([cx - r * 1.15, cy + r * 0.55, cx + r * 1.15, cy + r * 1.05],
               fill=(0, 0, 0, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(r * 0.16))
    img.alpha_composite(shadow)

    d = ImageDraw.Draw(img)
    # 八根短刺，避免看起来像太阳
    spike_w = max(2, int(r * 0.24))
    for i in range(8):
        angle = math.radians(i * 45)
        x1 = cx + math.cos(angle) * r * 0.95
        y1 = cy + math.sin(angle) * r * 0.95
        x2 = cx + math.cos(angle) * r * 1.33
        y2 = cy + math.sin(angle) * r * 1.33
        d.line([x1, y1, x2, y2], fill=AMBER_DARK, width=spike_w)
        d.ellipse([x2 - spike_w / 2, y2 - spike_w / 2,
                   x2 + spike_w / 2, y2 + spike_w / 2], fill=AMBER_DARK)

    # 引线
    p0 = (cx + r * 0.45, cy - r * 0.87)
    p1 = (cx + r * 0.58, cy + r * fuse_tip[1] * 0.9)
    p2 = (cx + r * fuse_tip[0], cy + r * fuse_tip[1])
    fuse_pts = quadratic(p0, p1, p2)
    fuse_w = max(2, int(r * 0.21))
    d.line(fuse_pts, fill=FUSE, width=fuse_w, joint="curve")
    d.ellipse([p0[0] - fuse_w / 2, p0[1] - fuse_w / 2,
               p0[0] + fuse_w / 2, p0[1] + fuse_w / 2], fill=FUSE)

    # 火花：一颗小火星，而不是长芒星
    sx, sy = p2
    d.ellipse([sx - r * 0.16, sy - r * 0.16, sx + r * 0.16, sy + r * 0.16],
              fill=SPARK)
    for i in range(4):
        a = math.radians(i * 90 + 45)
        d.line([sx + math.cos(a) * r * 0.14, sy + math.sin(a) * r * 0.14,
                sx + math.cos(a) * r * 0.38, sy + math.sin(a) * r * 0.38],
               fill=SPARK, width=max(2, int(r * 0.07)))

    # 雷体：上亮下暗的球面感
    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = Image.linear_gradient("L").resize((size, size), Image.LANCZOS)
    body_colour = ImageOps.colorize(grad, black="#FFC974", white="#E28B26").convert("RGBA")
    body_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(body_mask).ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    body.paste(body_colour, (0, 0), body_mask)
    img.alpha_composite(body)
    d.ellipse([cx - r, cy - r, cx + r, cy + r],
              outline=AMBER_DARK, width=max(2, int(r * 0.07)))

    # 高光
    hl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.ellipse([cx - r * 0.5, cy - r * 0.5, cx - r * 0.18, cy - r * 0.18],
               fill=(255, 255, 255, 80))
    img.alpha_composite(hl)


def background(size):
    grad = Image.linear_gradient("L").resize((size, size), Image.LANCZOS)
    return ImageOps.colorize(grad, black=BG_TOP, white=BG_BOTTOM).convert("RGBA")


def make_legacy(size, round_icon):
    canvas = size * SS
    img = background(canvas)
    img.alpha_composite(glow(canvas, canvas * 0.5, canvas * 0.46, canvas * 0.48))
    draw_bomb(img, canvas * 0.5, canvas * 0.52, canvas * 0.245)

    mask = Image.new("L", (canvas, canvas), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse([0, 0, canvas - 1, canvas - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, canvas - 1, canvas - 1],
                             radius=canvas * 0.23, fill=255)
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


def make_foreground(size):
    canvas = size * SS
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    draw_bomb(img, canvas * 0.5, canvas * 0.52, canvas * 0.145,
              fuse_tip=(1.0, -1.45))
    return img.resize((size, size), Image.LANCZOS)


def main():
    for density, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        make_legacy(size, False).save(os.path.join(folder, "ic_launcher.png"))
        make_legacy(size, True).save(os.path.join(folder, "ic_launcher_round.png"))
    for density, size in FOREGROUND_SIZES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        make_foreground(size).save(os.path.join(folder, "ic_launcher_foreground.png"))

    # 预览图（不参与打包）：方形 / 圆形 / 自适应图标（圆形裁切）
    preview = Image.new("RGBA", (680, 230), (9, 15, 24, 255))
    preview.alpha_composite(make_legacy(192, False), (20, 19))
    preview.alpha_composite(make_legacy(192, True), (236, 19))
    tile = Image.new("RGBA", (216, 216), (11, 20, 34, 255))
    tile.alpha_composite(make_foreground(216), (0, 0))
    mask = Image.new("L", (216, 216), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 215, 215], fill=255)
    tile.putalpha(mask)
    preview.alpha_composite(tile, (444, 7))
    preview.save(os.path.join(ROOT, "icon-preview.png"))
    print("icons generated")


if __name__ == "__main__":
    main()
