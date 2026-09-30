package com.xiaofeiwu.cmdhelper.client.forceload;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the answer to {@code /forceload query}. The server sends it as one line of chat such as
 * "2 force loaded chunks were found in minecraft:overworld at: [0, 0], [3, -2]" — the positions are
 * <b>chunk</b> coordinates (vanilla prints a {@code ChunkPos}). The wording changes with the client's
 * language, but the "[x, z]" pairs never do, so those are what get parsed. No Minecraft classes here.
 */
public final class ForceLoadedChunks {

    private static final Pattern CHUNK = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");

    private ForceLoadedChunks() {
    }

    /** A chunk column, in chunk coordinates (block coordinate divided by 16, rounded down). */
    public record Chunk(int x, int z) {

        public int minBlockX() {
            return x * 16;
        }

        public int minBlockZ() {
            return z * 16;
        }

        /** A block inside this chunk near its middle: what to hand /tp and /forceload as a block position. */
        public int centerBlockX() {
            return minBlockX() + 8;
        }

        public int centerBlockZ() {
            return minBlockZ() + 8;
        }
    }

    /** All chunks named in the message, in the order given, without duplicates. Empty if none. */
    public static List<Chunk> parse(String message) {
        Set<Chunk> chunks = new LinkedHashSet<>();
        if (message != null) {
            Matcher m = CHUNK.matcher(message);
            while (m.find()) {
                try {
                    chunks.add(new Chunk(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                } catch (NumberFormatException ignored) {
                    // absurdly large number: not a chunk coordinate
                }
            }
        }
        return new ArrayList<>(chunks);
    }
}
