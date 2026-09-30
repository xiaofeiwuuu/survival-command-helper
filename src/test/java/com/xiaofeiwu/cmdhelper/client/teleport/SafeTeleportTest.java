package com.xiaofeiwu.cmdhelper.client.teleport;

import com.xiaofeiwu.cmdhelper.client.teleport.SafeTeleport.Plan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeTeleportTest {

    @Test
    void farAwayChunk_isLoadedFirstThenLandedOnTheGround() {
        // An unloaded chunk has no heightmap: asking for the surface there gives the lowest Y (bedrock).
        assertEquals(Plan.LOAD_THEN_SURFACE, SafeTeleport.decide(false, false));
    }

    @Test
    void loadedChunk_goesStraightToTheGround() {
        assertEquals(Plan.DIRECT_TO_SURFACE, SafeTeleport.decide(false, true));
    }

    @Test
    void dimensionWithACeiling_onlyMovesSideways_whetherLoadedOrNot() {
        assertEquals(Plan.KEEP_HEIGHT, SafeTeleport.decide(true, true));
        assertEquals(Plan.KEEP_HEIGHT, SafeTeleport.decide(true, false));
    }
}
