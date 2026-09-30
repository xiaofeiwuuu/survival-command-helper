package com.xiaofeiwu.cmdhelper.client.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatResultCaptureTest {

    private static final Set<String> KEYS = Set.of("commands.forceload.added.none", "argument.pos.unloaded");

    @Test
    void aSuccessMessage_isTheTranslatableItself() {
        assertTrue(ChatResultCapture.containsKey(Component.translatable("commands.forceload.added.none", "x"), KEYS));
    }

    @Test
    void aFailureMessage_isWrappedByTheServer_andStillMatches() {
        // CommandSourceStack.sendFailure: Component.empty().append(message).withStyle(RED)
        Component wrapped = Component.empty().append(Component.translatable("argument.pos.unloaded")).withStyle(ChatFormatting.RED);
        assertTrue(ChatResultCapture.containsKey(wrapped, KEYS));
    }

    @Test
    void deeplyNestedMessagesMatchToo() {
        Component nested = Component.empty().append(Component.empty().append(Component.translatable("argument.pos.unloaded")));
        assertTrue(ChatResultCapture.containsKey(nested, KEYS));
    }

    @Test
    void otherMessagesDoNotMatch() {
        assertFalse(ChatResultCapture.containsKey(Component.translatable("chat.type.text", "a", "hi"), KEYS));
        assertFalse(ChatResultCapture.containsKey(Component.literal("该位置尚未被加载"), KEYS)); // the words alone are not the key
        assertFalse(ChatResultCapture.containsKey(Component.empty(), KEYS));
    }
}
