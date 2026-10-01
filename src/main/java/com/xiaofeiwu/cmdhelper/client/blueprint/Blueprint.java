package com.xiaofeiwu.cmdhelper.client.blueprint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A scanned building: its size, the distinct block states it uses (the palette), and the boxes of
 * identical blocks it is made of (positions local to the blueprint, 0-based). Air is a palette entry
 * like any other, so a blueprint can also clear space. Chests' contents and entities are not part of it.
 *
 * @param palette block states as text, e.g. "minecraft:oak_stairs[facing=north]" — only properties that
 *                differ from the block's default are written, to keep the fill commands short
 */
public record Blueprint(String name, int sizeX, int sizeY, int sizeZ, List<String> palette,
                        List<Cuboid> cuboids, long createdAtMillis) {

    public static final String AIR = "minecraft:air";

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    /** "minecraft:oak_stairs[facing=north]" -> "minecraft:oak_stairs". */
    public static String blockId(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    public static boolean isAir(String state) {
        String id = blockId(state);
        return id.equals(AIR) || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** Every mod namespace the blueprint uses, air excluded, in order of first appearance. */
    public Set<String> namespaces() {
        Set<String> result = new LinkedHashSet<>();
        for (String state : palette) {
            if (!isAir(state)) {
                String id = blockId(state);
                int colon = id.indexOf(':');
                result.add(colon < 0 ? "minecraft" : id.substring(0, colon));
            }
        }
        return result;
    }
}
