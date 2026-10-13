package com.sapio.progress;

/** Reaching level n needs 50·n·(n−1) XP in total: L2 = 100, L3 = 300, L4 = 600, L5 = 1000, … */
public final class LevelCurve {

    private LevelCurve() {
    }

    public static int levelFor(long xp) {
        int level = 1;
        while (xpToReach(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    public static long xpToReach(int level) {
        return 50L * level * (level - 1);
    }
}
