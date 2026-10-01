package com.xiaofeiwu.cmdhelper.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPreview;
import com.xiaofeiwu.cmdhelper.client.blueprint.ColorOf;
import com.xiaofeiwu.cmdhelper.client.blueprint.GhostGrids;
import com.xiaofeiwu.cmdhelper.client.blueprint.GhostModel;
import com.xiaofeiwu.cmdhelper.client.blueprint.GhostPreferences;
import com.xiaofeiwu.cmdhelper.client.blueprint.StateStrings;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Translucent "ghost" of the region a /fill is about to touch. While active, the box is redrawn
 * every frame from a supplier (so "centered on me" / "in front of me" follow the player as they
 * move or turn), right-click sends the command and left-click cancels — both clicks are eaten
 * so they don't also place or mine a block. Purely client-side; nothing is sent until confirm.
 */
public final class FillPreview {

    /** One frame's worth of preview: the two corners (plain "x y z") and the command they belong to. */
    public record Plan(String from, String to, String command, String secondFrom, String secondTo, String title,
                       Ghost ghost) {

        /** The common case: one box, called "填充预览", drawn as a coloured box. */
        public Plan(String from, String to, String command) {
            this(from, to, command, null, null, "填充预览", null);
        }

        public Plan(String from, String to, String command, String secondFrom, String secondTo, String title) {
            this(from, to, command, secondFrom, secondTo, title, null);
        }
    }

    /**
     * What to show as real blocks instead of a plain box: FILL = the block being filled in, over the first
     * region (all of it, or just its shell for hollow / outline); COPY = the blocks currently in the first
     * region (the clone's source), shown over the second region (where they will land).
     */
    public record Ghost(Kind kind, String stateText, GhostGrids.Shape shape) {

        public enum Kind {FILL, COPY}

        public static Ghost fill(String stateText, GhostGrids.Shape shape) {
            return new Ghost(Kind.FILL, stateText, shape);
        }

        public static Ghost copyOfSource() {
            return new Ghost(Kind.COPY, null, GhostGrids.Shape.SOLID);
        }
    }

    private static final float[] COLOR_OK = {0.30f, 0.85f, 1.00f};
    // The second box (clone: where the copy will land) is green so it can't be mistaken for the source.
    private static final float[] COLOR_SECOND = {0.35f, 0.95f, 0.45f};
    private static final float[] COLOR_TOO_BIG = {1.00f, 0.25f, 0.25f};
    private static final float FACE_ALPHA = 0.22f;
    private static final float GHOST_ALPHA = 0.6f;
    private static final int MAX_STAND_BOXES = 3000;
    // Grows the box a hair past the block grid so its faces don't z-fight with real blocks.
    private static final double INFLATE = 0.003;

    private static Supplier<Plan> supplier;
    private static Plan current;
    private static RegionBounds currentBounds;
    private static RegionBounds currentSecondBounds;
    // The action-bar text is re-sent only when it changes or is about to fade. Sending it every
    // tick meant 20 system-chat events a second for every other mod listening to them.
    private static String lastStatus;
    private static int ticksSinceStatus;
    private static final int STATUS_REFRESH_TICKS = 40;

    // The real-block ghost, baked once per shape (not per position, so a region that follows the player
    // isn't rebaked every step). Null while boxes are shown instead.
    private static GhostModel ghost;
    private static String ghostKey;
    private static String ghostNote = "";

    private FillPreview() {
    }

    public static boolean isActive() {
        return supplier != null;
    }

    private static void closeGhost() {
        if (ghost != null) {
            ghost.close();
            ghost = null;
        }
        ghostKey = null;
    }

    public static void start(Supplier<Plan> planSupplier) {
        closeGhost();
        ghostNote = "";
        // Presses of the toggle key made while no preview was running would flip the mode at once.
        while (BlueprintPreview.MODE_KEY.consumeClick()) {
        }
        supplier = planSupplier;
        current = null;
        currentBounds = null;
        currentSecondBounds = null;
        lastStatus = null;
    }

