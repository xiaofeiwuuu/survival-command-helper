package com.xiaofeiwu.cmdhelper.client.blueprint;

/**
 * Which cells of a box a /fill puts its block in, for the ghost preview. Pure and generic over the
 * block type so it can be tested without the game.
 */
public final class GhostGrids {

    public enum Shape {
        /** Every cell (replace / keep / destroy). */
        SOLID,
        /** Only the outermost layer (hollow and outline); what is inside is left empty in the preview. */
        SHELL
    }

    private GhostGrids() {
    }

    /** @param modeArg what follows the block in the fill command ("hollow", "replace minecraft:stone"...), or null */
    public static Shape shapeForFillMode(String modeArg) {
        if (modeArg == null) {
            return Shape.SOLID;
        }
        String mode = modeArg.trim().split("\\s+")[0];
        return mode.equals("hollow") || mode.equals("outline") ? Shape.SHELL : Shape.SOLID;
    }

    /** Puts {@code value} in the cells the shape covers; every other cell is left as it was. Indexed like {@link VoxelMerger#index}. */
    public static <T> void fill(T[] grid, int sizeX, int sizeY, int sizeZ, T value, Shape shape) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    boolean onShell = x == 0 || y == 0 || z == 0 || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
                    if (shape == Shape.SOLID || onShell) {
                        grid[VoxelMerger.index(x, y, z, sizeX, sizeZ)] = value;
                    }
                }
            }
        }
    }
}
