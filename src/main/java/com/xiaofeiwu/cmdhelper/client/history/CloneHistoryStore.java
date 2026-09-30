package com.xiaofeiwu.cmdhelper.client.history;

import com.google.gson.Gson;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The /clone commands that were actually sent, newest first, kept in config/cmdhelper/clone_history.json.
 * Separate from the general command history (which keeps only 20 of everything) so a few clones don't
 * push each other out. Only commands this mod can read back are kept, because the clone screen turns
 * an entry back into its fields.
 */
public final class CloneHistoryStore {

    public static final int CAP = 30;

    private static final Gson GSON = new Gson();
    private static CloneHistoryStore shared;

    private final Path file;
    private final List<String> commands = new ArrayList<>();

    /** Separate from the singleton so tests can point it at a temp file. */
    public CloneHistoryStore(Path file) {
        this.file = file;
        load();
    }

    public static synchronized CloneHistoryStore shared() {
        if (shared == null) {
            shared = new CloneHistoryStore(FMLPaths.CONFIGDIR.get().resolve("cmdhelper").resolve("clone_history.json"));
        }
        return shared;
    }

    /** Commands without the leading slash, newest first. */
    public List<String> commands() {
        return Collections.unmodifiableList(commands);
    }

    /** @return false if the command isn't a clone this mod can read back (nothing recorded) */
    public boolean record(String commandWithoutSlash) {
        String command = commandWithoutSlash == null ? "" : commandWithoutSlash.trim().replaceAll("\\s+", " ");
        if (CloneCalc.parse(command).isEmpty()) {
            return false;
        }
        commands.remove(command);
        commands.add(0, command);
        while (commands.size() > CAP) {
            commands.remove(commands.size() - 1);
        }
        save();
        return true;
    }

    public void clear() {
        if (!commands.isEmpty()) {
            commands.clear();
            save();
        }
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                String[] stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), String[].class);
                if (stored != null) {
                    for (String command : Arrays.asList(stored)) {
                        if (command != null && CloneCalc.parse(command).isPresent() && !commands.contains(command)) {
                            commands.add(command);
                        }
                    }
                    while (commands.size() > CAP) {
                        commands.remove(commands.size() - 1);
                    }
                }
            }
        } catch (Exception ignored) {
            commands.clear(); // unreadable file: start empty rather than break the screen
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(commands), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best effort, like the other config files
        }
    }
}
