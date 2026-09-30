package com.xiaofeiwu.cmdhelper.client.teleport;

import com.xiaofeiwu.cmdhelper.client.command.ChatResultCapture;
import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;

/**
 * Teleports to an X/Z column when the height isn't known (what /locate gives for structures).
 *
 * The server can find the ground for us ("positioned over"), but it refuses with "that position is not
 * loaded" when the chunk isn't loaded — and a structure a thousand blocks away usually isn't. Whether
 * the SERVER has the chunk can't be read off the client (a client-side chunk cache can make the client
 * think it does), so this goes by the server's answer: send the surface teleport; if it's refused,
 * climb to the top of the world (which makes the server load the area), wait, and try again. The
 * retry logic itself is {@link SurfaceTeleportFlow}.
 */
public final class SafeTeleport {

    // The key of BlockPosArgument.ERROR_NOT_LOADED, i.e. the "该位置尚未被加载" refusal.
    private static final Set<String> NOT_LOADED = Set.of("argument.pos.unloaded");

    private static SurfaceTeleportFlow flow;
    private static int targetX;
    private static int targetZ;

    private SafeTeleport() {
    }

    public static void toColumn(int x, int z) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        // Under a bedrock ceiling (the Nether) there is no ground to look up: just move sideways.
        if (level.dimensionType().hasCeiling()) {
            CommandExecutor.execute(CommandBuilders.teleportKeepingHeight(x, z));
            return;
        }
        targetX = x;
        targetZ = z;
        flow = new SurfaceTeleportFlow();
        // The first attempt is shown in chat like any command; retries are sent quietly so a slow
        // load doesn't fill the chat with copies of it.
        CommandExecutor.execute(CommandBuilders.teleportToSurface(x, z));
        watchForRefusal();
    }

    /** The refusal is swallowed (hidden from chat): this class handles it, the player needn't see red text. */
    private static void watchForRefusal() {
        ChatResultCapture.awaitKeyed(NOT_LOADED, true, message -> {
            if (flow != null) {
                flow.reportRefusal();
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || flow == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            finish();
            return;
        }
        switch (flow.tick()) {
            case CLIMB -> {
                say("目标区域服务器还没加载，先升到高处让它加载，稍后自动落到地面…", ChatFormatting.GRAY);
                CommandExecutor.execute(CommandBuilders.teleportToHeight(targetX, mc.level.getMaxBuildHeight(), targetZ));
            }
            case ATTEMPT -> {
                mc.player.connection.sendCommand(CommandBuilders.teleportToSurface(targetX, targetZ));
                watchForRefusal();
            }
            case SUCCESS -> finish();
            case GIVE_UP -> {
                boolean climbed = flow.hasClimbed();
                finish();
                say(climbed ? "目标区域没能加载出来，你可能还停在高空，请手动处理（比如 /tp 回原处）"
                        : "传送没有成功", ChatFormatting.RED);
            }
            case NONE -> {
            }
        }
    }

    private static void finish() {
        ChatResultCapture.cancelKeyed();
        flow = null;
    }

    private static void say(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), true);
        }
    }
}
