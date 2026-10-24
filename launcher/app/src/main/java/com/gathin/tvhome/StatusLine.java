package com.gathin.tvhome;

/**
 * The line another app supplies for under the clock (config.xml status_provider). It comes from outside the
 * launcher, so it is cut to one short line of printable text before it is shown.
 */
final class StatusLine {
    static final int MAX_CHARS = 32;
    /** The optional second line, shown only on the idle clock screen, where there is more room. */
    static final int MAX_DETAIL_CHARS = 48;

    private StatusLine() {}

    /** Printable text on one line, at most MAX_CHARS; empty when there is nothing to show. */
    static String clean(String text) {
        return clean(text, MAX_CHARS);
    }

    static String clean(String text, int max) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < max; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp)) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
            } else if (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT) {
                if (out.length() + Character.charCount(cp) > max) break;
                out.appendCodePoint(cp);
            }
        }
        return out.toString().trim();
    }

    /** False once the supplier's "expires" time (epoch ms) has passed; 0 or less means it never expires. */
    static boolean current(long expires, long now) {
        return expires <= 0 || now < expires;
    }
}
