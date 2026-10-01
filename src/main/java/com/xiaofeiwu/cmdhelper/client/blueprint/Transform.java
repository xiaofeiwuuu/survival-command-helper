package com.xiaofeiwu.cmdhelper.client.blueprint;

/**
 * Rotating and mirroring a blueprint, as pure geometry. Matches Minecraft's own structure transform so
 * the block states (done in game code with BlockState.mirror / rotate) turn the same way the boxes do:
 * the mirror is applied first (it flips X — FRONT_BACK), then the rotation, clockwise seen from above:
 * a clockwise quarter turn sends north to east.
 *
 * Coordinates are local to the blueprint (0 .. size-1 on each axis); after a transform they are local to
 * the transformed blueprint, whose X and Z sizes swap on an odd number of quarter turns.
 */
public record Transform(int quarterTurns, boolean mirrorX) {

    public static final Transform NONE = new Transform(0, false);

    public Transform {
        quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    public Transform rotatedClockwise() {
        return new Transform(quarterTurns + 1, mirrorX);
    }

    public Transform mirrored() {
        return new Transform(quarterTurns, !mirrorX);
    }

    public boolean isIdentity() {
        return quarterTurns == 0 && !mirrorX;
    }

    /** @return {sizeX, sizeZ} after the transform */
    public int[] horizontalSize(int sizeX, int sizeZ) {
        return quarterTurns % 2 == 0 ? new int[]{sizeX, sizeZ} : new int[]{sizeZ, sizeX};
    }

    public Cuboid apply(Cuboid c, int sizeX, int sizeZ) {
        int ax = c.x0(), bx = c.x1(), az = c.z0(), bz = c.z1();
        int sx = sizeX, sz = sizeZ;
        if (mirrorX) {
            int nx0 = sx - 1 - bx;
            int nx1 = sx - 1 - ax;
            ax = nx0;
            bx = nx1;
        }
        for (int i = 0; i < quarterTurns; i++) {
            // clockwise quarter turn: (x, z) -> (sz - 1 - z, x); the sizes swap
            int nax = sz - 1 - bz;
            int nbx = sz - 1 - az;
            int naz = ax;
            int nbz = bx;
            ax = nax;
            bx = nbx;
            az = naz;
            bz = nbz;
            int t = sx;
            sx = sz;
            sz = t;
        }
        return new Cuboid(ax, c.y0(), az, bx, c.y1(), bz, c.paletteIndex());
    }
}
