package com.xiaofeiwu.cmdhelper.client.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls an X/Y/Z block position out of pasted text, whatever separates the numbers: spaces, English
 * or Chinese commas, 、 ； / brackets, "X: 10 Y: 64 Z: -5", the F3 screen's "10.123 / 64.0 / -5.5",
 * even "/tp @s 10 64 -5" — the numbers are what count. Full-width digits and minus signs (from a
 * Chinese input method) are understood too. Decimals are floored, since a block coordinate is the
 * block a point is inside (so -5.5 is block -6, not -5).
 *
 * Exactly three numbers are required: with fewer it is incomplete and with more (say a whole F3
 * line with block and chunk coordinates) there is no telling which three were meant.
 */
public final class CoordinateParser {

    /** Fits the coordinate text boxes' six-digit limit. */
    public static final int MAX_ABS = 999_999;

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private CoordinateParser() {
    }

    public record Coordinates(int x, int y, int z) {
    }

    public static Optional<Coordinates> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        List<Integer> values = new ArrayList<>(3);
        Matcher m = NUMBER.matcher(normalise(text));
        while (m.find()) {
            if (values.size() == 3) {
                return Optional.empty(); // a fourth number: ambiguous
            }
            double value;
            try {
                value = Double.parseDouble(m.group());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            double floored = Math.floor(value);
            if (Math.abs(floored) > MAX_ABS) {
                return Optional.empty();
            }
            values.add((int) floored);
        }
        return values.size() == 3
                ? Optional.of(new Coordinates(values.get(0), values.get(1), values.get(2)))
                : Optional.empty();
    }

    /** Full-width digits / point / minus and the various dash-like minus signs become plain ASCII. */
    private static String normalise(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= '０' && c <= '９') {
                out.append((char) ('0' + (c - '０')));
            } else if (c == '．' || c == '。') {
                out.append('.');
            } else if (c == '－' || c == '−' || c == '–') { // full-width hyphen-minus, U+2212 minus, en dash
                out.append('-');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
