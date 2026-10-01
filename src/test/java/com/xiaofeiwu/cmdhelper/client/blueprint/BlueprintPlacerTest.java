package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.PaletteEntry;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Pass;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Plan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintPlacerTest {

    private static final PaletteEntry AIR = new PaletteEntry("minecraft:air", true, Pass.CLEAR);
    private static final PaletteEntry STONE = new PaletteEntry("minecraft:stone", true, Pass.SOLID);
    private static final PaletteEntry TORCH = new PaletteEntry("minecraft:wall_torch[facing=north]", true, Pass.ATTACHED);
    private static final PaletteEntry MODDED_MISSING = new PaletteEntry("abridged:bridge_block", false, Pass.SOLID);

    private static Blueprint blueprint(int sx, int sy, int sz, List<String> palette, Cuboid... boxes) {
        return new Blueprint("t", sx, sy, sz, palette, List.of(boxes), 0);
    }

    private static List<PaletteEntry> palette(PaletteEntry... entries) {
        return List.of(entries);
    }

    @Test
    void oneBoxBecomesOneFillAtTheRightWorldPosition() {
        Blueprint bp = blueprint(3, 2, 4, List.of("minecraft:stone"), new Cuboid(0, 0, 0, 2, 1, 3, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 100, 64, -50, palette(STONE), true);
        assertEquals(List.of("fill 100 64 -50 102 65 -47 minecraft:stone"), plan.commands());
    }

    @Test
    void aSingleBlockIsAOneBlockFill() {
        Blueprint bp = blueprint(2, 2, 2, List.of("minecraft:stone"), new Cuboid(1, 1, 1, 1, 1, 1, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(STONE), true);
        assertEquals(List.of("fill 1 1 1 1 1 1 minecraft:stone"), plan.commands());
    }

    @Test
    void aMissingModBlock_producesNoCommand_andIsReportedAsSkipped() {
        Blueprint bp = blueprint(4, 1, 1, List.of("minecraft:stone", "abridged:bridge_block"),
                new Cuboid(0, 0, 0, 1, 0, 0, 0), new Cuboid(2, 0, 0, 3, 0, 0, 1));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(STONE, MODDED_MISSING), true);
        assertEquals(1, plan.commands().size());
        assertFalse(plan.commands().get(0).contains("abridged"));
        assertEquals(1, plan.stats().missingCuboids());
        assertEquals(2, plan.stats().missingBlocks());
        assertTrue(plan.placed().stream().anyMatch(p -> p.skipped() && p.reason().contains("abridged:bridge_block")));
    }

    // ---- air -----------------------------------------------------------------------------------------

    @Test
    void airIsFilledWhenTheSwitchIsOn() {
        Blueprint bp = blueprint(3, 1, 1, List.of("minecraft:air", "minecraft:stone"),
                new Cuboid(0, 0, 0, 0, 0, 0, 1), new Cuboid(1, 0, 0, 2, 0, 0, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(AIR, STONE), true);
        assertTrue(plan.commands().contains("fill 1 0 0 2 0 0 minecraft:air"));
    }

    @Test
    void airIsLeftAloneWhenTheSwitchIsOff() {
        Blueprint bp = blueprint(3, 1, 1, List.of("minecraft:air", "minecraft:stone"),
                new Cuboid(0, 0, 0, 0, 0, 0, 1), new Cuboid(1, 0, 0, 2, 0, 0, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(AIR, STONE), false);
        assertEquals(List.of("fill 0 0 0 0 0 0 minecraft:stone"), plan.commands());
    }

    // ---- order ---------------------------------------------------------------------------------------

    @Test
    void airFirst_thenSolidBottomUp_thenAttachedThingsLast() {
        Blueprint bp = blueprint(1, 4, 1, List.of("minecraft:air", "minecraft:stone", "minecraft:wall_torch[facing=north]"),
                new Cuboid(0, 3, 0, 0, 3, 0, 1),   // stone high up
                new Cuboid(0, 0, 0, 0, 0, 0, 2),   // torch, but listed early and low
                new Cuboid(0, 1, 0, 0, 1, 0, 1),   // stone lower
                new Cuboid(0, 2, 0, 0, 2, 0, 0));  // air
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(AIR, STONE, TORCH), true);
        assertEquals(List.of(
                "fill 0 2 0 0 2 0 minecraft:air",
                "fill 0 1 0 0 1 0 minecraft:stone",
                "fill 0 3 0 0 3 0 minecraft:stone",
                "fill 0 0 0 0 0 0 minecraft:wall_torch[facing=north]"), plan.commands());
    }

    // ---- size limits ---------------------------------------------------------------------------------

    @Test
    void aBoxOverTheFillLimit_isSplit_andNoPieceIsOverTheLimit() {
        Blueprint bp = blueprint(40, 40, 40, List.of("minecraft:stone"), new Cuboid(0, 0, 0, 39, 39, 39, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(STONE), true);
        assertTrue(plan.commands().size() > 1);
        long total = 0;
        for (Cuboid piece : BlueprintPlacer.split(new Cuboid(0, 0, 0, 39, 39, 39, 0), BlueprintPlacer.FILL_LIMIT)) {
            assertTrue(piece.volume() <= BlueprintPlacer.FILL_LIMIT);
            total += piece.volume();
        }
        assertEquals(40L * 40 * 40, total);
    }

    @Test
    void aBoxAtExactlyTheLimit_isNotSplit() {
        assertEquals(1, BlueprintPlacer.split(new Cuboid(0, 0, 0, 31, 31, 31, 0), BlueprintPlacer.FILL_LIMIT).size());
    }

    @Test
    void aCommandOverTheLengthLimit_isSkippedWithAReason_notSentTruncated() {
        String longState = "mymod:thing[" + "property_number_one=value_one,".repeat(10) + "last=1]";
        PaletteEntry tooLong = new PaletteEntry(longState, true, Pass.SOLID);
        Blueprint bp = blueprint(1, 1, 1, List.of(longState), new Cuboid(0, 0, 0, 0, 0, 0, 0));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(tooLong), true);
        assertTrue(plan.commands().isEmpty());
        assertEquals(1, plan.stats().tooLongCuboids());
        assertTrue(plan.placed().get(0).reason().contains("太长"));
    }

    @Test
    void everyCommandFitsTheLimit() {
        Blueprint bp = blueprint(40, 40, 40, List.of("minecraft:stone"), new Cuboid(0, 0, 0, 39, 39, 39, 0));
        for (String command : BlueprintPlacer.plan(bp, Transform.NONE, -30000000, 0, -30000000, palette(STONE), true).commands()) {
            assertTrue(command.length() <= BlueprintPlacer.MAX_COMMAND_LENGTH, command);
        }
    }

    // ---- rotation ------------------------------------------------------------------------------------

    @Test
    void aRotatedBlueprint_isPlacedWithItsSwappedFootprint() {
        Blueprint bp = blueprint(4, 1, 7, List.of("minecraft:stone"), new Cuboid(0, 0, 0, 3, 0, 6, 0)); // 4 wide, 7 long
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE.rotatedClockwise(), 10, 5, 20, palette(STONE), true);
        assertEquals(List.of("fill 10 5 20 16 5 23 minecraft:stone"), plan.commands()); // now 7 wide, 4 long
    }

    @Test
    void centredMin_putsTheAnchorInTheMiddleOfTheFootprint() {
        int[] min = BlueprintPlacer.centredMin(100, 64, 200, 10, 12);
        assertEquals(95, min[0]);
        assertEquals(64, min[1]);
        assertEquals(194, min[2]);
    }

    @Test
    void statsCountWhatWasBuilt() {
        Blueprint bp = blueprint(5, 1, 1, List.of("minecraft:stone", "abridged:bridge_block"),
                new Cuboid(0, 0, 0, 2, 0, 0, 0), new Cuboid(3, 0, 0, 4, 0, 0, 1));
        Plan plan = BlueprintPlacer.plan(bp, Transform.NONE, 0, 0, 0, palette(STONE, MODDED_MISSING), true);
        assertEquals(3, plan.stats().placedBlocks());
        assertEquals(1, plan.stats().placedCuboids());
        assertEquals(1, plan.stats().commandCount());
    }
}
