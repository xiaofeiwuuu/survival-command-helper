package com.xiaofeiwu.cmdhelper.client.teleport;

import com.xiaofeiwu.cmdhelper.client.teleport.SurfaceTeleportFlow.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceTeleportFlowTest {

    /** Ticks until something other than NONE happens (or -1 after `limit` ticks). */
    private static int ticksUntilAction(SurfaceTeleportFlow flow, int limit, Action[] out) {
        for (int i = 1; i <= limit; i++) {
            Action a = flow.tick();
            if (a != Action.NONE) {
                out[0] = a;
                return i;
            }
        }
        return -1;
    }

    @Test
    void noRefusal_meansTheTeleportWorked_afterAWaitForTheServersReply() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        Action[] result = new Action[1];
        assertEquals(SurfaceTeleportFlow.REPLY_TICKS, ticksUntilAction(flow, 100, result));
        assertEquals(Action.SUCCESS, result[0]);
        assertFalse(flow.hasClimbed());
    }

    @Test
    void aRefusal_makesItClimbFirst_notRetryBlindly() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        flow.tick();
        flow.reportRefusal();          // "that position is not loaded"
        assertEquals(Action.CLIMB, flow.tick());
        assertTrue(flow.hasClimbed());
    }

    @Test
    void afterClimbing_itWaitsForTheServerToLoad_thenTriesTheSurfaceAgain() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        flow.reportRefusal();
        assertEquals(Action.CLIMB, flow.tick());
        Action[] result = new Action[1];
        assertEquals(SurfaceTeleportFlow.AFTER_CLIMB_TICKS, ticksUntilAction(flow, 100, result));
        assertEquals(Action.ATTEMPT, result[0]);
    }

    @Test
    void theSecondAttemptWorking_endsInSuccess_withoutClimbingAgain() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        flow.reportRefusal();
        flow.tick();                                            // CLIMB
        Action[] result = new Action[1];
        ticksUntilAction(flow, 100, result);                    // ATTEMPT
        assertEquals(Action.ATTEMPT, result[0]);
        assertEquals(SurfaceTeleportFlow.REPLY_TICKS, ticksUntilAction(flow, 100, result));
        assertEquals(Action.SUCCESS, result[0]);
    }

    @Test
    void aRefusedRetry_waitsAMomentThenTriesAgain_withoutClimbingAgain() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        flow.reportRefusal();
        flow.tick();                                            // CLIMB
        Action[] result = new Action[1];
        ticksUntilAction(flow, 100, result);                    // ATTEMPT
        flow.tick();
        flow.reportRefusal();                                   // still not loaded
        assertEquals(Action.NONE, flow.tick());                 // no second climb: the flow is now just waiting
        int waited = 0;                                         // the delay is counted from the tick that noticed the refusal
        Action action = Action.NONE;
        while (action == Action.NONE && waited < 100) {
            action = flow.tick();
            waited++;
        }
        assertEquals(Action.ATTEMPT, action);
        assertEquals(SurfaceTeleportFlow.RETRY_DELAY_TICKS, waited);
    }

    @Test
    void keepsRetryingUntilTheTimeLimit_thenGivesUp() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        boolean gaveUp = false;
        int climbs = 0;
        for (int i = 0; i < SurfaceTeleportFlow.TIMEOUT_TICKS + 50 && !gaveUp; i++) {
            flow.reportRefusal();      // the server refuses every single attempt
            Action a = flow.tick();
            if (a == Action.CLIMB) {
                climbs++;
            }
            gaveUp = a == Action.GIVE_UP;
        }
        assertTrue(gaveUp);
        assertEquals(1, climbs, "climbs once, not on every retry");
    }

    @Test
    void afterFinishing_nothingMoreHappens() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        Action[] result = new Action[1];
        ticksUntilAction(flow, 100, result);
        assertEquals(Action.SUCCESS, result[0]);
        for (int i = 0; i < 50; i++) {
            assertEquals(Action.NONE, flow.tick());
        }
    }

    @Test
    void aRefusalArrivingLateIsIgnored_onceTheWaitIsOver() {
        SurfaceTeleportFlow flow = new SurfaceTeleportFlow();
        Action[] result = new Action[1];
        ticksUntilAction(flow, 100, result);                    // SUCCESS
        flow.reportRefusal();
        assertEquals(Action.NONE, flow.tick());
    }
}
