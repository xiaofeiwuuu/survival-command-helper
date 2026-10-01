package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A blueprint turned into a translucent ghost made of the real block models.
 *
 * Drawing thousands of block models every frame would stall the game, so the models are baked once into
 * a GPU buffer — the same way the game builds a chunk — and each frame only draws that buffer where the
 * crosshair points. One bake per rotation/mirror; moving the crosshair costs nothing.
 *
 * Because the ghost is baked away from where it is shown, its lighting and colours can't come from the
 * world around it: it is lit at full brightness, and grass, leaves and water use fixed typical colours.
 * Faces hidden by a neighbouring block of the blueprint are not generated. Blocks the normal model
 * renderer can't draw (chests, beds, signs: drawn by special renderers) and fluids are kept as a list of
 * positions for the caller to show as coloured boxes instead.
 */
public final class GhostModel implements AutoCloseable {

    /** A blueprint with more drawable blocks than this is not baked (the caller shows boxes instead). */
    public static final int MAX_MODEL_BLOCKS = 60_000;

    private static final int FULL_BRIGHT = 15;

    /** A block that gets a box instead of a model: where (local to the blueprint) and what it is. */
    public record Stand(int x, int y, int z, String blockId) {
    }

    // One builder reused for every bake: it owns native memory, so making a fresh one per rotation would
    // pile up. Dropped (and remade next time) if a bake fails half way, so a broken state isn't reused.
    private static BufferBuilder bakeBuilder;

    private final VertexBuffer buffer;
    private final List<Stand> stand;
    private final int modelBlocks;

    private GhostModel(VertexBuffer buffer, List<Stand> stand, int modelBlocks) {
        this.buffer = buffer;
        this.stand = stand;
        this.modelBlocks = modelBlocks;
    }

    public List<Stand> stand() {
        return stand;
    }

    public int modelBlocks() {
        return modelBlocks;
    }

    /** How many cells hold a block that would need baking (cheap: lets the caller refuse before doing any work). */
    public static int countBlocks(BlockState[] grid) {
        int n = 0;
        for (BlockState s : grid) {
            if (s != null && !s.isAir()) {
                n++;
            }
        }
        return n;
    }

    /**
     * @param grid cells indexed like {@link VoxelMerger#index}; null or air means nothing to draw
     * @return the baked ghost; must be {@link #close() closed} when no longer needed
     */
    public static GhostModel bake(BlockState[] grid, int sizeX, int sizeY, int sizeZ) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        GhostLevel level = new GhostLevel(grid, sizeX, sizeY, sizeZ, mc.level);
        if (bakeBuilder == null) {
            bakeBuilder = new BufferBuilder(1 << 20);
        }
        BufferBuilder builder = bakeBuilder;
        try {
            return bake(builder, dispatcher, level, grid, sizeX, sizeY, sizeZ);
        } catch (RuntimeException | LinkageError e) {
            bakeBuilder = null;
            throw e;
        }
    }

    private static GhostModel bake(BufferBuilder builder, BlockRenderDispatcher dispatcher, GhostLevel level,
                                   BlockState[] grid, int sizeX, int sizeY, int sizeZ) {
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        java.util.Map<net.minecraft.world.level.block.Block, String> ids = new java.util.HashMap<>();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Stand> stand = new ArrayList<>();
        int baked = 0;

        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState state = grid[VoxelMerger.index(x, y, z, sizeX, sizeZ)];
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    String id = ids.computeIfAbsent(state.getBlock(), b -> {
                        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b);
                        return key == null ? Blueprint.AIR : key.toString();
                    });
                    if (!state.getFluidState().isEmpty()) {
                        stand.add(new Stand(x, y, z, id)); // fluids need the game's own chunk-relative coordinates
                        continue;
                    }
                    RenderShape shape = state.getRenderShape();
                    if (shape == RenderShape.INVISIBLE) {
                        continue; // barriers, structure voids...
                    }
                    if (shape != RenderShape.MODEL) {
                        stand.add(new Stand(x, y, z, id)); // chests, beds, signs: drawn by special renderers
                        continue;
                    }
                    pos.set(x, y, z);
                    random.setSeed(state.getSeed(pos));
                    pose.pushPose();
                    pose.translate(x, y, z);
                    dispatcher.renderBatched(state, pos, level, pose, builder, true, random);
                    pose.popPose();
                    baked++;
                }
            }
        }

        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        VertexBuffer buffer = null;
        if (rendered != null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(rendered);
            VertexBuffer.unbind();
        }
        return new GhostModel(buffer, stand, baked);
    }

    /**
     * Draws the ghost with its lowest corner at the given world position.
     * @param modelView  the level's model-view matrix (the camera's rotation), from the render event
     * @param projection the projection matrix, from the render event
     */
    public void draw(Matrix4f modelView, Matrix4f projection, Vec3 camera, int worldX, int worldY, int worldZ, float alpha) {
        if (buffer == null || buffer.isInvalid()) {
            return;
        }
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        ShaderInstance shader = GameRenderer.getRendertypeTranslucentShader();
        if (shader == null) {
            type.clearRenderState();
            return;
        }
        // The shader multiplies every pixel by this colour, so its alpha makes the whole ghost see-through.
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        // The baked positions are relative to the blueprint's corner; this places that corner in the world
        // (relative to the camera, which is what the model-view matrix expects).
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set((float) (worldX - camera.x), (float) (worldY - camera.y), (float) (worldZ - camera.z));
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        type.clearRenderState();
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
        }
    }

    // ---- the little world the models are rendered "in" ------------------------------------------------

    /**
     * Just enough of a world for the block model renderer: which block is where (so it can skip faces
     * that are hidden by a neighbour inside the blueprint), full light, and fixed tint colours.
     */
    private static final class GhostLevel implements BlockAndTintGetter {

        private static final BlockState NOTHING = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        private final BlockState[] grid;
        private final int sizeX, sizeY, sizeZ;
        private final ClientLevel real;

        GhostLevel(BlockState[] grid, int sizeX, int sizeY, int sizeZ, ClientLevel real) {
            this.grid = grid;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.real = real;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return NOTHING;
            }
            BlockState state = grid[VoxelMerger.index(x, y, z, sizeX, sizeZ)];
            return state == null ? NOTHING : state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return real.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return real.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return real.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return real.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return FULL_BRIGHT;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int ambientDarkening) {
            return FULL_BRIGHT;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
                return 0x91BD59; // plains grass
            }
            if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
                return 0x77AB2F; // plains foliage
            }
            if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
                return 0x3F76E4; // default water
            }
            return 0xFFFFFF;
        }
    }
}
