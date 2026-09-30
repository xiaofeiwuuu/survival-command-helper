package com.xiaofeiwu.cmdhelper.client.command;

import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.CloneMode;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.MaskMode;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The examples come straight from "clone 注意点.md". */
class CloneCalcTest {

    // Note §1: source -1267 73 -684 ~ -1258 78 -673
    private static final RegionBounds NOTE_SOURCE = RegionBounds.of("-1267 73 -684", "-1258 78 -673");

    @Test
    void sourceSizeIsDifferencePlusOne_onEveryAxis() {
        assertEquals(10, NOTE_SOURCE.sizeX());
        assertEquals(6, NOTE_SOURCE.sizeY());
        assertEquals(12, NOTE_SOURCE.sizeZ());
        assertEquals(720, NOTE_SOURCE.volume());
    }

    @Test
    void destinationHasExactlyTheSourceSize_noteSection3() {
        // dest start -1267 78 -684 -> copied area Y 78..83, X and Z unchanged
        Plan plan = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertEquals(new RegionBounds(-1267, 78, -684, -1258, 83, -673), plan.destination());
        assertEquals(plan.source().sizeX(), plan.destination().sizeX());
        assertEquals(plan.source().sizeY(), plan.destination().sizeY());
        assertEquals(plan.source().sizeZ(), plan.destination().sizeZ());
    }

    @Test
    void firstNoteCommand_isReproducedExactly() {
        Plan plan = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertTrue(plan.overlaps()); // Y 78 is in both areas
        assertEquals("clone -1267 73 -684 -1258 78 -673 -1267 78 -684 replace force",
                CloneCalc.command(plan, MaskMode.REPLACE, CloneMode.AUTO));
    }

    @Test
    void chainedSecondFloor_noteSection4() {
        RegionBounds secondSource = RegionBounds.of("-1267 78 -684", "-1258 83 -673");
        Plan plan = CloneCalc.plan(secondSource, -1267, 83, -684);
        assertEquals(new RegionBounds(-1267, 83, -684, -1258, 88, -673), plan.destination());
        assertEquals("clone -1267 78 -684 -1258 83 -673 -1267 83 -684 replace force",
                CloneCalc.command(plan, MaskMode.REPLACE, CloneMode.AUTO));
    }

