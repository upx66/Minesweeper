package com.dsh.minesweeper;

import java.util.Random;

/**
 * 扫雷核心规则。纯 Java，不依赖 Android，方便单元测试。
 *
 * 坐标：index = row * cols + col。
 */
public final class Game {

    public enum Level {
        EASY("初级", 9, 9, 10),
        MEDIUM("中级", 12, 16, 30),
        HARD("高级", 16, 24, 80);

        public final String label;
        public final int cols;
        public final int rows;
        public final int mines;

        Level(String label, int cols, int rows, int mines) {
            this.label = label;
            this.cols = cols;
            this.rows = rows;
            this.mines = mines;
        }

        public String spec() {
            return cols + " × " + rows + " · " + mines + " 雷";
        }
    }

    public enum Status { READY, PLAYING, WON, LOST }

    public static final byte HIDDEN = 0;
    public static final byte REVEALED = 1;
    public static final byte FLAGGED = 2;

    public final Level level;
    public final int cols;
    public final int rows;
    public final int cellCount;
    public final int mineCount;

    public final boolean[] isMine;
    /** 周围雷数，只在非雷格上有意义。 */
    public final byte[] around;
    public final byte[] state;

    public Status status = Status.READY;
    public int revealedCount;
    public int flaggedCount;
    public int explodedIndex = -1;

    private long startedAt;
    private long endedAt;
    private final Random random;

    public Game(Level level) {
        this(level, new Random());
    }

    Game(Level level, Random random) {
        this.level = level;
        this.cols = level.cols;
        this.rows = level.rows;
        this.cellCount = cols * rows;
        this.mineCount = level.mines;
        this.random = random;
        this.isMine = new boolean[cellCount];
        this.around = new byte[cellCount];
        this.state = new byte[cellCount];
    }

    public boolean inside(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    public int index(int row, int col) {
        return row * cols + col;
    }

    public boolean finished() {
        return status == Status.WON || status == Status.LOST;
    }

    public boolean started() {
        return status == Status.PLAYING || finished();
    }

    public int remainingMines() {
        return mineCount - flaggedCount;
    }

    public long elapsedMs() {
        if (startedAt == 0L) {
            return 0L;
        }
        long end = endedAt != 0L ? endedAt : System.currentTimeMillis();
        return Math.max(0L, end - startedAt);
    }

    public int elapsedSeconds() {
        return (int) (elapsedMs() / 1000L);
    }

    /** 翻开一格；若点的是数字格则尝试快速展开。 */
    public void tap(int index) {
        if (finished() || index < 0 || index >= cellCount) {
            return;
        }
        if (state[index] == FLAGGED) {
            return;
        }
        if (state[index] == REVEALED) {
            chord(index);
            return;
        }
        if (status == Status.READY) {
            begin(index);
        }
        reveal(index);
    }

    /** 插旗 / 取消插旗，只能在未翻开的格子上操作。 */
    public void toggleFlag(int index) {
        if (finished() || index < 0 || index >= cellCount) {
            return;
        }
        if (state[index] == REVEALED) {
            return;
        }
        if (state[index] == FLAGGED) {
            state[index] = HIDDEN;
            flaggedCount--;
        } else {
            state[index] = FLAGGED;
            flaggedCount++;
        }
    }

    /** 开局：在安全区之外布雷，保证第一下不会踩雷且周围是空的。 */
    private void begin(int safeIndex) {
        placeMines(safeIndex);
        status = Status.PLAYING;
        startedAt = System.currentTimeMillis();
        endedAt = 0L;
    }

    private void placeMines(int safeIndex) {
        int safeRow = safeIndex / cols;
        int safeCol = safeIndex % cols;
        int[] candidates = new int[cellCount];
        int n = 0;
        for (int i = 0; i < cellCount; i++) {
            int r = i / cols;
            int c = i % cols;
            if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) {
                continue;
            }
            candidates[n++] = i;
        }
        // 极小棋盘兜底：只保证点的这一格安全。
        if (n < mineCount) {
            n = 0;
            for (int i = 0; i < cellCount; i++) {
                if (i != safeIndex) {
                    candidates[n++] = i;
                }
            }
        }
        for (int k = 0; k < mineCount; k++) {
            int j = k + random.nextInt(n - k);
            int tmp = candidates[k];
            candidates[k] = candidates[j];
            candidates[j] = tmp;
            isMine[candidates[k]] = true;
        }
        computeAround();
    }

    private void computeAround() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = index(r, c);
                if (isMine[i]) {
                    continue;
                }
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        int nr = r + dr;
                        int nc = c + dc;
                        if (inside(nr, nc) && isMine[index(nr, nc)]) {
                            count++;
                        }
                    }
                }
                around[i] = (byte) count;
            }
        }
    }

    private void reveal(int start) {
        if (state[start] != HIDDEN) {
            return;
        }
        if (isMine[start]) {
            state[start] = REVEALED;
            explodedIndex = start;
            finish(false);
            return;
        }
        floodReveal(start);
        checkWin();
    }

    private void floodReveal(int start) {
        int[] stack = new int[cellCount * 9];
        int sp = 0;
        stack[sp++] = start;
        while (sp > 0) {
            int i = stack[--sp];
            if (state[i] != HIDDEN) {
                continue;
            }
            state[i] = REVEALED;
            revealedCount++;
            if (around[i] != 0) {
                continue;
            }
            int r = i / cols;
            int c = i % cols;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) {
                        continue;
                    }
                    int nr = r + dr;
                    int nc = c + dc;
                    if (!inside(nr, nc)) {
                        continue;
                    }
                    int n = index(nr, nc);
                    if (state[n] == HIDDEN) {
                        stack[sp++] = n;
                    }
                }
            }
        }
    }

    /** 快速展开：数字格周围插满旗时，翻开其余相邻格。 */
    private void chord(int index) {
        if (status != Status.PLAYING || around[index] == 0) {
            return;
        }
        int r = index / cols;
        int c = index % cols;
        int flags = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (inside(nr, nc) && state[index(nr, nc)] == FLAGGED) {
                    flags++;
                }
            }
        }
        if (flags != around[index]) {
            return;
        }
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (!inside(nr, nc)) {
                    continue;
                }
                int n = index(nr, nc);
                if (state[n] == HIDDEN) {
                    reveal(n);
                    if (finished()) {
                        return;
                    }
                }
            }
        }
    }

    private void checkWin() {
        if (revealedCount == cellCount - mineCount) {
            finish(true);
        }
    }

    private void finish(boolean won) {
        if (finished()) {
            return;
        }
        status = won ? Status.WON : Status.LOST;
        endedAt = System.currentTimeMillis();
        if (won) {
            for (int i = 0; i < cellCount; i++) {
                if (isMine[i] && state[i] == HIDDEN) {
                    state[i] = FLAGGED;
                    flaggedCount++;
                }
            }
        } else {
            for (int i = 0; i < cellCount; i++) {
                if (isMine[i] && state[i] == HIDDEN) {
                    state[i] = REVEALED;
                }
            }
        }
    }
}
