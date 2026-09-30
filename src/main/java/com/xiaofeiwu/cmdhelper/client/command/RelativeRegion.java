package com.xiaofeiwu.cmdhelper.client.command;

/**
 * Turns "centered on me" / "N blocks in front of me" into the two absolute corners /fill
 * needs. Takes plain step vectors instead of a Minecraft Direction so it stays testable
 * without booting the game — the screen is the one that knows about Direction.
 */
public final class RelativeRegion {

    private RelativeRegion() {
    }

    public record Corners(String from, String to) {
    }

    /**
     * Lowest layer to fill. A player's Y is the block their feet are *in* (air, when standing),
     * so "the block under my feet" is one lower.
     */
    public static int baseY(int feetY, boolean includeFloor) {
        return includeFloor ? feetY - 1 : feetY;
    }

    public static Corners centered(int px, int py, int pz, int radius, int height) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        String from = (px - r) + " " + py + " " + (pz - r);
        String to = (px + r) + " " + (py + h - 1) + " " + (pz + r);
        return new Corners(from, to);
    }

    /**
     * @param facingStepX/Z the block player is facing (e.g. Direction.getStepX()/getStepZ())
     * @param rightStepX/Z  the direction 90° clockwise from facing, for the width axis
     * @param length        blocks extending forward, starting row included
     * @param width         blocks wide, centered on the player's lateral position
     * @param height        blocks tall, starting at the player's feet
     */
    public static Corners forward(int px, int py, int pz, int facingStepX, int facingStepZ,
                                   int rightStepX, int rightStepZ, int length, int width, int height) {
        int len = Math.max(1, length);
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        int leftHalf = w / 2;
        int rightHalf = w - 1 - leftHalf;

        int ax = px - rightStepX * leftHalf;
        int az = pz - rightStepZ * leftHalf;
        int bx = px + facingStepX * (len - 1) + rightStepX * rightHalf;
        int bz = pz + facingStepZ * (len - 1) + rightStepZ * rightHalf;

        String from = ax + " " + py + " " + az;
        String to = bx + " " + (py + h - 1) + " " + bz;
        return new Corners(from, to);
    }
}
