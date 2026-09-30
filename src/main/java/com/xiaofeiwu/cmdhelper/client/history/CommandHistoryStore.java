package com.xiaofeiwu.cmdhelper.client.history;

import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The last 20 commands run/copied, plus whatever the player has explicitly starred — kept
 * in a tiny JSON file under config/cmdhelper/ so it survives restarting the game. This is
 * deliberately a flat list of raw command strings, not a re-editable form: reversing a
 * command string back into a specific screen's fields would need a small parser per command.
 */
public final class CommandHistoryStore {

    private static final int HISTORY_CAP = 20;
    private static final int FAVORITES_CAP = 50;

    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("cmdhelper");
    private static final Path HISTORY_FILE = DIR.resolve("history.json");
    private static final Path FAVORITES_FILE = DIR.resolve("favorites.json");
    private static final Gson GSON = new Gson();

    private static List<String> history;
    private static List<String> favorites;

    private CommandHistoryStore() {
    }

    public static List<String> history() {
        load();
        return history;
    }

    public static List<String> favorites() {
        load();
        return favorites;
    }

    public static void record(String commandWithoutSlash) {
        load();
        history.remove(commandWithoutSlash);
        history.add(0, commandWithoutSlash);
        while (history.size() > HISTORY_CAP) {
            history.remove(history.size() - 1);
        }
        save(HISTORY_FILE, history);
    }

    public static boolean isFavorite(String commandWithoutSlash) {
        load();
        return favorites.contains(commandWithoutSlash);
    }

    public static void toggleFavorite(String commandWithoutSlash) {
        load();
        if (!favorites.remove(commandWithoutSlash)) {
            favorites.add(0, commandWithoutSlash);
            while (favorites.size() > FAVORITES_CAP) {
                favorites.remove(favorites.size() - 1);
            }
        }
        save(FAVORITES_FILE, favorites);
    }

    private static void load() {
        if (history != null) {
            return;
        }
        history = readList(HISTORY_FILE);
        favorites = readList(FAVORITES_FILE);
    }

    private static List<String> readList(Path file) {
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                String[] arr = GSON.fromJson(json, String[].class);
                if (arr != null) {
                    return new ArrayList<>(Arrays.asList(arr));
                }
            }
        } catch (Exception ignored) {
            // Corrupt/missing file — start fresh rather than crash the menu.
        }
        return new ArrayList<>();
    }

    private static void save(Path file, List<String> list) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(file, GSON.toJson(list), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort persistence; losing history isn't worth crashing over.
        }
    }
}
