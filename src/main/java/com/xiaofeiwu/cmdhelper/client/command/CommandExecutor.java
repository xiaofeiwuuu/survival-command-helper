package com.xiaofeiwu.cmdhelper.client.command;

import com.xiaofeiwu.cmdhelper.client.history.CommandHistoryStore;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Sends a finished command to the server exactly the way the chat box does, and mirrors
 * it back into chat so the player can see what ran (or read the server's rejection message
 * if they don't actually have permission — this mod never claims success on its own).
 */
public final class CommandExecutor {

    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    private CommandExecutor() {
    }

    /** Best-effort check only: mirrors the op level the server last told this client about. */
    public static boolean likelyHasPermission() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }

    public static void execute(String commandWithoutSlash) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.sendCommand(commandWithoutSlash);
        var echo = Component.literal("[指令助手] ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/" + commandWithoutSlash).withStyle(ChatFormatting.YELLOW));
        String description = CommandDescriber.describe(commandWithoutSlash, RegistryNames.INSTANCE);
        if (description != null) {
            echo.append(Component.literal("  " + description).withStyle(ChatFormatting.GRAY));
        }
        player.displayClientMessage(echo, false);
        CommandHistoryStore.record(commandWithoutSlash);
    }

    /**
     * Sends several commands as one action with a single line of echo ("[指令助手] <summary>"),
     * instead of one echo line each. Not recorded in the history list: history is for re-running
     * one command, and a scan-dependent batch is meaningless to replay later.
     */
    public static void executeBatch(java.util.List<String> commandsWithoutSlash, String summary) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null || commandsWithoutSlash.isEmpty()) {
            return;
        }
        for (String command : commandsWithoutSlash) {
            player.connection.sendCommand(command);
        }
        player.displayClientMessage(
                Component.literal("[指令助手] ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(summary).withStyle(ChatFormatting.YELLOW)),
                false
        );
    }

    /**
     * For commands whose answer only comes back as a line of chat (/locate, /forceload query).
     * The capture is armed AFTER execute() on purpose: execute() echoes the command into chat
     * through the same system-message event the capture listens on, so arming first made the
     * echo ("[指令助手] /locate ...") get taken as the result. The server's real reply can't
     * arrive before this method returns — it needs a network round trip.
     */
    public static void executeAwaitingResult(String commandWithoutSlash, Consumer<String> onResult) {
        execute(commandWithoutSlash);
        ChatResultCapture.awaitNext(onResult);
    }

    public static void copyToClipboard(String commandWithoutSlash) {
        copyRaw("/" + commandWithoutSlash);
        CommandHistoryStore.record(commandWithoutSlash);
    }

    public static void copyRaw(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.literal("[指令助手] 已复制到剪贴板").withStyle(ChatFormatting.GRAY),
                    false
            );
        }
    }
}