    public static void cancel() {
        closeGhost();
        supplier = null;
        current = null;
        currentBounds = null;
        currentSecondBounds = null;
        lastStatus = null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || supplier == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancel();
            return;
        }
        if (mc.screen == null) {
            while (BlueprintPreview.MODE_KEY.consumeClick()) {
                GhostPreferences.realBlocks = !GhostPreferences.realBlocks;
                ghostNote = "";
                closeGhost();
            }
        }
        current = supplier.get();
        currentBounds = current == null ? null : RegionBounds.of(current.from(), current.to());
        currentSecondBounds = current == null || current.secondFrom() == null
                ? null : RegionBounds.of(current.secondFrom(), current.secondTo());
        updateGhost(mc);
        if (currentBounds != null && mc.screen == null) {
            Component status = statusLine(current.title(), currentBounds, currentSecondBounds != null);
            String text = status.getString();
            ticksSinceStatus++;
            if (!text.equals(lastStatus) || ticksSinceStatus >= STATUS_REFRESH_TICKS) {
                mc.player.displayClientMessage(status, true);
                lastStatus = text;
                ticksSinceStatus = 0;
            }
        }
    }

    /** The region the ghost is drawn over: the second one for a clone's copy, the first for a fill. */
    private static RegionBounds ghostTarget() {
        return current.ghost().kind() == Ghost.Kind.COPY ? currentSecondBounds : currentBounds;
    }

    private static void updateGhost(Minecraft mc) {
        if (current == null || current.ghost() == null || !GhostPreferences.realBlocks
                || currentBounds == null || currentBounds.exceedsFillLimit()) {
            closeGhost();
            return;
        }
        RegionBounds target = ghostTarget();
        if (target == null) {
            closeGhost();
            return;
        }
        int sx = target.sizeX(), sy = target.sizeY(), sz = target.sizeZ();
        Ghost g = current.ghost();
        // Position isn't part of the key: only what the ghost looks like decides whether to rebake.
        String key = g.kind() == Ghost.Kind.FILL
                ? "F|" + g.stateText() + "|" + g.shape() + "|" + sx + "x" + sy + "x" + sz
                : "C|" + current.from() + "|" + current.to() + "|" + current.secondFrom();
        if (key.equals(ghostKey)) {
            return;
        }
        closeGhost();
        ghostKey = key; // set even if it fails below, so a failure isn't retried every tick
        try {
            BlockState[] grid = new BlockState[sx * sy * sz];
            if (g.kind() == Ghost.Kind.FILL) {
                StateStrings.Parsed parsed = StateStrings.parse(g.stateText());
                // A block drawn by a special renderer (chest, bed) or a fluid has no model to show: the box stays.
                if (!parsed.exists() || parsed.state().isAir() || parsed.state().getRenderShape() != RenderShape.MODEL
                        || !parsed.state().getFluidState().isEmpty()) {
                    return;
                }
                GhostGrids.fill(grid, sx, sy, sz, parsed.state(), g.shape());
            } else {
                RegionBounds source = currentBounds;
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        for (int x = 0; x < sx; x++) {
                            pos.set(source.minX() + x, source.minY() + y, source.minZ() + z);
                            BlockState state = mc.level.getBlockState(pos);
                            if (!state.isAir()) {
                                grid[com.xiaofeiwu.cmdhelper.client.blueprint.VoxelMerger.index(x, y, z, sx, sz)] = state;
                            }
                        }
                    }
                }
            }
            if (GhostModel.countBlocks(grid) > GhostModel.MAX_MODEL_BLOCKS) {
                ghostNote = "方块太多，改用方框";
                return;
            }
            ghost = GhostModel.bake(grid, sx, sy, sz);
        } catch (RuntimeException | LinkageError e) {
            GhostPreferences.realBlocks = false;
            ghostNote = "真实方块预览出错，已改用方框（" + e.getClass().getSimpleName() + "）";
            closeGhost();
        }
    }

    private static Component statusLine(String title, RegionBounds b, boolean hasSecondBox) {
        String size = b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + " = " + b.volume() + " 格";
        if (b.exceedsFillLimit()) {
            return Component.literal(title + " " + size + "  超过 " + RegionBounds.FILL_BLOCK_LIMIT
                    + " 格上限，无法确认  |  左键取消").withStyle(ChatFormatting.RED);
        }
        String legend = hasSecondBox ? "  （蓝=源 绿=复制后）" : "";
        String modeHint = current != null && current.ghost() != null
                ? "  " + BlueprintPreview.MODE_KEY.getTranslatedKeyMessage().getString() + " 切换方块/方框"
                        + (ghostNote.isEmpty() ? "" : "（" + ghostNote + "）")
                : "";
        String permission = CommandExecutor.likelyHasPermission() ? "" : "  ⚠ 当前可能没有权限，服务器可能拒绝";
        return Component.literal(title + " " + size + legend + "  |  右键确认  ·  左键取消" + modeHint + permission)
                .withStyle(CommandExecutor.likelyHasPermission() ? ChatFormatting.AQUA : ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (supplier == null) {
            return;
        }
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            cancel();
            notify("已取消预览", ChatFormatting.GRAY);
        } else if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            confirm();
        }
    }

    private static void confirm() {
        if (current == null || currentBounds == null) {
            return;
        }
        if (currentBounds.exceedsFillLimit()) {
            notify("区域超过 " + RegionBounds.FILL_BLOCK_LIMIT + " 格上限，缩小后再确认", ChatFormatting.RED);
            return;
        }
        String command = current.command();
        cancel();
        CommandExecutor.execute(command);
    }

    private static void notify(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || currentBounds == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        RegionBounds target = ghost != null && current != null && current.ghost() != null ? ghostTarget() : null;
        boolean ghostOnFirst = target != null && target == currentBounds;
        boolean ghostOnSecond = target != null && target == currentSecondBounds;

        // A region showing real blocks only gets its outline; the filled faces would hide the blocks.
        drawBox(poseStack, buffers, cam, currentBounds,
                currentBounds.exceedsFillLimit() ? COLOR_TOO_BIG : COLOR_OK, !ghostOnFirst);
        if (currentSecondBounds != null) {
            drawBox(poseStack, buffers, cam, currentSecondBounds, COLOR_SECOND, !ghostOnSecond);
        }
        if (target != null) {
            ghost.draw(poseStack.last().pose(), event.getProjectionMatrix(), cam,
                    target.minX(), target.minY(), target.minZ(), GHOST_ALPHA);
            drawStandIns(poseStack, buffers, cam, target);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** Blocks the ghost can't draw as models (chests, beds, water): a coloured box each. */
    private static void drawStandIns(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam, RegionBounds target) {
        List<GhostModel.Stand> stands = ghost.stand();
        List<AABB> boxes = new ArrayList<>();
        for (int i = 0; i < stands.size() && i < MAX_STAND_BOXES; i++) {
            GhostModel.Stand st = stands.get(i);
            int x = target.minX() + st.x(), y = target.minY() + st.y(), z = target.minZ() + st.z();
            float[] color = ColorOf.rgb(st.blockId());
            AABB box = new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(INFLATE).move(-cam.x, -cam.y, -cam.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, box, color[0], color[1], color[2], FACE_ALPHA);
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, color[0], color[1], color[2], 0.9f);
        }
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam,
                                RegionBounds b, float[] c, boolean filled) {
        AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1)
                .inflate(INFLATE)
                .move(-cam.x, -cam.y, -cam.z);

        if (filled) {
            DebugRenderer.renderFilledBox(poseStack, buffers, box, c[0], c[1], c[2], FACE_ALPHA);
        }
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, c[0], c[1], c[2], 1.0f);
    }
}
