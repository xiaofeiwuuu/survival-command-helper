package com.xiaofeiwu.cmdhelper.client.teleport;

/**
 * The retry logic for "teleport onto the ground at X/Z", separate from the game so it can be tested.
 *
 * The server can find the ground for us, but refuses ("that position is not loaded") when the chunk
 * isn't loaded. The client can't tell reliably whether the server has a chunk (a chunk cache mod can
 * make the client believe it does), so the flow is driven by the server's answer instead:
 *
 * <ol>
 *   <li>the caller sends the surface teleport; if no "not loaded" refusal arrives within a moment, done;</li>
 *   <li>if it is refused: climb to the top of the world (which makes the server load the area), wait,
 *       and try the surface teleport again;</li>
 *   <li>if it is refused again, wait a little and retry, until a time limit.</li>
 * </ol>
 *
 * The caller reports refusals with {@link #reportRefusal()} and calls {@link #tick()} once per game
 * tick; tick says what to do next.
 */
public final class SurfaceTeleportFlow {

    public enum Action {
        NONE,
        /** Send the "go to the top of the world" teleport. */
        CLIMB,
        /** Send the surface teleport again (and watch for a refusal again). */
        ATTEMPT,
        /** No refusal came: the teleport worked. */
        SUCCESS,
        /** Ran out of time. */
        GIVE_UP
    }

    private enum Phase {WAIT_REPLY, WAIT_AFTER_CLIMB, WAIT_BEFORE_RETRY, FINISHED}

    /** How long to wait for a refusal before believing the teleport worked (a round trip to the server). */
    static final int REPLY_TICKS = 20;
    /** Time for the server to load the area after climbing. */
    static final int AFTER_CLIMB_TICKS = 20;
    static final int RETRY_DELAY_TICKS = 10;
    static final int TIMEOUT_TICKS = 20 * 15;

    private Phase phase = Phase.WAIT_REPLY;
    private int ticksInPhase;
    private int totalTicks;
    private boolean refused;
    private boolean climbed;

    /** The first surface teleport has just been sent by the caller. */
    public SurfaceTeleportFlow() {
    }

    public void reportRefusal() {
        if (phase == Phase.WAIT_REPLY) {
            refused = true;
        }
    }

    public boolean hasClimbed() {
        return climbed;
    }

    public Action tick() {
        if (phase == Phase.FINISHED) {
            return Action.NONE;
        }
        totalTicks++;
        ticksInPhase++;
        if (totalTicks > TIMEOUT_TICKS) {
            phase = Phase.FINISHED;
            return Action.GIVE_UP;
        }
        switch (phase) {
            case WAIT_REPLY -> {
                if (refused) {
                    refused = false;
                    ticksInPhase = 0;
                    if (!climbed) {
                        climbed = true;
                        phase = Phase.WAIT_AFTER_CLIMB;
                        return Action.CLIMB;
                    }
                    phase = Phase.WAIT_BEFORE_RETRY;
                    return Action.NONE;
                }
                if (ticksInPhase >= REPLY_TICKS) {
                    phase = Phase.FINISHED;
                    return Action.SUCCESS;
                }
            }
            case WAIT_AFTER_CLIMB -> {
                if (ticksInPhase >= AFTER_CLIMB_TICKS) {
                    phase = Phase.WAIT_REPLY;
                    ticksInPhase = 0;
                    return Action.ATTEMPT;
                }
            }
            case WAIT_BEFORE_RETRY -> {
                if (ticksInPhase >= RETRY_DELAY_TICKS) {
                    phase = Phase.WAIT_REPLY;
                    ticksInPhase = 0;
                    return Action.ATTEMPT;
                }
            }
            default -> {
            }
        }
        return Action.NONE;
    }
}
