package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusLineTest {
    @Test
    public void keepsAShortLine() {
        assertEquals("24° Cloudy", StatusLine.clean("24° Cloudy"));
    }

    @Test
    public void nothingIsEmpty() {
        assertEquals("", StatusLine.clean(null));
        assertEquals("", StatusLine.clean("  \n\t "));
    }

    @Test
    public void foldsLinesAndDropsControlCharacters() {
        assertEquals("24° Cloudy now", StatusLine.clean("24°\nCloudy\u0007‮  now"));
    }

    @Test
    public void cutsToTheLimitWithoutSplittingACharacter() {
        String longText = times('x', StatusLine.MAX_CHARS - 1) + "🌧";   // the last one is two chars
        assertEquals(times('x', StatusLine.MAX_CHARS - 1), StatusLine.clean(longText));
        assertEquals(StatusLine.MAX_CHARS, StatusLine.clean(times('y', 100)).length());
    }

    @Test
    public void theDetailLineHasItsOwnLimit() {
        String detail = "Feels like 31° · Humidity 80% · Wind 9 km/h";
        assertEquals(detail, StatusLine.clean(detail, StatusLine.MAX_DETAIL_CHARS));
        assertEquals(StatusLine.MAX_DETAIL_CHARS, StatusLine.clean(times('z', 99), StatusLine.MAX_DETAIL_CHARS).length());
    }

    private static String times(char c, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }

    @Test
    public void expiry() {
        assertTrue(StatusLine.current(0, 1_000));
        assertTrue(StatusLine.current(2_000, 1_000));
        assertFalse(StatusLine.current(1_000, 1_000));
    }
}
