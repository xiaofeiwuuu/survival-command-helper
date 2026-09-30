package com.xiaofeiwu.cmdhelper.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.Supplier;

/**
 * Translucent "ghost" of the region a /fill is about to touch. While active, the box is redrawn
 * every frame from a supplier (so "centered on me" / "in front of me" follow the player as they
 * move or turn), right-click sends the command and left-click cancels — both clicks are eaten
 * so they don't also place or mine a block. Purely client-side; nothing is sent until confirm.
 */
public final class FillPreview {

    /** One frame's worth of preview: the two corners (plain "x y z") and the command they belong to. */
    public record Plan(String from, String to, String command, String secondFrom, String secondTo, String title) {

        /** The common case: one box, called "填充预览". */
        public Plan(String from, String to, String command) {
            this(from, to, command, null, null, "填充预览");
        }
    }

    private static final float[] COLOR_OK = {0.30f, 0.85f, 1.00f};
    // The second box (clone: where the copy will land) is green so it can't be mistaken for the source.
    private static final float[] COLOR_SECOND = {0.35f, 0.95f, 0.45f};
    private static final float[] COLOR_TOO_BIG = {1.00f, 0.25f, 0.25f};
    private static final float FACE_ALPHA = 0.22f;
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

    private FillPreview() {
    }

    public static boolean isActive() {
        return supplier != null;
    }

    public static void start(Supplier<Plan> planSupplier) {
        supplier = planSupplier;
        current = null;
        currentBounds = null;
        currentSecondBounds = null;
        lastStatus = null;
    }

    public static void cancel() {
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
        current = supplier.get();
        currentBounds = current == null ? null : RegionBounds.of(current.from(), current.to());
        currentSecondBounds = current == null || current.secondFrom() == null
                ? null : RegionBounds.of(current.secondFrom(), current.secondTo());
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

    private static Component statusLine(String title, RegionBounds b, boolean hasSecondBox) {
        String size = b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + " = " + b.volume() + " 格";
        if (b.exceedsFillLimit()) {
            return Component.literal(title + " " + size + "  超过 " + RegionBounds.FILL_BLOCK_LIMIT
                    + " 格上限，无法确认  |  左键取消").withStyle(ChatFormatting.RED);
        }
        String legend = hasSecondBox ? "  （蓝=源 绿=复制后）" : "";
        String permission = CommandExecutor.likelyHasPermission() ? "" : "  ⚠ 当前可能没有权限，服务器可能拒绝";
        return Component.literal(title + " " + size + legend + "  |  右键确认  ·  左键取消" + permission)
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

        drawBox(poseStack, buffers, cam, currentBounds,
                currentBounds.exceedsFillLimit() ? COLOR_TOO_BIG : COLOR_OK);
        if (currentSecondBounds != null) {
            drawBox(poseStack, buffers, cam, currentSecondBounds, COLOR_SECOND);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void drawBox(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam,
                                RegionBounds b, float[] c) {
        AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1)
                .inflate(INFLATE)
                .move(-cam.x, -cam.y, -cam.z);

        DebugRenderer.renderFilledBox(poseStack, buffers, box, c[0], c[1], c[2], FACE_ALPHA);
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), box, c[0], c[1], c[2], 1.0f);
    }
}
