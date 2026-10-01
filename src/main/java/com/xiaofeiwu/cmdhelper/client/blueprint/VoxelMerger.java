package com.xiaofeiwu.cmdhelper.client.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a grid of blocks into as few boxes of identical blocks as a simple greedy pass can find, so a
 * wall is one /fill instead of hundreds, a single stray block is one box, and a row is one box.
 *
 * Cells are indexed {@code x + sizeX * (z + sizeZ * y)} (x fastest, then z, then y) and hold a palette
 * index; a negative value means "nothing here" and is left out. Every cell with a value ends up in
 * exactly one box. The result isn't guaranteed to be the global minimum — it merges runs along X, then
 * rows along Z, then layers along Y, which is cheap and gets the common shapes right.
 */
public final class VoxelMerger {

    private VoxelMerger() {
    }

    public static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return x + sizeX * (z + sizeZ * y);
    }

    private static final class Rect {
        final int x0, x1, z0, idx;
        int z1;

        Rect(int x0, int x1, int z0, int z1, int idx) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.idx = idx;
        }
    }

    private record RunKey(int x0, int x1, int idx) {
    }

    private record RectKey(int x0, int x1, int z0, int z1, int idx) {
    }

    private static final class Open {
        final Cuboid start;
        int y1;

        Open(Cuboid start) {
            this.start = start;
            this.y1 = start.y0();
        }
    }

    public static List<Cuboid> merge(int[] cells, int sizeX, int sizeY, int sizeZ) {
        List<Cuboid> out = new ArrayList<>();
        Map<RectKey, Open> openBoxes = new HashMap<>();
        // Per layer, runs along X are merged into rectangles along Z; a rectangle that is identical to
        // one in the layer below extends that box upward instead of starting a new one.
        List<Open> all = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            List<Rect> rects = rectsOfLayer(cells, sizeX, sizeZ, y);
            Map<RectKey, Open> next = new HashMap<>();
            for (Rect r : rects) {
                RectKey key = new RectKey(r.x0, r.x1, r.z0, r.z1, r.idx);
                Open open = openBoxes.remove(key);
                if (open == null) {
                    open = new Open(new Cuboid(r.x0, y, r.z0, r.x1, y, r.z1, r.idx));
                    all.add(open);
                } else {
                    open.y1 = y;
                }
                next.put(key, open);
            }
            openBoxes = next;
        }
        for (Open o : all) {
            Cuboid s = o.start;
            out.add(new Cuboid(s.x0(), s.y0(), s.z0(), s.x1(), o.y1, s.z1(), s.paletteIndex()));
        }
        return out;
    }

    private static List<Rect> rectsOfLayer(int[] cells, int sizeX, int sizeZ, int y) {
        List<Rect> rects = new ArrayList<>();
        Map<RunKey, Rect> open = new HashMap<>();
        for (int z = 0; z < sizeZ; z++) {
            Map<RunKey, Rect> next = new HashMap<>();
            int x = 0;
            while (x < sizeX) {
                int value = cells[index(x, y, z, sizeX, sizeZ)];
                if (value < 0) {
                    x++;
                    continue;
                }
                int end = x;
                while (end + 1 < sizeX && cells[index(end + 1, y, z, sizeX, sizeZ)] == value) {
                    end++;
                }
                RunKey key = new RunKey(x, end, value);
                Rect rect = open.remove(key);
                if (rect == null) {
                    rect = new Rect(x, end, z, z, value);
                    rects.add(rect);
                } else {
                    rect.z1 = z;
                }
                next.put(key, rect);
                x = end + 1;
            }
            open = next;
        }
        return rects;
    }
}
