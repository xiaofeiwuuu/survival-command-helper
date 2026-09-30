package com.xiaofeiwu.cmdhelper.client.hostile;

import com.xiaofeiwu.cmdhelper.client.hostile.HostileScan.Found;
import com.xiaofeiwu.cmdhelper.client.hostile.HostileScan.Sighting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostileScanTest {

    private static final List<String> TYPES = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:creeper");

    private static Sighting at(String type, double blocks) {
        return new Sighting(type, blocks * blocks);
    }

    @Test
    void countsOnlyTypesOnTheList_inListOrder() {
        List<Found> found = HostileScan.count(List.of(
                at("minecraft:creeper", 3), at("minecraft:zombie", 5), at("minecraft:zombie", 6),
                at("minecraft:cow", 2)), TYPES, 10);
        assertEquals(List.of(new Found("minecraft:zombie", 2), new Found("minecraft:creeper", 1)), found);
    }

    @Test
    void typesNotNearbyAreNotReported() {
        // The whole point: a type on the list that isn't around must not produce a command.
        List<Found> found = HostileScan.count(List.of(at("minecraft:zombie", 4)), TYPES, 10);
        assertEquals(List.of(new Found("minecraft:zombie", 1)), found);
    }

    @Test
    void rangeIsInclusive_likeServerDistanceSelector() {
        assertEquals(1, HostileScan.count(List.of(at("minecraft:zombie", 10)), TYPES, 10).size());
        assertEquals(0, HostileScan.count(List.of(at("minecraft:zombie", 10.01)), TYPES, 10).size());
    }

    @Test
    void nothingAround_givesEmptyResult() {
        assertTrue(HostileScan.count(List.of(), TYPES, 10).isEmpty());
    }

    @Test
    void commands_areOnePerFoundType_withTheRange() {
        List<Found> found = List.of(new Found("minecraft:zombie", 2), new Found("minecraft:creeper", 1));
        assertEquals(List.of(
                "kill @e[type=minecraft:zombie,distance=..10]",
                "kill @e[type=minecraft:creeper,distance=..10]"),
                HostileScan.commands(found, Set.of(), 10));
    }

    @Test
    void commands_skipTypesTheePlayerUntickedForThisRun() {
        List<Found> found = List.of(new Found("minecraft:zombie", 2), new Found("minecraft:enderman", 1));
        assertEquals(List.of("kill @e[type=minecraft:zombie,distance=..20]"),
                HostileScan.commands(found, Set.of("minecraft:enderman"), 20));
    }

    @Test
    void commands_noneWhenEverythingSkipped() {
        assertTrue(HostileScan.commands(List.of(new Found("minecraft:zombie", 1)),
                Set.of("minecraft:zombie"), 10).isEmpty());
    }
}
