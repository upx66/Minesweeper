package com.dsh.minesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class GameTest {

    private static int countMines(Game g) {
        int n = 0;
        for (int i = 0; i < g.cellCount; i++) {
            if (g.isMine[i]) {
                n++;
            }
        }
        return n;
    }

    private static int firstMine(Game g) {
        for (int i = 0; i < g.cellCount; i++) {
            if (g.isMine[i]) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void firstTapIsSafeAndOpensAnArea() {
        for (Game.Level level : Game.Level.values()) {
            for (int seed = 0; seed < 150; seed++) {
                Game g = new Game(level, new Random(seed));
                int tap = (seed * 37 + 11) % g.cellCount;
                g.tap(tap);
                assertEquals(Game.Status.PLAYING, g.status);
                assertFalse("第一下不能是雷", g.isMine[tap]);
                assertEquals(Game.REVEALED, g.state[tap]);
                assertEquals("第一下周围必须是空的", 0, g.around[tap]);
                int tapRow = tap / g.cols;
                int tapCol = tap % g.cols;
                int neighbourhood = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (g.inside(tapRow + dr, tapCol + dc)) {
                            neighbourhood++;
                            assertEquals("第一下周围的安全格要全部翻开", Game.REVEALED,
                                    g.state[g.index(tapRow + dr, tapCol + dc)]);
                        }
                    }
                }
                assertTrue("第一下至少展开 " + neighbourhood + " 格",
                        g.revealedCount >= neighbourhood);
                assertEquals(level.mines, countMines(g));
                assertEquals(0, g.flaggedCount);
            }
        }
    }

    @Test
    public void steppingOnAMineLosesAndShowsTheBoard() {
        Game g = new Game(Game.Level.MEDIUM, new Random(7));
        g.tap(0);
        int mine = firstMine(g);
        assertTrue(mine >= 0);
        g.tap(mine);
        assertEquals(Game.Status.LOST, g.status);
        assertEquals(mine, g.explodedIndex);
        for (int i = 0; i < g.cellCount; i++) {
            if (g.isMine[i]) {
                assertNotEquals("输棋后雷要亮出来", Game.HIDDEN, g.state[i]);
            }
        }
        assertTrue(g.elapsedSeconds() >= 0);
    }

    @Test
    public void revealingEverySafeCellWinsAndAutoFlags() {
        Game g = new Game(Game.Level.EASY, new Random(3));
        g.tap(40);
        for (int i = 0; i < g.cellCount; i++) {
            if (!g.isMine[i]) {
                g.tap(i);
            }
        }
        assertEquals(Game.Status.WON, g.status);
        assertEquals(0, g.remainingMines());
        assertEquals(g.mineCount, g.flaggedCount);
        for (int i = 0; i < g.cellCount; i++) {
            if (g.isMine[i]) {
                assertEquals(Game.FLAGGED, g.state[i]);
            } else {
                assertEquals(Game.REVEALED, g.state[i]);
            }
        }
    }

    @Test
    public void flagsToggleAndBlockReveal() {
        Game g = new Game(Game.Level.EASY, new Random(1));
        g.toggleFlag(0);
        assertEquals(Game.FLAGGED, g.state[0]);
        assertEquals(g.mineCount - 1, g.remainingMines());
        g.tap(0);
        assertEquals("插旗的格子点不开", Game.FLAGGED, g.state[0]);
        assertEquals(Game.Status.READY, g.status);
        g.toggleFlag(0);
        assertEquals(Game.HIDDEN, g.state[0]);
        assertEquals(g.mineCount, g.remainingMines());
    }

    @Test
    public void chordOpensNeighboursWhenFlagsMatch() {
        boolean tested = false;
        for (int seed = 0; seed < 80; seed++) {
            Game g = new Game(Game.Level.MEDIUM, new Random(seed));
            g.tap(g.cellCount / 2);
            for (int i = 0; i < g.cellCount; i++) {
                if (g.state[i] != Game.REVEALED || g.around[i] <= 0) {
                    continue;
                }
                int r = i / g.cols;
                int c = i % g.cols;
                int hidden = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        int nr = r + dr;
                        int nc = c + dc;
                        if (g.inside(nr, nc) && g.state[g.index(nr, nc)] == Game.HIDDEN) {
                            hidden++;
                        }
                    }
                }
                if (hidden < g.around[i]) {
                    continue;
                }
                // 把这一圈里所有的雷都插上旗，再点数字快速展开。
                int flagged = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (!g.inside(nr, nc)) {
                            continue;
                        }
                        int n = g.index(nr, nc);
                        if (g.isMine[n] && g.state[n] == Game.HIDDEN) {
                            g.toggleFlag(n);
                            flagged++;
                        }
                    }
                }
                assertEquals(g.around[i], flagged);
                g.tap(i);
                assertNotEquals("插旗正确时不该踩雷", Game.Status.LOST, g.status);
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (!g.inside(nr, nc)) {
                            continue;
                        }
                        int n = g.index(nr, nc);
                        assertNotEquals(Game.HIDDEN, g.state[n]);
                    }
                }
                tested = true;
                break;
            }
            if (tested) {
                break;
            }
        }
        assertTrue("应该至少找到一次可以快速展开的局面", tested);
    }
}
