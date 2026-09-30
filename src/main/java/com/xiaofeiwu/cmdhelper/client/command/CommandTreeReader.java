package com.xiaofeiwu.cmdhelper.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the command tree the server actually sent this client (the same data that
 * drives chat tab-completion), so parameter rules like "count must be 1..99" or
 * "/fill accepts replace/keep/destroy" come from the live server instead of being
 * hand-copied into this mod and going stale when a command's rules change.
 */
public final class CommandTreeReader {

    private CommandTreeReader() {
    }

    /** Walks a dot-free path of literal/argument names, e.g. "give", "targets", "item", "count". */
    public static Optional<CommandNode<SharedSuggestionProvider>> findNode(String... path) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Optional.empty();
        }
        CommandNode<SharedSuggestionProvider> node = connection.getCommands().getRoot();
        for (String segment : path) {
            if (node == null) {
                return Optional.empty();
            }
            node = node.getChild(segment);
        }
        return Optional.ofNullable(node);
    }

    /** For a node like /fill's mode slot, where each choice is a sibling literal (replace/keep/destroy/...). */
    public static List<String> literalChildNames(CommandNode<SharedSuggestionProvider> node) {
        List<String> names = new ArrayList<>();
        for (CommandNode<SharedSuggestionProvider> child : node.getChildren()) {
            if (child instanceof LiteralCommandNode<SharedSuggestionProvider> literal) {
                names.add(literal.getLiteral());
            }
        }
        return names;
    }

    /** Reads the real min/max Brigadier enforces for an integer argument node, if that's what it is. */
    public static Optional<int[]> integerBounds(CommandNode<SharedSuggestionProvider> node) {
        if (node instanceof com.mojang.brigadier.tree.ArgumentCommandNode<SharedSuggestionProvider, ?> argNode
                && argNode.getType() instanceof IntegerArgumentType intType) {
            return Optional.of(new int[]{intType.getMinimum(), intType.getMaximum()});
        }
        return Optional.empty();
    }
}
