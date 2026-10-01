package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns a blueprint, a rotation/mirror and a position into the list of /fill commands that build it —
 * and records, per box, what will be skipped and why (a block from a mod that isn't installed, a command
 * that would be too long), so the preview can show it and the player can be told.
 *
 * Pure: the game-specific parts (is this block available here? what does the state look like after
 * rotating?) arrive as a ready-made palette.
 */
public final class BlueprintPlacer {

    /** Vanilla's default cap for one /fill (gamerule commandModificationBlockLimit). */
    public static final int FILL_LIMIT = 32768;
    /** A command packet is capped at 256 characters in 1.20.1. */
    public static final int MAX_COMMAND_LENGTH = 256;

    /**
     * The order blocks go down in. Air first (clear the space), then blocks that other blocks can sit
     * on, then the ones that need something to attach to — torches, doors, signs, plants — which would
     * pop off if placed before their support.
     */
    public enum Pass {CLEAR, SOLID, ATTACHED}

    /** One palette entry as it will be placed here: already rotated, and whether the block exists in this game. */
    public record PaletteEntry(String state, boolean available, Pass pass) {
    }

    /** A box in world coordinates, and whether it will not be built. */
    public record Placed(Cuboid cuboid, boolean skipped, String reason) {
    }

    public record Stats(int placedCuboids, long placedBlocks, int missingCuboids, long missingBlocks,
                        int tooLongCuboids, int commandCount) {
    }

    public record Plan(List<Placed> placed, List<String> commands, Stats stats) {
    }

    private BlueprintPlacer() {
    }

    /** @param min world position of the transformed blueprint's lowest corner */
    public static Plan plan(Blueprint blueprint, Transform transform, int minX, int minY, int minZ,
                            List<PaletteEntry> palette, boolean includeAir) {
        List<Cuboid> world = new ArrayList<>(blueprint.cuboids().size());
        for (Cuboid c : blueprint.cuboids()) {
            world.add(transform.apply(c, blueprint.sizeX(), blueprint.sizeZ()).moved(minX, minY, minZ));
        }
        return planWorld(world, palette, includeAir);
    }

    /** The same for boxes that are already in world coordinates (also used to patch up what a check found wrong). */
    public static Plan planWorld(List<Cuboid> worldCuboids, List<PaletteEntry> palette, boolean includeAir) {
        record Command(Pass pass, Cuboid box, String text) {
        }
        List<Placed> placed = new ArrayList<>();
        List<Command> commands = new ArrayList<>();
        int okCuboids = 0, missingCuboids = 0, tooLong = 0;
        long okBlocks = 0, missingBlocks = 0;

        for (Cuboid cuboid : worldCuboids) {
            PaletteEntry entry = palette.get(cuboid.paletteIndex());
            boolean air = Blueprint.isAir(entry.state());
            if (air && !includeAir) {
                continue; // not asked to clear: leave whatever is there
            }
            if (!entry.available()) {
                placed.add(new Placed(cuboid, true, "缺少方块 " + Blueprint.blockId(entry.state())));
                missingCuboids++;
                missingBlocks += cuboid.volume();
                continue;
            }
            Pass pass = air ? Pass.CLEAR : entry.pass();
            boolean fits = true;
            List<Command> pieces = new ArrayList<>();
            for (Cuboid piece : split(cuboid, FILL_LIMIT)) {
                String text = fillText(piece, entry.state());
                if (text.length() > MAX_COMMAND_LENGTH) {
                    fits = false;
                    break;
                }
                pieces.add(new Command(pass, piece, text));
            }
            if (!fits) {
                placed.add(new Placed(cuboid, true, "命令太长（超过 " + MAX_COMMAND_LENGTH + " 个字符）：" + entry.state()));
                tooLong++;
                continue;
            }
            placed.add(new Placed(cuboid, false, null));
            commands.addAll(pieces);
            okCuboids++;
            okBlocks += cuboid.volume();
        }

        commands.sort(Comparator.<Command>comparingInt(c -> c.pass().ordinal())
                .thenComparingInt(c -> c.box().y0())
                .thenComparingInt(c -> c.box().z0())
                .thenComparingInt(c -> c.box().x0()));
        List<String> texts = new ArrayList<>(commands.size());
        for (Command c : commands) {
            texts.add(c.text());
        }
        return new Plan(placed, texts, new Stats(okCuboids, okBlocks, missingCuboids, missingBlocks, tooLong, texts.size()));
    }

    static String fillText(Cuboid c, String state) {
        return CommandBuilders.fill(c.x0() + " " + c.y0() + " " + c.z0(), c.x1() + " " + c.y1() + " " + c.z1(), state, null);
    }

    /** Cuts a box into pieces of at most {@code limit} blocks, halving along its longest side. */
    static List<Cuboid> split(Cuboid c, long limit) {
        if (c.volume() <= limit) {
            return List.of(c);
        }
        int sx = c.sizeX(), sy = c.sizeY(), sz = c.sizeZ();
        List<Cuboid> out = new ArrayList<>();
        Cuboid a, b;
        if (sx >= sy && sx >= sz) {
            int mid = c.x0() + sx / 2 - 1;
            a = new Cuboid(c.x0(), c.y0(), c.z0(), mid, c.y1(), c.z1(), c.paletteIndex());
            b = new Cuboid(mid + 1, c.y0(), c.z0(), c.x1(), c.y1(), c.z1(), c.paletteIndex());
        } else if (sy >= sz) {
            int mid = c.y0() + sy / 2 - 1;
            a = new Cuboid(c.x0(), c.y0(), c.z0(), c.x1(), mid, c.z1(), c.paletteIndex());
            b = new Cuboid(c.x0(), mid + 1, c.z0(), c.x1(), c.y1(), c.z1(), c.paletteIndex());
        } else {
            int mid = c.z0() + sz / 2 - 1;
            a = new Cuboid(c.x0(), c.y0(), c.z0(), c.x1(), c.y1(), mid, c.paletteIndex());
            b = new Cuboid(c.x0(), c.y0(), mid + 1, c.x1(), c.y1(), c.z1(), c.paletteIndex());
        }
        out.addAll(split(a, limit));
        out.addAll(split(b, limit));
        return out;
    }

    /** World position of the transformed blueprint's lowest corner when it is centred on (anchorX, anchorZ). */
    public static int[] centredMin(int anchorX, int baseY, int anchorZ, int sizeX, int sizeZ) {
        return new int[]{anchorX - sizeX / 2, baseY, anchorZ - sizeZ / 2};
    }
}
