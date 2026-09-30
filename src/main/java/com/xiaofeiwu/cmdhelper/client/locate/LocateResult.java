package com.xiaofeiwu.cmdhelper.client.locate;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the position out of a /locate reply such as
 * "The nearest minecraft:ancient_city is at [-736, ~, -720] (1201 blocks away)".
 * Whatever the language, the position is the bracketed triple, so that is what gets parsed.
 * For structures Y is "~" — the server doesn't know a height — which is why {@link Position#y()} can be null.
 */
public final class LocateResult {

    private static final Pattern POSITION =
            Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(~|-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");

    private LocateResult() {
    }

    /** @param y null when the server reported "~" (height unknown) */
    public record Position(int x, Integer y, int z) {
    }

    public static Optional<Position> parse(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher m = POSITION.matcher(message);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            Integer y = m.group(2).equals("~") ? null : Integer.valueOf(m.group(2));
            return Optional.of(new Position(Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3))));
        } catch (NumberFormatException e) {
            return Optional.empty(); // absurdly large number: not a coordinate
        }
    }
}
