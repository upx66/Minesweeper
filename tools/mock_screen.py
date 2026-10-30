#!/usr/bin/env python3
"""按 GameView 的真实尺寸和配色画一张界面示意图（非真机截图），用于预览排版。"""

import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 1080, 2400
D = 2.625  # 411dp x 914dp 的屏幕

BG_TOP = (14, 27, 46)
BG_BOTTOM = (6, 10, 18)
TEXT = (234, 241, 252)
TEXT_2 = (142, 161, 191)
TEXT_MUTED = (92, 111, 140)
TEXT_FAINT = (84, 101, 126)
ACCENT = (255, 180, 84)
ACCENT_DARK = (36, 23, 5)
SURFACE = (18, 29, 48)
BORDER = (31, 47, 73)
BOARD_CARD = (14, 25, 41)
BOARD_BORDER = (28, 43, 67)
CELL = (37, 55, 80)
CELL_BORDER = (49, 72, 106)
OPEN_A = (12, 22, 36)
OPEN_B = (14, 25, 41)
FLAG = (255, 95, 109)
NUM = [None, (90, 169, 255), (74, 222, 155), (255, 122, 133), (183, 156, 255),
       (255, 193, 90), (79, 216, 232), (232, 237, 247), (147, 164, 189)]

FONT_CJK = "/system/fonts/NotoSansCJK-Regular.ttc"
FONT_MONO = "/system/fonts/DroidSansMono.ttf"


def px(v):
    return v * D


def font(size_px):
    return ImageFont.truetype(FONT_CJK, max(6, int(size_px)))


def mono(size_px):
    return ImageFont.truetype(FONT_MONO, max(6, int(size_px)))


def rrect(d, box, radius, fill=None, outline=None, width=1):
    d.rounded_rectangle([round(v, 1) for v in box], radius=radius, fill=fill,
                        outline=outline, width=width)


def make_board():
    cols, rows = 12, 18
    random.seed(11)
    sr, sc = 5, 7
    cells = [(r, c) for r in range(rows) for c in range(cols)]
    candidates = [(r, c) for r, c in cells if max(abs(r - sr), abs(c - sc)) > 1]
    mines = set(random.sample(candidates, 36))
    counts = {}
    for r, c in cells:
        if (r, c) in mines:
            continue
        n = 0
        for dr in (-1, 0, 1):
            for dc in (-1, 0, 1):
                if (dr or dc) and (r + dr, c + dc) in mines:
                    n += 1
        counts[(r, c)] = n
    revealed = set()
    stack = [(sr, sc)]
    while stack:
        cur = stack.pop()
        if cur in revealed or cur in mines:
            continue
        revealed.add(cur)
        if counts[cur] == 0:
            r, c = cur
            for dr in (-1, 0, 1):
                for dc in (-1, 0, 1):
                    if dr or dc:
                        nr, nc = r + dr, c + dc
                        if 0 <= nr < rows and 0 <= nc < cols:
                            stack.append((nr, nc))
    hidden_mines = [m for m in mines if m not in revealed]
    flags = set(random.sample(hidden_mines, 10))
    return cols, rows, mines, counts, revealed, flags


def draw_mine(d, cx, cy, r, color):
    for i in range(8):
        a = math.radians(i * 45)
        x1, y1 = cx + math.cos(a) * r * 0.9, cy + math.sin(a) * r * 0.9
        x2, y2 = cx + math.cos(a) * r * 1.65, cy + math.sin(a) * r * 1.65
        d.line([x1, y1, x2, y2], fill=color, width=max(2, int(r * 0.6)), joint="curve")
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    d.ellipse([cx - r * 0.62, cy - r * 0.62, cx - r * 0.32, cy - r * 0.32],
              fill=(255, 255, 255, 150))


def draw_flag(d, cx, cy, s):
    pole_x = cx + s * 0.14
    top = cy - s * 0.34
    bottom = cy + s * 0.28
    d.line([pole_x, top, pole_x, bottom], fill=(231, 238, 249),
           width=max(3, int(s * 0.075)))
    d.line([cx - s * 0.24, cy + s * 0.3, cx + s * 0.3, cy + s * 0.3],
           fill=(231, 238, 249), width=max(3, int(s * 0.1)))
    d.polygon([(pole_x, top), (cx - s * 0.3, cy - s * 0.19),
               (pole_x, cy - s * 0.04)], fill=FLAG)


def draw_clock(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(92, 111, 140), width=3)
    d.line([cx, cy, cx, cy - r * 0.55], fill=(92, 111, 140), width=3)
    d.line([cx, cy, cx + r * 0.45, cy + r * 0.12], fill=(92, 111, 140), width=3)


def draw_face(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 201, 120))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(224, 164, 78), width=4)
    ink = (58, 42, 18)
    d.ellipse([cx - r * 0.39, cy - r * 0.23, cx - r * 0.21, cy - r * 0.05], fill=ink)
    d.ellipse([cx + r * 0.21, cy - r * 0.23, cx + r * 0.39, cy - r * 0.05], fill=ink)
    d.arc([cx - r * 0.38, cy + r * 0.02, cx + r * 0.38, cy + r * 0.6],
          20, 160, fill=ink, width=5)


