package com.xiaofeiwu.cmdhelper.client.registry;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Directional blocks (trapdoors, doors, stairs, furnaces, dispensers, ...) default to some
 * fixed facing/half if you don't specify one — fine for placing one block, wrong for bulk-filling
 * a wall of trapdoors that all need to face the same way (and possibly sit in the top half of
 * the block space, e.g. a trapdoor hung from a ceiling). This reads the block's own possible
 * states instead of hand-listing which blocks are directional, and combines both choices into
 * a single "[facing=...,half=...]" state suffix — Minecraft rejects two separate bracket groups.
 */
public final class BlockFacing {

    public static final String NONE = "不设置";
    public static final List<String> LABELS = List.of(NONE, "北", "南", "东", "西", "上", "下");

    private static final Map<String, Direction> DIRECTION_BY_LABEL = Map.of(
            "北", Direction.NORTH,
            "南", Direction.SOUTH,
            "东", Direction.EAST,
            "西", Direction.WEST,
            "上", Direction.UP,
            "下", Direction.DOWN
    );

    // Trapdoors/stairs use the state property "half" with values top/bottom; doors (and a few
    // other double-tall blocks) also use a property named "half" but with values upper/lower.
    // Both are read generically by trying each candidate raw value against the block's actual
    // property instead of hard-coding which enum a given block uses.
    public static final String HALF_NONE = "不设置";
    public static final List<String> HALF_LABELS = List.of(HALF_NONE, "上半", "下半");

    private static final Map<String, List<String>> HALF_CANDIDATES = Map.of(
            "上半", List.of("top", "upper"),
            "下半", List.of("bottom", "lower")
    );

    private BlockFacing() {
    }

    // Trapdoors and stairs call it "half"; slabs call the very same top/bottom choice "type"
    // (and add "double"), so picking "下半" on a slab used to be silently dropped.
    private static final List<String> HALF_PROPERTY_NAMES = List.of("half", "type");

    /** The block-state suffix for the chosen labels, plus which of the two choices this
     *  particular block had no matching property for (so the screen can say so). */
    public record Resolved(String suffix, boolean facingIgnored, boolean halfIgnored) {

        /** Null when nothing was dropped; otherwise a one-line explanation for the preview. */
        public String ignoredNote() {
            if (facingIgnored && halfIgnored) {
                return "该方块不支持所选的朝向和上/下半，已忽略";
            }
            if (facingIgnored) {
                return "该方块不支持所选的朝向，已忽略";
            }
            if (halfIgnored) {
                return "该方块不支持所选的上/下半，已忽略";
            }
            return null;
        }
    }

    /**
     * Builds the combined "[facing=xxx,half=yyy]" block-state suffix for the chosen labels,
     * silently dropping whichever half doesn't apply to this particular block (e.g. "上"/"下"
     * on a trapdoor's facing, which only accepts the 4 horizontal directions) instead of
     * generating a command that fails on an unrelated block.
     */
    public static String stateSuffix(ResourceLocation blockId, String facingLabel, String halfLabel) {
        return resolve(blockId, facingLabel, halfLabel).suffix();
    }

    public static Resolved resolve(ResourceLocation blockId, String facingLabel, String halfLabel) {
        Block block = ForgeRegistries.BLOCKS.getValue(blockId);
        if (block == null) {
            return new Resolved("", false, false);
        }
        List<String> parts = new ArrayList<>();
        boolean facingIgnored = false;
        boolean halfIgnored = false;

        Direction dir = facingLabel == null ? null : DIRECTION_BY_LABEL.get(facingLabel);
        if (dir != null) {
            Property<?> facing = block.getStateDefinition().getProperty("facing");
            if (facing instanceof DirectionProperty && facing.getPossibleValues().contains(dir)) {
                parts.add("facing=" + dir.getSerializedName());
            } else {
                facingIgnored = true;
            }
        }

        List<String> halfCandidates = halfLabel == null ? null : HALF_CANDIDATES.get(halfLabel);
        if (halfCandidates != null) {
            String applied = null;
            for (String propertyName : HALF_PROPERTY_NAMES) {
                Property<?> half = block.getStateDefinition().getProperty(propertyName);
                if (half == null) {
                    continue;
                }
                for (String candidate : halfCandidates) {
                    if (half.getValue(candidate).isPresent()) {
                        applied = propertyName + "=" + candidate;
                        break;
                    }
                }
                if (applied != null) {
                    break;
                }
            }
            if (applied != null) {
                parts.add(applied);
            } else {
                halfIgnored = true;
            }
        }

        String suffix = parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
        return new Resolved(suffix, facingIgnored, halfIgnored);
    }
}
