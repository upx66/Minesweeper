package com.dsh.minesweeper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 整个游戏的界面：标题、难度、计时、棋盘、结算层都在这块画布上绘制。
 * 单文件自绘，避免引入任何第三方 UI 依赖。
 */
public class GameView extends View {

    // ---- 配色 ----
    private static final int COLOR_BG_TOP = 0xFF0E1B2E;
    private static final int COLOR_BG_BOTTOM = 0xFF060A12;
    private static final int COLOR_TEXT = 0xFFEAF1FC;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8EA1BF;
    private static final int COLOR_TEXT_MUTED = 0xFF5C6F8C;
    private static final int COLOR_TEXT_FAINT = 0xFF43536B;
    private static final int COLOR_ACCENT = 0xFFFFB454;
    private static final int COLOR_ACCENT_DARK = 0xFF241705;
    private static final int COLOR_SURFACE = 0xFF121D30;
    private static final int COLOR_BORDER = 0xFF1F2F49;
    private static final int COLOR_BOARD_CARD = 0xFF0E1929;
    private static final int COLOR_BOARD_BORDER = 0xFF1C2B43;
    private static final int COLOR_CELL = 0xFF253750;
    private static final int COLOR_CELL_PRESSED = 0xFF3D587F;
    private static final int COLOR_CELL_BORDER = 0xFF31486A;
    private static final int COLOR_CELL_PRESSED_BORDER = 0xFF5777A6;
    private static final int COLOR_OPEN_A = 0xFF0C1624;
    private static final int COLOR_OPEN_B = 0xFF0E1929;
    private static final int COLOR_FLAG = 0xFFFF5F6D;
    private static final int COLOR_DANGER = 0xFFFF5F6D;
    private static final int COLOR_WIN = 0xFF4ADE9B;
    private static final int COLOR_OVERLAY_CARD = 0xFF16233A;
    private static final int COLOR_OVERLAY_BORDER = 0xFF2C4162;
    private static final int COLOR_EXPLODED_BG = 0xFF6E1D2A;
    private static final int COLOR_MINE_CELL_BG = 0xFF171E2C;

    private static final int[] NUMBER_COLORS = {
            0x00000000,
            0xFF5AA9FF, // 1 蓝
            0xFF4ADE9B, // 2 绿
            0xFFFF7A85, // 3 红
            0xFFB79CFF, // 4 紫
            0xFFFFC15A, // 5 橙
            0xFF4FD8E8, // 6 青
            0xFFE8EDF7, // 7 白
            0xFF93A4BD, // 8 灰
    };

    private static final int[] CONFETTI_COLORS = {
            0xFFFFB454, 0xFF5AA9FF, 0xFF4ADE9B, 0xFFFF7A85, 0xFFB79CFF, 0xFFFFFFFF,
    };

    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flagPath = new Path();
    private final RectF tmpRect = new RectF();
    private final RectF mouthRect = new RectF();

    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface sansBold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface mono = Typeface.create("monospace", Typeface.BOLD);

    private final float pad = dp(16);

    private final RectF titleRect = new RectF();
    private final RectF segRect = new RectF();
    private final RectF[] segRects = {new RectF(), new RectF(), new RectF()};
    private final RectF statsRect = new RectF();
    private final RectF faceRect = new RectF();
    private final RectF boardCardRect = new RectF();
    private final RectF boardInner = new RectF();
    private final RectF overlayCardRect = new RectF();
    private final RectF overlayPrimary = new RectF();
    private final RectF overlaySecondary = new RectF();

    private float cell = dp(24);
    private float gap = dp(1.5f);

    private final SharedPreferences prefs;
    private final int[] bestSeconds = new int[Game.Level.values().length];

    private Game game;
    private int selectedLevel;
    private int pressedCell = -1;
    private boolean facePressed;
    private boolean overlayDismissed;
    private long overlayStartedAt;
    private boolean winNewRecord;
    private int winSeconds;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GestureDetector detector;
    private final Random random = new Random();