def main():
    img = Image.new("RGB", (W, H))
    grad = Image.linear_gradient("L").resize((W, H), Image.LANCZOS)
    img = ImageOps.colorize(grad, black=BG_TOP, white=BG_BOTTOM)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W * 0.8 - W, -H * 0.6, W * 0.8 + W, H * 0.5], fill=(58, 92, 143, 38))
    img = Image.alpha_composite(img.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(120)))
    d = ImageDraw.Draw(img)

    pad = px(16)
    title_top = pad
    # 标题
    d.rounded_rectangle([pad, title_top + px(8), pad + px(4), title_top + px(30)],
                        radius=px(2), fill=ACCENT)
    d.text((pad + px(12), title_top + px(20)), "扫雷", font=font(px(20)),
           fill=TEXT, anchor="ls")
    d.text((pad + px(12), title_top + px(33)), "MINESWEEPER", font=font(px(7.5)),
           fill=TEXT_MUTED, anchor="ls")
    d.text((W - pad, title_top + px(19)), "最佳 00:57", font=font(px(11)),
           fill=TEXT_2, anchor="rs")

    # 难度
    seg = [pad, title_top + px(48), W - pad, title_top + px(92)]
    rrect(d, seg, px(15), SURFACE, BORDER, 2)
    labels = ["初级", "中级", "高级"]
    seg_w = (seg[2] - seg[0]) / 3
    for i, label in enumerate(labels):
        slot = [seg[0] + i * seg_w, seg[1], seg[0] + (i + 1) * seg_w, seg[3]]
        if i == 1:
            rrect(d, [slot[0] + px(4), slot[1] + px(4), slot[2] - px(4), slot[3] - px(4)],
                  px(11), ACCENT)
        cy = (slot[1] + slot[3]) / 2
        d.text((seg[0] + (i + 0.5) * seg_w, cy), label, font=font(px(13)),
               fill=ACCENT_DARK if i == 1 else TEXT_2, anchor="mm",
               stroke_width=1 if i == 1 else 0, stroke_fill=ACCENT_DARK if i == 1 else TEXT_2)

    # 状态卡
    stats = [pad, seg[3] + px(12), W - pad, seg[3] + px(12) + px(64)]
    rrect(d, stats, px(18), SURFACE, BORDER, 2)
    cy = (stats[1] + stats[3]) / 2
    cx = (stats[0] + stats[2]) / 2
    for sign in (-1, 1):
        x = cx + sign * px(72)
        d.rectangle([x - 1, stats[1] + px(16), x + 1, stats[3] - px(16)], fill=(28, 43, 68))

    draw_mine(d, stats[0] + px(30), cy, px(7), ACCENT)
    d.text((stats[0] + px(46), cy), "026", font=mono(px(18)), fill=ACCENT, anchor="lm")
    d.text((stats[2] - px(46), cy), "01:08", font=mono(px(18)), fill=TEXT, anchor="rm")
    draw_clock(d, stats[2] - px(27), cy, px(7.5))
    draw_face(d, cx, cy, px(24))

    # 棋盘
    cols, rows, mines, counts, revealed, flags = make_board()
    area_top = stats[3] + px(12)
    area_bottom = H - pad - px(46)
    inner_w = W - 2 * pad - 2 * px(8)
    inner_h = area_bottom - area_top - 2 * px(8)
    cell = max(px(9), math.floor(min(inner_w / cols, inner_h / rows)))
    gap = max(px(1.1), cell * 0.055)
    board_w = cell * cols + 2 * px(8)
    board_h = cell * rows + 2 * px(8)
    left = (W - board_w) / 2
    top = area_top + max(0, (area_bottom - area_top - board_h) / 2)
    card = [left, top, left + board_w, top + board_h]
    rrect(d, card, px(20), BOARD_CARD, BOARD_BORDER, 2)

    inner_left = left + px(8)
    inner_top = top + px(8)
    for r in range(rows):
        for c in range(cols):
            x = inner_left + c * cell + gap / 2
            y = inner_top + r * cell + gap / 2
            s = cell - gap
            if (r, c) in revealed:
                fill = OPEN_A if (r + c) % 2 == 0 else OPEN_B
                rrect(d, [x, y, x + s, y + s], s * 0.14, fill)
                n = counts[(r, c)]
                if n > 0:
                    d.text((x + s / 2, y + s / 2), str(n), font=font(s * 0.6),
                           fill=NUM[n], anchor="mm", stroke_width=1, stroke_fill=NUM[n])
            else:
                rrect(d, [x, y, x + s, y + s], s * 0.2, CELL, CELL_BORDER, 2)
                if (r, c) in flags:
                    draw_flag(d, x + s / 2, y + s / 2, s)

    # 底部
    d.text((W / 2, H - pad - px(27)), "12 × 18 · 36 雷", font=font(px(10.5)),
           fill=TEXT_MUTED, anchor="mm")
    d.text((W / 2, H - pad - px(9)), "轻点翻开 · 长按插旗 · 点数字速开", font=font(px(9.5)),
           fill=TEXT_FAINT, anchor="mm")

    out = os.path.join(ROOT, "preview.png")
    img.save(out)
    print("saved", out, img.size)


if __name__ == "__main__":
    main()
