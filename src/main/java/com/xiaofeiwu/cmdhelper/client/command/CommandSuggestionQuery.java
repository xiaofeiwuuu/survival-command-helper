package com.xiaofeiwu.cmdhelper.client.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Some command arguments (structure/biome/poi ids, recipe names, sound ids, ...) aren't backed
 * by a registry the client has locally — Brigadier resolves them by asking the server for
 * completions at the given cursor position, exactly like pressing Tab in chat. This replays
 * that same request/response round trip programmatically, so a screen can populate a dropdown
 * with the server's real, mod-inclusive answer instead of a hand-maintained vanilla-only list.
 */
public final class CommandSuggestionQuery {

    private CommandSuggestionQuery() {
    }

    /** partialCommand should end where completions are wanted, e.g. "locate structure " (no leading slash). */
    public static CompletableFuture<List<String>> suggest(String partialCommand) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        SharedSuggestionProvider source = connection.getSuggestionsProvider();
        var parsed = connection.getCommands().parse(new StringReader(partialCommand), source);
        return connection.getCommands().getCompletionSuggestions(parsed, partialCommand.length())
                .thenApply(suggestions -> suggestions.getList().stream()
                        .map(Suggestion::getText)
                        .collect(Collectors.toList()))
                .exceptionally(e -> List.of());
    }
}
