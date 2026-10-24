package io.github.saiminhtet.backlight;

/** The backlight steps, kept apart from Android so the JVM tests can check them. */
final class Level {
    /** Never lower: at 0 some panels go fully dark, and then the slider itself can't be seen to bring it back. */
    static final int MIN = 5;
    static final int MAX = 100;
    /** 5 at a time, then 1 at a time at 10 and below, where each step shows the most. */
    static final int STEP = 5;
    static final int FINE_STEP = 1;
    static final int FINE_BELOW = 10;

    private Level() {}

    /**
     * The value one press of ◀ (direction -1) or ▶ (+1) moves to. Above 10 it goes to the next multiple of 5, so a
     * value set in the TV's own menu (43) lands back on the grid (40 or 45); at 10 and below it moves by 1.
     */
    static int next(int value, int direction) {
        value = clamp(value);
        int moved;
        if (direction < 0) {
            moved = value > FINE_BELOW ? Math.max(FINE_BELOW, (value - 1) / STEP * STEP) : value - FINE_STEP;
        } else {
            moved = value >= FINE_BELOW ? (value / STEP + 1) * STEP : value + FINE_STEP;
        }
        return clamp(moved);
    }

    static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
