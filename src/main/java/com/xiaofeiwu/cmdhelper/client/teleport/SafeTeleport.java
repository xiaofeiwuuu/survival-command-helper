package com.xiaofeiwu.cmdhelper.client.teleport;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Teleports to an X/Z column when the height isn't known (what /locate gives for structures).
 *
 * The server can find the ground for us ("positioned over"), but only in a chunk that is loaded:
 * for an unloaded one Level.getHeight() returns the world's lowest Y, which would drop the player
 * into bedrock. A structure a thousand blocks away is exactly that case. So for a chunk the client
 * doesn't have, this goes in two steps — up to the top of the world (which makes the server load
 * the area), then, once the chunk has arrived on the client, down onto the ground.
 */
public final class SafeTeleport {

    public enum Plan {
        /** Under a bedrock ceiling there is no reachable "ground" to look up: just move sideways. */
        KEEP_HEIGHT,
        /** The chunk is loaded, so the server can find the ground right away. */
        DIRECT_TO_SURFACE,
        /** Load the chunk first (go high), then come down. */
        LOAD_THEN_SURFACE
    }

    /** Pure decision, kept apart from the game so it can be tested. */
    public static Plan decide(boolean dimensionHasCeiling, boolean chunkLoadedOnClient) {
        if (dimensionHasCeiling) {
            return Plan.KEEP_HEIGHT;
        }
        return chunkLoadedOnClient ? Plan.DIRECT_TO_SURFACE : Plan.LOAD_THEN_SURFACE;
    }

    // Give the server a moment to actually start sending before believing "not loaded yet" is the
    // final answer, and don't wait forever for a chunk that never comes.
    private static final int MIN_WAIT_TICKS = 10;
    private static final int TIMEOUT_TICKS = 20 * 15;

    private static int targetX;
    private static int targetZ;
    private static int waited = -1; // -1: nothing pending

    private SafeTeleport() {
    }

    public static void toColumn(int x, int z) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        boolean loaded = level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        switch (decide(level.dimensionType().hasCeiling(), loaded)) {
            case KEEP_HEIGHT -> CommandExecutor.execute(CommandBuilders.teleportKeepingHeight(x, z));
            case DIRECT_TO_SURFACE -> CommandExecutor.execute(CommandBuilders.teleportToSurface(x, z));
            case LOAD_THEN_SURFACE -> {
                CommandExecutor.execute(CommandBuilders.teleportToHeight(x, level.getMaxBuildHeight(), z));
                targetX = x;
                targetZ = z;
                waited = 0;
                say("正在加载目标区域，加载完成后会自动落到地面…", ChatFormatting.GRAY);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || waited < 0) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            waited = -1;
            return;
        }
        waited++;
        if (waited >= MIN_WAIT_TICKS && mc.level.hasChunk(Math.floorDiv(targetX, 16), Math.floorDiv(targetZ, 16))) {
            waited = -1;
            CommandExecutor.execute(CommandBuilders.teleportToSurface(targetX, targetZ));
        } else if (waited > TIMEOUT_TICKS) {
            waited = -1;
            say("目标区域没能加载出来，你还停在高空，请手动处理（比如 /tp 回原处）", ChatFormatting.RED);
        }
    }

    private static void say(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), true);
        }
    }
}
