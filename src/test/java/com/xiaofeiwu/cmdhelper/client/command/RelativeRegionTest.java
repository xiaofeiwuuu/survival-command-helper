package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelativeRegionTest {

    @Test
    void centered_boxSpansRadiusInBothDirections() {
        RelativeRegion.Corners c = RelativeRegion.centered(0, 64, 0, 5, 3);
        assertEquals("-5 64 -5", c.from());
        assertEquals("5 66 5", c.to());
    }

    @Test
    void centered_zeroRadiusIsSingleColumn() {
        RelativeRegion.Corners c = RelativeRegion.centered(10, 70, -20, 0, 1);
        assertEquals("10 70 -20", c.from());
        assertEquals("10 70 -20", c.to());
    }

    @Test
    void forward_facingNorth_extendsInNegativeZ() {
        // Facing north (0,-1), right is east (1,0): "10 forward, 3 wide, height 3" from origin.
        RelativeRegion.Corners c = RelativeRegion.forward(0, 64, 0, 0, -1, 1, 0, 10, 3, 3);
        assertEquals("-1 64 0", c.from());
        assertEquals("1 66 -9", c.to());
    }

    @Test
    void forward_singleWideIsCenteredOnPlayer() {
        RelativeRegion.Corners c = RelativeRegion.forward(5, 10, 5, 1, 0, 0, -1, 4, 1, 1);
        assertEquals("5 10 5", c.from());
        assertEquals("8 10 5", c.to());
    }

    @Test
    void baseY_includeFloorStartsOneBelowFeet() {
        assertEquals(63, RelativeRegion.baseY(64, true));
        assertEquals(64, RelativeRegion.baseY(64, false));
    }

    @Test
    void centered_withFloor_heightOneFillsOnlyTheFloorLayer() {
        RelativeRegion.Corners c = RelativeRegion.centered(0, RelativeRegion.baseY(64, true), 0, 2, 1);
        assertEquals("-2 63 -2", c.from());
        assertEquals("2 63 2", c.to());
    }
}
