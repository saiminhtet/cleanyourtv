package io.github.saiminhtet.backlight;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LevelTest {
    @Test
    public void coarseStepsAboveTen() {
        assertEquals(95, Level.next(100, -1));
        assertEquals(15, Level.next(20, -1));
        assertEquals(10, Level.next(15, -1));
        assertEquals(15, Level.next(10, 1));
    }

    @Test
    public void fineStepsAtTenAndBelow() {
        assertEquals(9, Level.next(10, -1));
        assertEquals(6, Level.next(7, -1));
        assertEquals(8, Level.next(7, 1));
        assertEquals(10, Level.next(9, 1));
    }

    @Test
    public void neverBelowTheFloorOrAboveTheTop() {
        assertEquals(Level.MIN, Level.next(Level.MIN, -1));
        assertEquals(Level.MIN, Level.next(0, -1));
        assertEquals(Level.MAX, Level.next(100, 1));
        assertEquals(Level.MAX, Level.next(250, 1));
    }

    @Test
    public void aValueOffTheGridSnapsBackToIt() {
        assertEquals(40, Level.next(43, -1));
        assertEquals(45, Level.next(43, 1));
        assertEquals(10, Level.next(12, -1));
        assertEquals(15, Level.next(12, 1));
    }
}
