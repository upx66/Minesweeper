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
AMBER = "#FFB454"
AMBER_DARK = "#C77F24"
FUSE = "#B06A18"
SPARK = "#FFF4D9"

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
    # 八根刺
    spike_w = max(2, int(r * 0.2))
    for i in range(8):
        angle = math.radians(i * 45)
        x1 = cx + math.cos(angle) * r * 0.8
        y1 = cy + math.sin(angle) * r * 0.8
        x2 = cx + math.cos(angle) * r * 1.5
        y2 = cy + math.sin(angle) * r * 1.5
        d.line([x1, y1, x2, y2], fill=AMBER_DARK, width=spike_w)
        d.ellipse([x2 - spike_w / 2, y2 - spike_w / 2,
                   x2 + spike_w / 2, y2 + spike_w / 2], fill=AMBER_DARK)

    # 引线
    p0 = (cx + r * 0.5, cy - r * 0.82)
    p1 = (cx + r * 0.62, cy + r * fuse_tip[1] * 0.9)
    p2 = (cx + r * fuse_tip[0], cy + r * fuse_tip[1])
    fuse_pts = quadratic(p0, p1, p2)
    fuse_w = max(2, int(r * 0.17))
    d.line(fuse_pts, fill=FUSE, width=fuse_w, joint="curve")
    d.ellipse([p0[0] - fuse_w / 2, p0[1] - fuse_w / 2,
               p0[0] + fuse_w / 2, p0[1] + fuse_w / 2], fill=FUSE)

    # 火花
    sx, sy = p2
    star_r = r * 0.42
    star = []
    for i in range(8):
        a = math.radians(i * 45)
        length = star_r if i % 2 == 0 else star_r * 0.34
        star.append((sx + math.cos(a) * length, sy + math.sin(a) * length))
    d.polygon(star, fill=SPARK)
    d.ellipse([sx - star_r * 0.2, sy - star_r * 0.2,
               sx + star_r * 0.2, sy + star_r * 0.2], fill="#FFFFFF")

    # 雷体
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=AMBER,
              outline=AMBER_DARK, width=max(2, int(r * 0.07)))

    # 高光
    hl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.ellipse([cx - r * 0.62, cy - r * 0.62, cx - r * 0.06, cy - r * 0.06],
               fill=(255, 255, 255, 95))
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

    # 预览图（不参与打包）
    preview = Image.new("RGBA", (460, 220), (10, 16, 26, 255))
    big = make_legacy(192, False)
    round_big = make_legacy(192, True)
    fg = make_foreground(216)
    preview.alpha_composite(big, (20, 14))
    preview.alpha_composite(round_big, (234, 14))
    preview.alpha_composite(fg, (234, 14))
    preview.save(os.path.join(ROOT, "icon-preview.png"))
    print("icons generated")


if __name__ == "__main__":
    main()