    private final List<Particle> particles = new ArrayList<>();
    private long lastFrameAt;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (game.status == Game.Status.PLAYING) {
                handler.postDelayed(this, 200);
            }
        }
    };

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("minesweeper", Context.MODE_PRIVATE);
        selectedLevel = prefs.getInt("level", Game.Level.MEDIUM.ordinal());
        if (selectedLevel < 0 || selectedLevel >= Game.Level.values().length) {
            selectedLevel = 0;
        }
        loadBests();
        game = new Game(Game.Level.values()[selectedLevel]);
        detector = new GestureDetector(context, new Gestures());
        detector.setIsLongpressEnabled(true);
        setBackgroundColor(COLOR_BG_TOP);
        setContentDescription("扫雷棋盘");
        setFocusable(true);
    }

    // ---- 单位换算 ----

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    private void useText(float sizeSp, int color, Typeface typeface, Paint.Align align) {
        textPaint.setColor(color);
        textPaint.setTextSize(sp(sizeSp));
        textPaint.setTypeface(typeface);
        textPaint.setTextAlign(align);
        textPaint.setLetterSpacing(0f);
        textPaint.setAlpha(255);
    }

    private float baseline(float centerY, Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    // ---- 数据 ----

    private static String bestKey(Game.Level level) {
        return "best_" + level.name().toLowerCase(Locale.US);
    }

    private void loadBests() {
        for (Game.Level level : Game.Level.values()) {
            bestSeconds[level.ordinal()] = prefs.getInt(bestKey(level), 0);
        }
    }

    private static String formatTime(int seconds) {
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    // ---- 布局 ----

    private void computeLayout() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float titleTop = pad;
        titleRect.set(pad, titleTop, w - pad, titleTop + dp(38));

        float segTop = titleRect.bottom + dp(10);
        segRect.set(pad, segTop, w - pad, segTop + dp(44));
        float segW = segRect.width() / 3f;
        for (int i = 0; i < 3; i++) {
            segRects[i].set(segRect.left + i * segW, segRect.top,
                    segRect.left + (i + 1) * segW, segRect.bottom);
        }

        float statsTop = segRect.bottom + dp(12);
        statsRect.set(pad, statsTop, w - pad, statsTop + dp(64));
        float faceR = dp(24);
        faceRect.set(statsRect.centerX() - faceR, statsRect.centerY() - faceR,
                statsRect.centerX() + faceR, statsRect.centerY() + faceR);

        float footerH = dp(46);
        float areaTop = statsRect.bottom + dp(12);
        float areaBottom = h - pad - footerH;
        float innerW = w - 2 * pad - 2 * dp(8);
        float innerH = areaBottom - areaTop - 2 * dp(8);
        float size = Math.min(innerW / game.cols, innerH / game.rows);
        cell = Math.max(dp(9), (float) Math.floor(size));
        gap = Math.max(dp(1.1f), cell * 0.055f);

        float boardW = cell * game.cols + 2 * dp(8);
        float boardH = cell * game.rows + 2 * dp(8);
        float left = (w - boardW) / 2f;
        float top = areaTop + Math.max(0f, (areaBottom - areaTop - boardH) / 2f);
        boardCardRect.set(left, top, left + boardW, top + boardH);
        boardInner.set(left + dp(8), top + dp(8), left + boardW - dp(8), top + boardH - dp(8));

        float cardW = Math.min(w - dp(48), dp(310));
        float cardH = dp(268);
        float cardCx = w / 2f;
        float cardCy = h / 2f - dp(6);
        overlayCardRect.set(cardCx - cardW / 2, cardCy - cardH / 2,
                cardCx + cardW / 2, cardCy + cardH / 2);
        float rowCy = overlayCardRect.top + dp(228);
        overlayPrimary.set(cardCx - dp(118), rowCy - dp(22), cardCx + dp(12), rowCy + dp(22));
        overlaySecondary.set(cardCx + dp(22), rowCy - dp(22), cardCx + dp(118), rowCy + dp(22));

        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                COLOR_BG_TOP, COLOR_BG_BOTTOM, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(w * 0.8f, -h * 0.08f, w * 0.95f,
                0x203A5C8F, 0x000E1B2E, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeLayout();
    }

    // ---- 绘制 ----

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        shapePaint.setAlpha(255);
        shapePaint.setStyle(Paint.Style.FILL);
        textPaint.setAlpha(255);
        textPaint.setLetterSpacing(0f);

        long now = SystemClock.uptimeMillis();
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);

        drawHeader(canvas);
        drawSegments(canvas);
        drawStats(canvas);
        drawBoard(canvas);
        drawFooter(canvas);
        drawOverlay(canvas, now);
        drawParticles(canvas, now);
    }

    private void drawHeader(Canvas canvas) {
        float barW = dp(4);
        float barH = dp(22);
        float barTop = titleRect.centerY() - barH / 2f;
        shapePaint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(pad, barTop, pad + barW, barTop + barH, barW / 2f, barW / 2f,
                shapePaint);

        useText(20f, COLOR_TEXT, sansBold, Paint.Align.LEFT);
        canvas.drawText("扫雷", pad + dp(12), titleRect.top + dp(20), textPaint);

        useText(7.5f, COLOR_TEXT_MUTED, sansMedium, Paint.Align.LEFT);
        textPaint.setLetterSpacing(0.18f);
        canvas.drawText("MINESWEEPER", pad + dp(12), titleRect.top + dp(33), textPaint);
        textPaint.setLetterSpacing(0f);

        int best = bestSeconds[selectedLevel];
        String bestText = best == 0 ? "最佳 --:--" : "最佳 " + formatTime(best);
        useText(11f, COLOR_TEXT_SECONDARY, sansMedium, Paint.Align.RIGHT);
        canvas.drawText(bestText, getWidth() - pad, baseline(titleRect.centerY(), textPaint),
                textPaint);
    }

    private void drawSegments(Canvas canvas) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(COLOR_SURFACE);
        canvas.drawRoundRect(segRect, dp(15), dp(15), shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1));
        shapePaint.setColor(COLOR_BORDER);
        canvas.drawRoundRect(segRect, dp(15), dp(15), shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        Game.Level[] levels = Game.Level.values();
        for (int i = 0; i < levels.length; i++) {
            RectF slot = segRects[i];
            boolean selected = i == selectedLevel;
            if (selected) {
                tmpRect.set(slot.left + dp(4), slot.top + dp(4), slot.right - dp(4),
                        slot.bottom - dp(4));
                shapePaint.setColor(COLOR_ACCENT);
                canvas.drawRoundRect(tmpRect, dp(11), dp(11), shapePaint);
            }
            useText(13f, selected ? COLOR_ACCENT_DARK : COLOR_TEXT_SECONDARY,
                    selected ? sansBold : sansMedium, Paint.Align.CENTER);
            canvas.drawText(levels[i].label, slot.centerX(),
                    baseline(slot.centerY(), textPaint), textPaint);
        }
    }

    private void drawStats(Canvas canvas) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(COLOR_SURFACE);
        canvas.drawRoundRect(statsRect, dp(18), dp(18), shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1));
        shapePaint.setColor(COLOR_BORDER);
        canvas.drawRoundRect(statsRect, dp(18), dp(18), shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        shapePaint.setColor(ColorUtil.DIVIDER);
        float divTop = statsRect.top + dp(16);
        float divBottom = statsRect.bottom - dp(16);
        canvas.drawRect(statsRect.centerX() - dp(72) - dp(0.5f), divTop,
                statsRect.centerX() - dp(72) + dp(0.5f), divBottom, shapePaint);
        canvas.drawRect(statsRect.centerX() + dp(72) - dp(0.5f), divTop,
                statsRect.centerX() + dp(72) + dp(0.5f), divBottom, shapePaint);

        float cy = statsRect.centerY();
        drawMineIcon(canvas, statsRect.left + dp(30), cy, dp(7), COLOR_ACCENT);
        int remaining = Math.max(-99, Math.min(999, game.remainingMines()));
        useText(18f, COLOR_ACCENT, mono, Paint.Align.LEFT);
        canvas.drawText(String.format(Locale.US, "%03d", remaining),
                statsRect.left + dp(46), baseline(cy, textPaint), textPaint);

        useText(18f, COLOR_TEXT, mono, Paint.Align.RIGHT);
        canvas.drawText(formatTime(game.elapsedSeconds()), statsRect.right - dp(46),
                baseline(cy, textPaint), textPaint);
        drawClockIcon(canvas, statsRect.right - dp(27), cy, dp(7.5f));

        drawFace(canvas);
    }

    private void drawFace(Canvas canvas) {
        float cx = faceRect.centerX();
        float cy = faceRect.centerY();
        float r = faceRect.width() / 2f;
        float rr = facePressed ? r * 0.95f : r;

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(facePressed ? 0xFFF2BB6E : 0xFFFFC978);
        canvas.drawCircle(cx, cy, rr, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1.4f));
        shapePaint.setColor(0xFFE0A44E);
        canvas.drawCircle(cx, cy, rr, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        int ink = 0xFF3A2A12;
        shapePaint.setColor(ink);
        Game.Status status = game.status;

        if (status == Game.Status.WON) {
            float w = rr * 0.62f;
            float h = rr * 0.3f;
            float top = cy - rr * 0.34f;
            canvas.drawRoundRect(cx - rr * 0.66f, top, cx - rr * 0.06f, top + h,
                    h / 2f, h / 2f, shapePaint);
            canvas.drawRoundRect(cx + rr * 0.06f, top, cx + rr * 0.66f, top + h,
                    h / 2f, h / 2f, shapePaint);
            shapePaint.setStrokeWidth(rr * 0.09f);
            canvas.drawLine(cx - rr * 0.06f, cy - rr * 0.24f, cx + rr * 0.06f,
                    cy - rr * 0.24f, shapePaint);
        } else if (status == Game.Status.LOST) {
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeWidth(rr * 0.11f);
            shapePaint.setStrokeCap(Paint.Cap.ROUND);
            float e = rr * 0.15f;
            float ex = rr * 0.3f;
            float ey = cy - rr * 0.14f;
            canvas.drawLine(cx - ex - e, ey - e, cx - ex + e, ey + e, shapePaint);
            canvas.drawLine(cx - ex - e, ey + e, cx - ex + e, ey - e, shapePaint);
            canvas.drawLine(cx + ex - e, ey - e, cx + ex + e, ey + e, shapePaint);
            canvas.drawLine(cx + ex - e, ey + e, cx + ex + e, ey - e, shapePaint);
            shapePaint.setStrokeCap(Paint.Cap.BUTT);
            shapePaint.setStyle(Paint.Style.FILL);
        } else if (facePressed) {
            canvas.drawCircle(cx - rr * 0.3f, cy - rr * 0.14f, rr * 0.085f, shapePaint);
            canvas.drawCircle(cx + rr * 0.3f, cy - rr * 0.14f, rr * 0.085f, shapePaint);
            canvas.drawCircle(cx, cy + rr * 0.3f, rr * 0.13f, shapePaint);
        } else {
            canvas.drawCircle(cx - rr * 0.3f, cy - rr * 0.14f, rr * 0.085f, shapePaint);
            canvas.drawCircle(cx + rr * 0.3f, cy - rr * 0.14f, rr * 0.085f, shapePaint);
        }

        if (status == Game.Status.LOST) {
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeWidth(rr * 0.1f);
            shapePaint.setStrokeCap(Paint.Cap.ROUND);
            mouthRect.set(cx - rr * 0.34f, cy + rr * 0.34f, cx + rr * 0.34f, cy + rr * 0.86f);
            canvas.drawArc(mouthRect, 200f, 140f, false, shapePaint);
            shapePaint.setStrokeCap(Paint.Cap.BUTT);
            shapePaint.setStyle(Paint.Style.FILL);
        } else if (!facePressed) {
            shapePaint.setStyle(Paint.Style.STROKE);
            shapePaint.setStrokeWidth(rr * 0.1f);
            shapePaint.setStrokeCap(Paint.Cap.ROUND);
            mouthRect.set(cx - rr * 0.38f, cy + rr * 0.02f, cx + rr * 0.38f, cy + rr * 0.6f);
            canvas.drawArc(mouthRect, 20f, 140f, false, shapePaint);
            shapePaint.setStrokeCap(Paint.Cap.BUTT);
            shapePaint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawBoard(Canvas canvas) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(COLOR_BOARD_CARD);
        canvas.drawRoundRect(boardCardRect, dp(20), dp(20), shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1));
        shapePaint.setColor(COLOR_BOARD_BORDER);
        canvas.drawRoundRect(boardCardRect, dp(20), dp(20), shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        for (int r = 0; r < game.rows; r++) {
            for (int c = 0; c < game.cols; c++) {
                int idx = r * game.cols + c;
                float x = boardInner.left + c * cell + gap / 2f;
                float y = boardInner.top + r * cell + gap / 2f;
                float s = cell - gap;
                byte state = game.state[idx];

                if (state == Game.REVEALED) {
                    if (game.isMine[idx]) {
                        float radius = s * 0.14f;
                        shapePaint.setColor(idx == game.explodedIndex
                                ? COLOR_EXPLODED_BG : COLOR_MINE_CELL_BG);
                        canvas.drawRoundRect(x, y, x + s, y + s, radius, radius, shapePaint);
                        drawMineIcon(canvas, x + s / 2f, y + s / 2f, s * 0.24f,
                                idx == game.explodedIndex ? 0xFFFFE6D5 : COLOR_ACCENT);
                    } else {
                        float radius = s * 0.14f;
                        shapePaint.setColor(((r + c) & 1) == 0 ? COLOR_OPEN_A : COLOR_OPEN_B);
                        canvas.drawRoundRect(x, y, x + s, y + s, radius, radius, shapePaint);
                        int n = game.around[idx];
                        if (n > 0) {
                            useText(0f, NUMBER_COLORS[n], sansBold, Paint.Align.CENTER);
                            textPaint.setTextSize(s * 0.6f);
                            canvas.drawText(Integer.toString(n), x + s / 2f,
                                    baseline(y + s / 2f, textPaint), textPaint);
                        }
                    }
                } else {
                    boolean pressed = idx == pressedCell && !game.finished();
                    float radius = s * 0.2f;
                    shapePaint.setColor(pressed ? COLOR_CELL_PRESSED : COLOR_CELL);
                    canvas.drawRoundRect(x, y, x + s, y + s, radius, radius, shapePaint);
                    shapePaint.setStyle(Paint.Style.STROKE);
                    shapePaint.setStrokeWidth(Math.max(dp(0.8f), s * 0.03f));
                    shapePaint.setColor(pressed ? COLOR_CELL_PRESSED_BORDER : COLOR_CELL_BORDER);
                    canvas.drawRoundRect(x, y, x + s, y + s, radius, radius, shapePaint);
                    shapePaint.setStyle(Paint.Style.FILL);

                    if (state == Game.FLAGGED) {
                        boolean wrong = game.finished() && !game.isMine[idx];
                        drawFlag(canvas, x + s / 2f, y + s / 2f, s, COLOR_FLAG);
                        if (wrong) {
                            drawCross(canvas, x + s / 2f, y + s / 2f, s);
                        }
                    }
                }
            }
        }
    }

    private void drawFooter(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        useText(10.5f, COLOR_TEXT_MUTED, sansMedium, Paint.Align.CENTER);
        canvas.drawText(game.level.spec(), w / 2f, h - pad - dp(27), textPaint);
        useText(9.5f, COLOR_TEXT_FAINT, sans, Paint.Align.CENTER);
        canvas.drawText("轻点翻开 · 长按插旗 · 点数字速开", w / 2f, h - pad - dp(9), textPaint);
    }

    private void drawOverlay(Canvas canvas, long now) {
        if (!overlayVisible()) {
            return;
        }
        float a = Math.min(1f, (now - overlayStartedAt) / 220f);
        float ease = 1f - (1f - a) * (1f - a);
        int alpha = (int) (255 * a);

        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xFF05090F);
        shapePaint.setAlpha((int) (204 * a));
        canvas.drawRect(0, 0, getWidth(), getHeight(), shapePaint);
        shapePaint.setAlpha(255);

        float cx = overlayCardRect.centerX();
        float cy = overlayCardRect.centerY();
        canvas.save();
        float scale = 0.94f + 0.06f * ease;
        canvas.scale(scale, scale, cx, cy);

        tmpRect.set(overlayCardRect);
        tmpRect.inset(-dp(2), -dp(2));
        shapePaint.setColor(COLOR_ACCENT);
        shapePaint.setAlpha((int) (16 * a));
        canvas.drawRoundRect(tmpRect, dp(26), dp(26), shapePaint);

        shapePaint.setColor(COLOR_OVERLAY_CARD);
        shapePaint.setAlpha((int) (248 * a));
        canvas.drawRoundRect(overlayCardRect, dp(24), dp(24), shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1));
        shapePaint.setColor(COLOR_OVERLAY_BORDER);
        shapePaint.setAlpha(alpha);
        canvas.drawRoundRect(overlayCardRect, dp(24), dp(24), shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);

        // 顶部一小段强调线
        float lineW = dp(36);
        shapePaint.setColor(COLOR_ACCENT);
        shapePaint.setAlpha(alpha);
        canvas.drawRoundRect(cx - lineW / 2, overlayCardRect.top + dp(18),
                cx + lineW / 2, overlayCardRect.top + dp(21), dp(1.5f), dp(1.5f), shapePaint);

        float iconCy = overlayCardRect.top + dp(48);
        shapePaint.setColor(COLOR_ACCENT);
        shapePaint.setAlpha((int) (28 * a));
        canvas.drawCircle(cx, iconCy, dp(22), shapePaint);
        shapePaint.setAlpha(255);
        if (game.status == Game.Status.WON) {
            drawFlag(canvas, cx, iconCy - dp(1), dp(46), COLOR_ACCENT);
        } else {
            drawMineIcon(canvas, cx, iconCy, dp(9), COLOR_DANGER);
        }

        boolean won = game.status == Game.Status.WON;
        useText(19f, COLOR_TEXT, sansBold, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText(won ? "扫雷成功" : "踩到雷了", cx, overlayCardRect.top + dp(94),
                textPaint);

        useText(9.5f, COLOR_TEXT_MUTED, sansMedium, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText("用时", cx, overlayCardRect.top + dp(118), textPaint);

        useText(28f, COLOR_ACCENT, mono, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText(formatTime(winSeconds), cx, baseline(overlayCardRect.top + dp(145),
                textPaint), textPaint);

        String bestLine;
        if (won) {
            bestLine = winNewRecord ? "新纪录！"
                    : "最佳 " + formatTime(bestSeconds[game.level.ordinal()]);
        } else {
            bestLine = bestSeconds[game.level.ordinal()] == 0 ? "最佳 --:--"
                    : "最佳 " + formatTime(bestSeconds[game.level.ordinal()]);
        }
        useText(11f, won && winNewRecord ? COLOR_WIN : COLOR_TEXT_SECONDARY,
                sansMedium, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText(bestLine, cx, baseline(overlayCardRect.top + dp(174), textPaint),
                textPaint);

        shapePaint.setAlpha(alpha);
        shapePaint.setColor(COLOR_ACCENT);
        canvas.drawRoundRect(overlayPrimary, dp(14), dp(14), shapePaint);
        useText(13.5f, COLOR_ACCENT_DARK, sansBold, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText("再来一局", overlayPrimary.centerX(),
                baseline(overlayPrimary.centerY(), textPaint), textPaint);

        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1.2f));
        shapePaint.setColor(0xFF35507A);
        shapePaint.setAlpha(alpha);
        canvas.drawRoundRect(overlaySecondary, dp(14), dp(14), shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        useText(13f, 0xFFC9D6E9, sansMedium, Paint.Align.CENTER);
        textPaint.setAlpha(alpha);
        canvas.drawText("看棋盘", overlaySecondary.centerX(),
                baseline(overlaySecondary.centerY(), textPaint), textPaint);

        textPaint.setAlpha(255);
        shapePaint.setAlpha(255);
        canvas.restore();

        if (a < 1f) {
            postInvalidateOnAnimation();
        }
    }

    private boolean overlayVisible() {
        return game.finished() && !overlayDismissed;
    }

    // ---- 小图标 ----

    private void drawFlag(Canvas canvas, float cx, float cy, float s, int clothColor) {
        float poleX = cx + s * 0.14f;
        float top = cy - s * 0.34f;
        float bottom = cy + s * 0.28f;
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStrokeWidth(Math.max(dp(1.2f), s * 0.075f));
        shapePaint.setColor(0xFFE7EEF9);
        canvas.drawLine(poleX, top, poleX, bottom, shapePaint);
        float baseHalf = s * 0.24f;
        shapePaint.setStrokeWidth(Math.max(dp(1.4f), s * 0.1f));
        canvas.drawLine(cx - baseHalf, cy + s * 0.3f, cx + baseHalf + s * 0.06f,
                cy + s * 0.3f, shapePaint);
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        shapePaint.setStrokeWidth(1f);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(clothColor);
        flagPath.reset();
        flagPath.moveTo(poleX, top);
        flagPath.lineTo(cx - s * 0.3f, cy - s * 0.19f);
        flagPath.lineTo(poleX, cy - s * 0.04f);
        flagPath.close();
        canvas.drawPath(flagPath, shapePaint);
    }

    private void drawCross(Canvas canvas, float cx, float cy, float s) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStrokeWidth(Math.max(dp(1.3f), s * 0.09f));
        shapePaint.setColor(COLOR_DANGER);
        float d = s * 0.24f;
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, shapePaint);
        canvas.drawLine(cx - d, cy + d, cx + d, cy - d, shapePaint);
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        shapePaint.setStyle(Paint.Style.FILL);
    }

    private void drawMineIcon(Canvas canvas, float cx, float cy, float bodyR, int color) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setStrokeWidth(Math.max(dp(1f), bodyR * 0.3f));
        shapePaint.setColor(color);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.drawLine(cx + cos * bodyR * 0.9f, cy + sin * bodyR * 0.9f,
                    cx + cos * bodyR * 1.65f, cy + sin * bodyR * 1.65f, shapePaint);
        }
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(color);
        canvas.drawCircle(cx, cy, bodyR, shapePaint);
        shapePaint.setColor(0x66FFFFFF);
        canvas.drawCircle(cx - bodyR * 0.32f, cy - bodyR * 0.32f, bodyR * 0.3f, shapePaint);
    }

    private void drawClockIcon(Canvas canvas, float cx, float cy, float r) {
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(dp(1.3f));
        shapePaint.setStrokeCap(Paint.Cap.ROUND);
        shapePaint.setColor(COLOR_TEXT_MUTED);
        canvas.drawCircle(cx, cy, r, shapePaint);
        canvas.drawLine(cx, cy, cx, cy - r * 0.55f, shapePaint);
        canvas.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.12f, shapePaint);
        shapePaint.setStrokeCap(Paint.Cap.BUTT);
        shapePaint.setStyle(Paint.Style.FILL);
    }

    // ---- 彩纸 ----

    private void spawnConfetti() {
        particles.clear();
        long now = SystemClock.uptimeMillis();
        float w = getWidth();
        for (int i = 0; i < 80; i++) {
            Particle p = new Particle();
            p.x = w / 2f + (random.nextFloat() - 0.5f) * dp(60);
            p.y = getHeight() * 0.4f;
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * 1.9;
            float speed = dp(160) + random.nextFloat() * dp(300);
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.size = dp(2.5f) + random.nextFloat() * dp(4);
            p.rot = random.nextFloat() * 360f;
            p.vr = (random.nextFloat() - 0.5f) * 720f;
            p.color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            p.bornAt = now;
            particles.add(p);
        }
        lastFrameAt = now;
        postInvalidateOnAnimation();
    }

    private void drawParticles(Canvas canvas, long now) {
        if (particles.isEmpty()) {
            lastFrameAt = now;
            return;
        }
        float dt = lastFrameAt == 0 ? 0f
                : Math.min(0.033f, (now - lastFrameAt) / 1000f);
        lastFrameAt = now;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            long age = now - p.bornAt;
            if (age > 2600 || p.y > getHeight() + dp(40)) {
                it.remove();
                continue;
            }
            p.vy += dp(1100) * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vr * dt;
            float alpha = age > 1900 ? 1f - (age - 1900) / 700f : 1f;
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rot);
            shapePaint.setColor(p.color);
            shapePaint.setAlpha((int) (255 * alpha));
            canvas.drawRect(-p.size / 2f, -p.size / 4f, p.size / 2f, p.size / 4f, shapePaint);
            canvas.restore();
        }
        shapePaint.setAlpha(255);
        if (!particles.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float rot;
        float vr;
        int color;
        long bornAt;
    }

    // ---- 交互 ----

    private void doTap(int idx) {
        if (game.finished() || game.state[idx] == Game.FLAGGED) {
            return;
        }
        Game.Status before = game.status;
        game.tap(idx);
        Game.Status after = game.status;

        if (before == Game.Status.READY && after == Game.Status.PLAYING) {
            startTicker();
        }
        if (after == Game.Status.WON && before != Game.Status.WON) {
            onWin();
        } else if (after == Game.Status.LOST && before != Game.Status.LOST) {
            onLose();
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        invalidate();
    }

    private void onWin() {
        winSeconds = Math.max(1, game.elapsedSeconds());
        Game.Level level = game.level;
        int stored = bestSeconds[level.ordinal()];
        winNewRecord = stored == 0 || winSeconds < stored;
        if (winNewRecord) {
            bestSeconds[level.ordinal()] = winSeconds;
            prefs.edit().putInt(bestKey(level), winSeconds).apply();
        }
        overlayStartedAt = SystemClock.uptimeMillis();
        overlayDismissed = false;
        spawnConfetti();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        postInvalidateOnAnimation();
    }

    private void onLose() {
        winSeconds = game.elapsedSeconds();
        overlayStartedAt = SystemClock.uptimeMillis();
        overlayDismissed = false;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        postInvalidateOnAnimation();
    }

    private void newGame() {
        handler.removeCallbacks(ticker);
        game = new Game(Game.Level.values()[selectedLevel]);
        overlayDismissed = false;
        overlayStartedAt = 0;
        winNewRecord = false;
        winSeconds = 0;
        particles.clear();
        pressedCell = -1;
        facePressed = false;
        computeLayout();
        invalidate();
    }

    private void selectLevel(int level) {
        if (level != selectedLevel) {
            selectedLevel = level;
            prefs.edit().putInt("level", level).apply();
        }
        newGame();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void handleTap(float x, float y) {
        if (overlayVisible()) {
            if (overlayPrimary.contains(x, y)) {
                newGame();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else if (overlaySecondary.contains(x, y)) {
                overlayDismissed = true;
                invalidate();
            }
            return;
        }
        if (faceRect.contains(x, y)) {
            newGame();
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            return;
        }
        for (int i = 0; i < segRects.length; i++) {
            if (segRects[i].contains(x, y)) {
                selectLevel(i);
                return;
            }
        }
        int idx = cellAt(x, y);
        if (idx >= 0) {
            doTap(idx);
        }
    }

    private void handleLongPress(float x, float y) {
        if (overlayVisible()) {
            return;
        }
        int idx = cellAt(x, y);
        if (idx < 0) {
            return;
        }
        byte before = game.state[idx];
        game.toggleFlag(idx);
        if (game.state[idx] != before) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }
    }

    private int cellAt(float x, float y) {
        if (boardInner.isEmpty()) {
            return -1;
        }
        if (x < boardInner.left || x >= boardInner.right
                || y < boardInner.top || y >= boardInner.bottom) {
            return -1;
        }
        int c = (int) ((x - boardInner.left) / cell);
        int r = (int) ((y - boardInner.top) / cell);
        if (c < 0 || c >= game.cols || r < 0 || r >= game.rows) {
            return -1;
        }
        return r * game.cols + c;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!overlayVisible()) {
                facePressed = faceRect.contains(event.getX(), event.getY());
                pressedCell = cellAt(event.getX(), event.getY());
                invalidate();
            }
        }
        detector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            facePressed = false;
            pressedCell = -1;
            invalidate();
        }
        return true;
    }

    private class Gestures extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            handleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            handleLongPress(e.getX(), e.getY());
        }
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        if (game.status == Game.Status.PLAYING) {
            handler.postDelayed(ticker, 200);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (game.status == Game.Status.PLAYING) {
            startTicker();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            if (game.status == Game.Status.PLAYING) {
                startTicker();
            }
        } else {
            handler.removeCallbacks(ticker);
        }
    }

    /** 几个不带颜色语义的常量放在这里，方便统一调整。 */
    private static final class ColorUtil {
        static final int DIVIDER = 0xFF1C2B44;
    }
}
