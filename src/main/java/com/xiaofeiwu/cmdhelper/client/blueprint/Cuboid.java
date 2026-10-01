package com.xiaofeiwu.cmdhelper.client.blueprint;

/** A box of identical blocks. Both corners inclusive; {@code paletteIndex} says which block state it is made of. */
public record Cuboid(int x0, int y0, int z0, int x1, int y1, int z1, int paletteIndex) {

    public int sizeX() {
        return x1 - x0 + 1;
    }

    public int sizeY() {
        return y1 - y0 + 1;
    }

    public int sizeZ() {
        return z1 - z0 + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public Cuboid moved(int dx, int dy, int dz) {
        return new Cuboid(x0 + dx, y0 + dy, z0 + dz, x1 + dx, y1 + dy, z1 + dz, paletteIndex);
    }
}
