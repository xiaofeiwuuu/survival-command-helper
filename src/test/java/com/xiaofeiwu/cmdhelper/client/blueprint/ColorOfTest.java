package com.xiaofeiwu.cmdhelper.client.blueprint;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorOfTest {

    @Test
    void theSameIdAlwaysGivesTheSameColour() {
        assertArrayEquals(ColorOf.rgb("minecraft:stone"), ColorOf.rgb("minecraft:stone"));
    }

    @Test
    void channelsStayInRange() {
        for (String id : new String[]{"minecraft:stone", "minecraft:oak_stairs", "abridged:bridge_block", "x:y", ""}) {
            for (float c : ColorOf.rgb(id)) {
                assertTrue(c >= 0f && c <= 1f, id + " -> " + c);
            }
        }
    }

    @Test
    void differentBlocksMostlyGetDifferentColours() {
        Set<String> seen = new HashSet<>();
        String[] ids = {"minecraft:stone", "minecraft:dirt", "minecraft:oak_planks", "minecraft:glass", "minecraft:bricks",
                "minecraft:sand", "minecraft:obsidian", "minecraft:gold_block", "minecraft:lapis_block", "minecraft:white_wool"};
        for (String id : ids) {
            float[] c = ColorOf.rgb(id);
            seen.add(Math.round(c[0] * 20) + "," + Math.round(c[1] * 20) + "," + Math.round(c[2] * 20));
        }
        assertTrue(seen.size() >= 7, "only " + seen.size() + " distinct colours for 10 blocks");
    }
}
