package com.xiaofeiwu.cmdhelper.client.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Some commands (/locate, /forceload query, ...) only return their result as a line of chat
 * text, and vanilla chat isn't reliably copyable. This grabs the next system chat message after
 * such a command runs and hands it to whoever's waiting, so a screen can show it inline with a
 * real copy button instead of telling the player to go fish it out of the chat log.
 *
 * Only one capture can be pending at a time; arming a new one replaces whatever was waiting.
 * Filtered to system messages only (ClientChatReceivedEvent.System, non-overlay) so ordinary
 * player chat never triggers it — but an unrelated *system* message (another player joining,
 * a sleep-status broadcast, someone else's command feedback on a shared server) arriving in the
 * split second after the command was sent would still be mistaken for the result. Fine for a
 * singleplayer/LAN world; worth knowing about on a busy shared server.
 */
public final class ChatResultCapture {

    private static Consumer<String> pending;

    // The keyed capture is the precise one: it only reacts to a message whose translation key is on
    // its list (so the wording/language never matters and unrelated chat can't be mistaken for the
    // answer), and it hides that message from chat — the screen that asked shows the answer itself.
    private static Set<String> keyedKeys;
    private static Consumer<Component> keyedCallback;
    private static boolean keyedHidesMessage;

    private ChatResultCapture() {
    }

    /** Arms the capture for the next system chat line; call this right before sending the command. */
    public static void awaitNext(Consumer<String> onResult) {
        pending = onResult;
    }

    /**
     * Waits for the next system message whose translation key is one of {@code keys}, hands it over
     * whole (so the caller can also read its arguments) and removes it from chat. Any other message
     * is left alone. Replaces a previous keyed wait; call {@link #cancelKeyed()} to give up.
     */
    public static void awaitKeyed(Set<String> keys, Consumer<Component> onResult) {
        awaitKeyed(keys, true, onResult);
    }

    /** @param hideMessage false leaves the matched message in chat (the caller only wants to read it) */
    public static void awaitKeyed(Set<String> keys, boolean hideMessage, Consumer<Component> onResult) {
        keyedKeys = keys;
        keyedCallback = onResult;
        keyedHidesMessage = hideMessage;
    }

    public static void cancelKeyed() {
        keyedKeys = null;
        keyedCallback = null;
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (keyedCallback != null && !event.isOverlay()
                && event.getMessage().getContents() instanceof TranslatableContents translatable
                && keyedKeys.contains(translatable.getKey())) {
            Consumer<Component> callback = keyedCallback;
            boolean hide = keyedHidesMessage;
            cancelKeyed();
            if (hide) {
                event.setCanceled(true);
            }
            callback.accept(event.getMessage());
            return;
        }
        if (pending == null || event.isOverlay()) {
            return;
        }
        Consumer<String> callback = pending;
        pending = null;
        callback.accept(event.getMessage().getString());
    }
}
