package com.xiaofeiwu.cmdhelper.client.command;

/**
 * The axis-aligned box a /fill touches, normalised so min <= max on every axis regardless of
 * which corner the player typed first. Pure ints, no Minecraft classes, so it stays testable.
 */
public record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Vanilla's default cap for one /fill (gamerule commandModificationBlockLimit). The client
     *  can't read the server's gamerule, so this is the default, not a guarantee. */
    public static final long FILL_BLOCK_LIMIT = 32768;

    /** @param from/to "x y z" with plain integers, exactly what CoordinateFields/RelativeRegion produce */
    public static RegionBounds of(String from, String to) {
        int[] a = parse(from);
        int[] b = parse(to);
        return new RegionBounds(
                Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** True if the two boxes share at least one block (edges are inclusive, so touching by one layer counts). */
    public boolean intersects(RegionBounds other) {
        return minX <= other.maxX && maxX >= other.minX
                && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean exceedsFillLimit() {
        return volume() > FILL_BLOCK_LIMIT;
    }

    private static int[] parse(String coords) {
        String[] parts = coords.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("expected \"x y z\" but got: " + coords);
        }
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
