package com.xiaofeiwu.cmdhelper.client.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic behind /clone, kept free of Minecraft classes so it can be tested against the
 * worked examples in "clone 注意点.md".
 *
 * What the game actually does (checked against 1.20.1): the two source corners are inclusive and
 * may be given in either order; the destination point is the LOWEST corner of the destination area
 * (not "the point matching the first source corner"); the destination has exactly the source's size.
 * So everything here works on the normalised source box and treats the destination as its min corner.
 */
public final class CloneCalc {

    private CloneCalc() {
    }

    public enum MaskMode {
        /** Everything in the source replaces what's at the destination. */
        REPLACE("replace"),
        /** Air in the source is skipped, so the destination keeps its blocks under it. */
        MASKED("masked");

        final String keyword;

        MaskMode(String keyword) {
            this.keyword = keyword;
        }
    }

    public enum CloneMode {
        /** Adds "force" only when source and destination overlap — the one case that needs it. */
        AUTO(null),
        NORMAL(null),
        /** Allows the areas to overlap. */
        FORCE("force"),
        /** Copies, then clears the source. */
        MOVE("move");

        final String keyword;

        CloneMode(String keyword) {
            this.keyword = keyword;
        }
    }

    /**
     * @param source      normalised source box (both corners inclusive)
     * @param destination the box the copy will occupy — same size, its min corner is the destination point
     */
    public record Plan(RegionBounds source, RegionBounds destination) {

        public boolean overlaps() {
            return source.intersects(destination);
        }

        public long volume() {
            return source.volume();
        }

        public String destinationStart() {
            return destination.minX() + " " + destination.minY() + " " + destination.minZ();
        }
    }

    /** Destination given directly, as the lowest corner of where the copy goes. */
    public static Plan plan(RegionBounds source, int destMinX, int destMinY, int destMinZ) {
        return new Plan(source, new RegionBounds(
                destMinX, destMinY, destMinZ,
                destMinX + source.sizeX() - 1, destMinY + source.sizeY() - 1, destMinZ + source.sizeZ() - 1));
    }

    /** Destination given as a shift of the source, e.g. dy = height to stack one copy on top of another. */
    public static Plan planWithOffset(RegionBounds source, int dx, int dy, int dz) {
        return plan(source, source.minX() + dx, source.minY() + dy, source.minZ() + dz);
    }

    /**
     * "Move it this many blocks east / south / west / north / up / down" as an X/Y/Z shift.
     * East is +X, south is +Z (Minecraft's own axes); opposite directions cancel.
     * @return {dx, dy, dz}
     */
    public static int[] directionalOffset(int east, int south, int west, int north, int up, int down) {
        return new int[]{east - west, up - down, south - north};
    }

    /** The trailing "[replace|masked] [force|move]" part, or null when the defaults already say it. */
    public static String options(MaskMode mask, CloneMode mode, boolean overlaps) {
        String modeWord = switch (mode) {
            case AUTO -> overlaps ? "force" : null;
            default -> mode.keyword;
        };
        if (mask == MaskMode.REPLACE && modeWord == null) {
            return null;
        }
        // The clone mode can't be written without the mask word in front of it.
        return modeWord == null ? mask.keyword : mask.keyword + " " + modeWord;
    }

    public static String command(Plan plan, MaskMode mask, CloneMode mode) {
        RegionBounds s = plan.source();
        String begin = s.minX() + " " + s.minY() + " " + s.minZ();
        String end = s.maxX() + " " + s.maxY() + " " + s.maxZ();
        return CommandBuilders.clone(begin, end, plan.destinationStart(), options(mask, mode, plan.overlaps()));
    }

    /** Things worth knowing before pressing execute, most important first. Empty when nothing stands out. */
    public static List<String> warnings(Plan plan, MaskMode mask, CloneMode mode) {
        List<String> warnings = new ArrayList<>();
        if (plan.volume() > RegionBounds.FILL_BLOCK_LIMIT) {
            warnings.add("共 " + plan.volume() + " 格，超过默认上限 " + RegionBounds.FILL_BLOCK_LIMIT + "，服务器可能拒绝");
        }
        if (plan.overlaps()) {
            switch (mode) {
                case NORMAL -> warnings.add("源和目标重叠，普通模式会被服务器拒绝，请改成自动或强制");
                case MOVE -> warnings.add("移动模式不允许源和目标重叠");
                default -> warnings.add("源和目标有重叠，已使用 force，重叠部分会被覆盖");
            }
        }
        if (mode == CloneMode.MOVE) {
            warnings.add("移动模式会清除源区域");
        }
        return warnings;
    }
}
