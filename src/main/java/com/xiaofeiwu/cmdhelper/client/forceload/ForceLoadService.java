package com.xiaofeiwu.cmdhelper.client.forceload;

import com.xiaofeiwu.cmdhelper.client.command.ChatResultCapture;
import com.xiaofeiwu.cmdhelper.client.forceload.ForceLoadedChunks.Chunk;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Set;

/**
 * Keeps the last known list of force-loaded chunks for the player's current dimension.
 *
 * Forced chunks live on the server and the client has no way to read them, so the list is obtained
 * the only way it's exposed: by asking with {@code /forceload query} and reading the reply. The
 * query is sent quietly (no chat echo, no history entry) and the server's reply is captured and
 * hidden — opening the screen shouldn't spam chat. It needs the same permission as /forceload;
 * without it the server never sends a list and the request times out.
 */
public final class ForceLoadService {

    public enum Status {NEVER_ASKED, LOADING, READY, TIMED_OUT}

    // list.* are the two "found N chunks" replies. The empty case is reported as a *failure* whose
    // key is (misleadingly) "added.none" — it is the only place vanilla uses it for the query.
    private static final Set<String> REPLY_KEYS = Set.of(
            "commands.forceload.list.single",
            "commands.forceload.list.multiple",
            "commands.forceload.added.none");

    private static final long TIMEOUT_MILLIS = 4000;

    private static Status status = Status.NEVER_ASKED;
    private static List<Chunk> chunks = List.of();
    private static long askedAt;

    private ForceLoadService() {
    }

    /** Asks the server again. The answer arrives a moment later; watch {@link #status()}. */
    public static void refresh() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            return;
        }
        status = Status.LOADING;
        askedAt = System.currentTimeMillis();
        ChatResultCapture.awaitKeyed(REPLY_KEYS, message -> {
            chunks = ForceLoadedChunks.parse(message.getString());
            status = Status.READY;
        });
        player.connection.sendCommand("forceload query");
    }

    public static Status status() {
        if (status == Status.LOADING && System.currentTimeMillis() - askedAt > TIMEOUT_MILLIS) {
            ChatResultCapture.cancelKeyed();
            status = Status.TIMED_OUT;
        }
        return status;
    }

    /** The last list received (empty until the first reply). */
    public static List<Chunk> chunks() {
        return chunks;
    }
}
