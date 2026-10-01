package com.xiaofeiwu.cmdhelper.client.blueprint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Block states to and from the text used in blueprints and /fill commands. Only properties that differ
 * from the block's default are written ("minecraft:oak_stairs[facing=north]"), which keeps commands well
 * under the 256-character limit; reading fills the rest in from the default state.
 */
public final class StateStrings {

    private StateStrings() {
    }

    public static String minimal(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String id = key == null ? Blueprint.AIR : key.toString();
        BlockState defaults = state.getBlock().defaultBlockState();
        List<String> parts = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (!state.getValue(property).equals(defaults.getValue(property))) {
                parts.add(property.getName() + "=" + valueName(state, property));
            }
        }
        Collections.sort(parts);
        return parts.isEmpty() ? id : id + "[" + String.join(",", parts) + "]";
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /**
     * @param state    the state (the block's default plus whatever properties could be applied); null if the block doesn't exist
     * @param degraded some property in the text wasn't understood by this version of the block and was left at its default
     */
    public record Parsed(BlockState state, boolean degraded) {
        public boolean exists() {
            return state != null;
        }
    }

    public static Parsed parse(String text) {
        int bracket = text.indexOf('[');
        ResourceLocation id = ResourceLocation.tryParse(bracket < 0 ? text : text.substring(0, bracket));
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) {
            return new Parsed(null, false);
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        BlockState state = block.defaultBlockState();
        boolean degraded = false;
        if (bracket >= 0 && text.endsWith("]")) {
            for (String pair : text.substring(bracket + 1, text.length() - 1).split(",")) {
                String[] kv = pair.split("=", 2);
                Property<?> property = kv.length == 2 ? block.getStateDefinition().getProperty(kv[0]) : null;
                Optional<BlockState> applied = property == null ? Optional.empty() : apply(state, property, kv[1]);
                if (applied.isPresent()) {
                    state = applied.get();
                } else {
                    degraded = true;
                }
            }
        }
        return new Parsed(state, degraded);
    }

    private static <T extends Comparable<T>> Optional<BlockState> apply(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v));
    }

    /** The state after the blueprint's mirror, then its rotation — in that order, like structure placement. */
    public static BlockState transform(BlockState state, Transform transform) {
        BlockState result = state;
        if (transform.mirrorX()) {
            result = result.mirror(Mirror.FRONT_BACK);
        }
        Rotation rotation = switch (transform.quarterTurns()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return rotation == Rotation.NONE ? result : result.rotate(rotation);
    }
}