    @Test
    void offsetEqualToHeightMinusOne_isTheNoteStackingCase() {
        Plan viaOffset = CloneCalc.planWithOffset(NOTE_SOURCE, 0, NOTE_SOURCE.sizeY() - 1, 0);
        assertEquals(CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684), viaOffset);
    }

    @Test
    void offsetEqualToHeight_stacksWithoutSharingALayer_soNoOverlap() {
        Plan plan = CloneCalc.planWithOffset(NOTE_SOURCE, 0, NOTE_SOURCE.sizeY(), 0);
        assertFalse(plan.overlaps());
        // no overlap and default masks: the shortest form, no trailing words
        assertEquals("clone -1267 73 -684 -1258 78 -673 -1267 79 -684",
                CloneCalc.command(plan, MaskMode.REPLACE, CloneMode.AUTO));
    }

    @Test
    void corners_canBeGivenInEitherOrder_andTheDestinationIsStillTheMinCorner() {
        // Same box, but "begin" is the max corner. The game takes the destination as the LOWEST corner
        // regardless, so the result must be identical — not shifted by the box size.
        RegionBounds reversed = RegionBounds.of("-1258 78 -673", "-1267 73 -684");
        assertEquals(NOTE_SOURCE, reversed);
        assertEquals(CloneCalc.command(CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684), MaskMode.REPLACE, CloneMode.AUTO),
                CloneCalc.command(CloneCalc.plan(reversed, -1267, 78, -684), MaskMode.REPLACE, CloneMode.AUTO));
    }

    @Test
    void negativeCoordinates_arePreservedNotNudged_noteSection5() {
        Plan plan = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertEquals("-1267 78 -684", plan.destinationStart());
        assertEquals(-1258, plan.source().maxX());
        assertEquals(-673, plan.source().maxZ());
    }

    // ---- moving by direction -----------------------------------------------------------------------

    @Test
    void directions_mapToTheGamesAxes() {
        assertEquals(java.util.List.of(5, 0, 0), toList(CloneCalc.directionalOffset(5, 0, 0, 0, 0, 0)));   // east  = +X
        assertEquals(java.util.List.of(0, 0, 5), toList(CloneCalc.directionalOffset(0, 5, 0, 0, 0, 0)));   // south = +Z
        assertEquals(java.util.List.of(-5, 0, 0), toList(CloneCalc.directionalOffset(0, 0, 5, 0, 0, 0)));  // west  = -X
        assertEquals(java.util.List.of(0, 0, -5), toList(CloneCalc.directionalOffset(0, 0, 0, 5, 0, 0)));  // north = -Z
        assertEquals(java.util.List.of(0, 5, 0), toList(CloneCalc.directionalOffset(0, 0, 0, 0, 5, 0)));   // up    = +Y
        assertEquals(java.util.List.of(0, -5, 0), toList(CloneCalc.directionalOffset(0, 0, 0, 0, 0, 5)));  // down  = -Y
    }

    @Test
    void oppositeDirectionsCancel_andSeveralAxesCombine() {
        assertEquals(java.util.List.of(3, 2, -4), toList(CloneCalc.directionalOffset(10, 1, 7, 5, 6, 4)));
    }

    @Test
    void moveUpBySourceHeightMinusOne_isTheNoteStackingCase() {
        int[] o = CloneCalc.directionalOffset(0, 0, 0, 0, NOTE_SOURCE.sizeY() - 1, 0);
        assertEquals(CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684), CloneCalc.planWithOffset(NOTE_SOURCE, o[0], o[1], o[2]));
    }

    @Test
    void moveEastBySourceWidth_sitsRightNextToTheSource_withoutOverlap() {
        int[] o = CloneCalc.directionalOffset(NOTE_SOURCE.sizeX(), 0, 0, 0, 0, 0);
        Plan plan = CloneCalc.planWithOffset(NOTE_SOURCE, o[0], o[1], o[2]);
        assertFalse(plan.overlaps());
        assertEquals(NOTE_SOURCE.maxX() + 1, plan.destination().minX());
    }

    private static java.util.List<Integer> toList(int[] a) {
        return java.util.List.of(a[0], a[1], a[2]);
    }

    // ---- overlap ---------------------------------------------------------------------------------

    @Test
    void touchingByOneLayerCountsAsOverlap() {
        assertTrue(new RegionBounds(0, 0, 0, 4, 4, 4).intersects(new RegionBounds(4, 4, 4, 8, 8, 8)));
        assertFalse(new RegionBounds(0, 0, 0, 4, 4, 4).intersects(new RegionBounds(5, 0, 0, 9, 4, 4)));
    }

    @Test
    void overlapIsPerAxis_allThreeMustOverlap() {
        RegionBounds a = new RegionBounds(0, 0, 0, 4, 4, 4);
        assertFalse(a.intersects(new RegionBounds(0, 5, 0, 4, 9, 4)));
        assertFalse(a.intersects(new RegionBounds(0, 0, 5, 4, 4, 9)));
    }

    // ---- options ---------------------------------------------------------------------------------

    @Test
    void options_autoAddsForceOnlyWhenOverlapping() {
        assertEquals("replace force", CloneCalc.options(MaskMode.REPLACE, CloneMode.AUTO, true));
        assertNull(CloneCalc.options(MaskMode.REPLACE, CloneMode.AUTO, false));
    }

    @Test
    void options_modeWordNeverAppearsWithoutTheMaskWord() {
        assertEquals("replace move", CloneCalc.options(MaskMode.REPLACE, CloneMode.MOVE, false));
        assertEquals("masked force", CloneCalc.options(MaskMode.MASKED, CloneMode.FORCE, false));
    }

    @Test
    void options_maskedAloneIsStillWritten() {
        assertEquals("masked", CloneCalc.options(MaskMode.MASKED, CloneMode.NORMAL, false));
        assertEquals("masked", CloneCalc.options(MaskMode.MASKED, CloneMode.AUTO, false));
    }

    // ---- warnings --------------------------------------------------------------------------------

    @Test
    void overlapInNormalMode_isFlaggedBecauseTheServerRejectsIt() {
        Plan plan = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertTrue(CloneCalc.warnings(plan, MaskMode.REPLACE, CloneMode.NORMAL).get(0).contains("拒绝"));
    }

    @Test
    void overlapWithAutoOrForce_saysOverlapIsBeingOverwritten() {
        Plan plan = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertTrue(CloneCalc.warnings(plan, MaskMode.REPLACE, CloneMode.AUTO).get(0).contains("force"));
    }

    @Test
    void moveClearsTheSource_andCannotOverlap() {
        Plan overlapping = CloneCalc.plan(NOTE_SOURCE, -1267, 78, -684);
        assertTrue(CloneCalc.warnings(overlapping, MaskMode.REPLACE, CloneMode.MOVE).stream().anyMatch(w -> w.contains("不允许") && w.contains("重叠")));
        Plan apart = CloneCalc.plan(NOTE_SOURCE, 0, 0, 0);
        assertTrue(CloneCalc.warnings(apart, MaskMode.REPLACE, CloneMode.MOVE).stream().anyMatch(w -> w.contains("清除源区域")));
    }

    @Test
    void tooManyBlocks_isFlagged() {
        RegionBounds big = RegionBounds.of("0 0 0", "40 40 40"); // 41^3 = 68921
        Plan plan = CloneCalc.plan(big, 100, 0, 0);
        assertTrue(CloneCalc.warnings(plan, MaskMode.REPLACE, CloneMode.AUTO).get(0).contains("上限"));
    }

    @Test
    void aPlainSafeCopy_hasNothingToWarnAbout() {
        Plan plan = CloneCalc.plan(NOTE_SOURCE, 0, 0, 0);
        assertTrue(CloneCalc.warnings(plan, MaskMode.REPLACE, CloneMode.AUTO).isEmpty());
        assertTrue(CloneCalc.warnings(plan, MaskMode.MASKED, CloneMode.AUTO).isEmpty());
    }
}
