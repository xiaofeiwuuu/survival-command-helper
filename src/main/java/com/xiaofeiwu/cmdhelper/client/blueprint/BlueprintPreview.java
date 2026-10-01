package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Plan;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Placed;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ghost of a blueprint at the block the crosshair points at, before anything is built. Coloured
 * boxes, one per /fill that will be sent (a colour per block kind; red for what will be skipped because
 * the block doesn't exist in this game). The keys turn, mirror and raise or lower it; right-click builds,
 * left-click cancels — the same pattern as the fill and clone previews.
 */
public final class BlueprintPreview {

    public static final KeyMapping ROTATE_KEY = new KeyMapping("key.cmdhelper.blueprint_rotate", GLFW.GLFW_KEY_R, "key.categories.cmdhelper");
    public static final KeyMapping MIRROR_KEY = new KeyMapping("key.cmdhelper.blueprint_mirror", GLFW.GLFW_KEY_G, "key.categories.cmdhelper");
    public static final KeyMapping UP_KEY = new KeyMapping("key.cmdhelper.blueprint_up", GLFW.GLFW_KEY_PAGE_UP, "key.categories.cmdhelper");
    public static final KeyMapping DOWN_KEY = new KeyMapping("key.cmdhelper.blueprint_down", GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.cmdhelper");
    public static final KeyMapping MODE_KEY = new KeyMapping("key.cmdhelper.blueprint_mode", GLFW.GLFW_KEY_V, "key.categories.cmdhelper");

    /** More boxes than this are not drawn (the filled faces get expensive); the rest still get built. */
    private static final int MAX_DRAWN_BOXES = 3000;
    private static final float FACE_ALPHA = 0.22f;
    private static final double INFLATE = 0.003;
    private static final double PICK_DISTANCE = 300.0;
    private static final float GHOST_ALPHA = 0.6f;

    private record DrawBox(AABB box, float[] color) {
    }

    private static Blueprint blueprint;
    private static boolean includeAir;
    private static boolean verifyAfter;
    private static Transform transform = Transform.NONE;
    private static int yOffset = 1; // 1 = the blueprint's bottom layer sits on top of the block aimed at

    private static final Map<Transform, PaletteBuilder.Prepared> PREPARED = new HashMap<>();
    private static BlockPos anchor;
    private static BlockPos planAnchor;
    private static Transform planTransform;
    private static int planYOffset;
    private static Plan plan;
    private static int[] planMin;
    private static List<DrawBox> drawBoxes = List.of();       // every box, for the coloured-boxes mode
    private static List<DrawBox> skippedBoxes = List.of();    // only what won't be built (red), shown over the ghost
    private static List<DrawBox> standInBoxes = List.of();    // blocks the ghost can't draw as models
    private static AABB outline;

    // The real-block ghost. Falls back to coloured boxes if it can't be made, or if it draws wrong (V toggles).
    private static boolean realBlocks = true;
    private static final Map<Transform, GhostModel> GHOSTS = new HashMap<>();
    private static GhostModel currentGhost;
    private static GhostModel planGhost;
    private static String ghostNote = "";
    private static String lastStatus;
    private static int ticksSinceStatus;

    private BlueprintPreview() {
    }

    public static boolean isActive() {
        return blueprint != null;
    }

    private static void closeGhosts() {
        for (GhostModel ghost : GHOSTS.values()) {
            ghost.close();
        }
        GHOSTS.clear();
        currentGhost = null;
        planGhost = null;
    }

    public static void start(Blueprint bp, boolean clearAir, boolean verify) {
        closeGhosts();
        ghostNote = "";
        blueprint = bp;
        includeAir = clearAir;
        verifyAfter = verify;
        transform = Transform.NONE;
        yOffset = 1;
        PREPARED.clear();
        // Presses made while no preview was running are still counted by the game: drop them, or
        // the ghost would start out already turned.
        while (ROTATE_KEY.consumeClick()) {
        }
        while (MIRROR_KEY.consumeClick()) {
        }
        while (UP_KEY.consumeClick()) {
        }
        while (DOWN_KEY.consumeClick()) {
        }
        while (MODE_KEY.consumeClick()) {
        }
        anchor = null;
        plan = null;
        planAnchor = null;
        drawBoxes = List.of();
        outline = null;
        lastStatus = null;
    }

    public static void cancel() {
        closeGhosts();
        blueprint = null;
        plan = null;
        PREPARED.clear();
        drawBoxes = List.of();
        skippedBoxes = List.of();
        standInBoxes = List.of();
        outline = null;
        lastStatus = null;
    }

    private static PaletteBuilder.Prepared prepared() {
        return PREPARED.computeIfAbsent(transform, t -> PaletteBuilder.prepare(blueprint, t));
    }

    // ---- per tick ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || blueprint == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            cancel();
            return;
        }
        if (mc.screen == null) {
            while (ROTATE_KEY.consumeClick()) {
                transform = transform.rotatedClockwise();
            }
            while (MIRROR_KEY.consumeClick()) {
                transform = transform.mirrored();
            }
            while (UP_KEY.consumeClick()) {
                yOffset++;
            }
            while (DOWN_KEY.consumeClick()) {
                yOffset--;
            }
            while (MODE_KEY.consumeClick()) {
                realBlocks = !realBlocks;
                ghostNote = "";
                lastStatus = null;
            }
        }
        currentGhost = realBlocks ? ghostFor(transform) : null;
        HitResult hit = mc.player.pick(PICK_DISTANCE, 1.0f, false);
        anchor = hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;

        if (anchor != null && (!anchor.equals(planAnchor) || !transform.equals(planTransform) || yOffset != planYOffset
                || currentGhost != planGhost)) {
            rebuildPlan();
        } else if (anchor == null) {
            plan = null;
            drawBoxes = List.of();
            skippedBoxes = List.of();
            standInBoxes = List.of();
            outline = null;
        }
        if (mc.screen == null) {
            showStatus(mc);
        }
    }

    /** The blueprint's blocks, already rotated/mirrored, as one block state per cell (null = nothing). */
    private static BlockState[] gridFor(Transform t, PaletteBuilder.Prepared prepared) {
        int[] size = t.horizontalSize(blueprint.sizeX(), blueprint.sizeZ());
        BlockState[] grid = new BlockState[size[0] * blueprint.sizeY() * size[1]];
        for (Cuboid c : blueprint.cuboids()) {
            BlockState state = prepared.states()[c.paletteIndex()];
            if (state == null || state.isAir()) {
                continue;
            }
            Cuboid r = t.apply(c, blueprint.sizeX(), blueprint.sizeZ());
            for (int y = r.y0(); y <= r.y1(); y++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    for (int x = r.x0(); x <= r.x1(); x++) {
                        grid[VoxelMerger.index(x, y, z, size[0], size[1])] = state;
                    }
                }
            }
        }
        return grid;
    }

    /**
     * The baked ghost for a rotation/mirror, made the first time it's needed. Null — and the preview
     * falls back to boxes — if the blueprint is too big to bake or baking fails.
     */
    private static GhostModel ghostFor(Transform t) {
        GhostModel cached = GHOSTS.get(t);
        if (cached != null) {
            return cached;
        }
        if (GHOSTS.containsKey(t) || !ghostNote.isEmpty()) {
            return null; // already tried and failed
        }
        try {
            PaletteBuilder.Prepared prepared = prepared();
            BlockState[] grid = gridFor(t, prepared);
            int count = GhostModel.countBlocks(grid);
            if (count > GhostModel.MAX_MODEL_BLOCKS) {
                ghostNote = "方块太多（" + count + " 个，上限 " + GhostModel.MAX_MODEL_BLOCKS + "），改用方框预览";
                return null;
            }
            int[] size = t.horizontalSize(blueprint.sizeX(), blueprint.sizeZ());
            GhostModel ghost = GhostModel.bake(grid, size[0], blueprint.sizeY(), size[1]);
            GHOSTS.put(t, ghost);
            return ghost;
        } catch (RuntimeException | LinkageError e) {
            realBlocks = false;
            ghostNote = "真实方块预览出错，已改用方框（" + e.getClass().getSimpleName() + "）";
            return null;
        }
    }

    private static void rebuildPlan() {
        PaletteBuilder.Prepared prepared = prepared();
        int[] size = transform.horizontalSize(blueprint.sizeX(), blueprint.sizeZ());
        // The blueprint is centred on the aimed block, and its bottom layer goes yOffset above it.
        int[] min = BlueprintPlacer.centredMin(anchor.getX(), anchor.getY() + yOffset, anchor.getZ(), size[0], size[1]);
        plan = BlueprintPlacer.plan(blueprint, transform, min[0], min[1], min[2], prepared.entries(), includeAir);
        planMin = min;
        planAnchor = anchor;
        planTransform = transform;
        planYOffset = yOffset;

        List<DrawBox> boxes = new ArrayList<>();
        List<DrawBox> skipped = new ArrayList<>();
        for (Placed placed : plan.placed()) {
            Cuboid c = placed.cuboid();
            String state = prepared.entries().get(c.paletteIndex()).state();
            if (Blueprint.isAir(state)) {
                continue; // clearing space isn't drawn; the outline shows the area
            }
            float[] color = placed.skipped() ? new float[]{1f, 0.2f, 0.2f} : ColorOf.rgb(Blueprint.blockId(state));
            DrawBox box = new DrawBox(new AABB(c.x0(), c.y0(), c.z0(), c.x1() + 1, c.y1() + 1, c.z1() + 1).inflate(INFLATE), color);
            if (boxes.size() < MAX_DRAWN_BOXES) {
                boxes.add(box);
            }
            if (placed.skipped() && skipped.size() < MAX_DRAWN_BOXES) {
                skipped.add(box);
            }
        }
        drawBoxes = boxes;
        skippedBoxes = skipped;

        List<DrawBox> stand = new ArrayList<>();
        planGhost = currentGhost;
        if (currentGhost != null) {
            for (GhostModel.Stand s : currentGhost.stand()) {
                if (stand.size() >= MAX_DRAWN_BOXES) {
                    break;
                }
                int wx = min[0] + s.x(), wy = min[1] + s.y(), wz = min[2] + s.z();
                stand.add(new DrawBox(new AABB(wx, wy, wz, wx + 1, wy + 1, wz + 1).inflate(INFLATE), ColorOf.rgb(s.blockId())));
            }
        }
        standInBoxes = stand;
        outline = new AABB(min[0], min[1], min[2], min[0] + size[0], min[1] + blueprint.sizeY(), min[2] + size[1]);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private static void showStatus(Minecraft mc) {
        String text;
        if (plan == null) {
            text = "蓝图「" + blueprint.name() + "」：把准星对准要放置的位置  |  左键取消";
        } else {
            var stats = plan.stats();
            PaletteBuilder.Prepared prepared = prepared();
            String skipped = stats.missingBlocks() + stats.tooLongCuboids() > 0
                    ? "  ⚠ 跳过 " + stats.missingBlocks() + " 格" + (stats.tooLongCuboids() > 0 ? "、" + stats.tooLongCuboids() + " 处命令太长" : "")
                    : "";
            text = "蓝图「" + blueprint.name() + "」 旋转 " + transform.quarterTurns() * 90 + "°" + (transform.mirrorX() ? " 镜像" : "")
                    + " 高度" + (yOffset >= 0 ? "+" : "") + yOffset + "  |  " + stats.commandCount() + " 条命令" + skipped
                    + "  |  右键放置 左键取消 " + keyName(ROTATE_KEY) + "旋转 " + keyName(MIRROR_KEY) + "镜像 "
                    + keyName(UP_KEY) + "/" + keyName(DOWN_KEY) + "升降 " + keyName(MODE_KEY) + "切换预览("
                    + (currentGhost != null ? "方块" : "方框") + ")" + (ghostNote.isEmpty() ? "" : "  " + ghostNote);
        }
        ticksSinceStatus++;
        if (!text.equals(lastStatus) || ticksSinceStatus >= 40) {
            lastStatus = text;
            ticksSinceStatus = 0;
            mc.player.displayClientMessage(Component.literal(text).withStyle(
                    plan != null && plan.stats().missingBlocks() > 0 ? ChatFormatting.YELLOW : ChatFormatting.AQUA), true);
        }
    }

    // ---- clicks -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (blueprint == null) {
            return;
        }
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            cancel();
            notify("已取消蓝图预览", ChatFormatting.GRAY);
        } else if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            confirm();
        }
    }

    private static void confirm() {
        if (plan == null || planMin == null) {
            notify("请先把准星对准要放置的位置", ChatFormatting.YELLOW);
            return;
        }
        if (plan.commands().isEmpty()) {
            notify("没有可以放置的内容（缺少的方块都被跳过了）", ChatFormatting.RED);
            return;
        }
        BlueprintExecutor.start(blueprint, transform, planMin, prepared(), plan, includeAir, verifyAfter);
        cancel();
    }

    private static void notify(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), true);
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------

    private static void drawBoxList(List<DrawBox> boxes, PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam) {
        for (DrawBox d : boxes) {
            AABB box = d.box().move(-cam.x, -cam.y, -cam.z);
            DebugRenderer.renderFilledBox(poseStack, buffers, box, d.color()[0], d.color()[1], d.color()[2], FACE_ALPHA);
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box,
                    d.color()[0], d.color()[1], d.color()[2], 0.9f);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || blueprint == null || outline == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();

        if (currentGhost != null && planMin != null && planGhost == currentGhost) {
            // the real blocks, see-through
            currentGhost.draw(poseStack.last().pose(), event.getProjectionMatrix(), cam, planMin[0], planMin[1], planMin[2], GHOST_ALPHA);
            drawBoxList(standInBoxes, poseStack, buffers, cam);   // chests, beds, water...: boxes
            drawBoxList(skippedBoxes, poseStack, buffers, cam);   // missing blocks: red
        } else {
            drawBoxList(drawBoxes, poseStack, buffers, cam);
        }
        // The whole footprint, so it's clear where the blueprint ends even where it is mostly air.
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()),
                outline.move(-cam.x, -cam.y, -cam.z), 1f, 1f, 1f, 1f);
        buffers.endBatch(RenderType.lines());
    }
}
