package com.xiaofeiwu.cmdhelper.client.blueprint;

/** A stable, distinct-looking colour per block id, for the preview boxes. Pure. */
public final class ColorOf {

    private ColorOf() {
    }

    /** @return {r, g, b} each 0..1; the same id always gives the same colour */
    public static float[] rgb(String blockId) {
        int hash = blockId.hashCode();
        float hue = ((hash & 0x7fffffff) % 360) / 360f;
        // two brightness levels keep neighbouring hues apart
        float value = ((hash >>> 9) & 1) == 0 ? 0.95f : 0.7f;
        return hsv(hue, 0.65f, value);
    }

    static float[] hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        int sector = (int) (h * 6) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return new float[]{r + m, g + m, b + m};
    }
}
