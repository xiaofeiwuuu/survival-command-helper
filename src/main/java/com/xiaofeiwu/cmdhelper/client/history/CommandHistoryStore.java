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
 *
 * The state lives in an instance (so it can be pointed at a temp directory in tests); the static
 * methods below are the shared instance the rest of the mod uses.
 */
public final class CommandHistoryStore {

    private static final int HISTORY_CAP = 20;
    private static final int FAVORITES_CAP = 50;
    private static final Gson GSON = new Gson();

    private static CommandHistoryStore shared;

    private final Path historyFile;
    private final Path favoritesFile;
    private final List<String> history;
    private final List<String> favorites;

    public CommandHistoryStore(Path dir) {
        this.historyFile = dir.resolve("history.json");
        this.favoritesFile = dir.resolve("favorites.json");
        this.history = readList(historyFile);
        this.favorites = readList(favoritesFile);
    }

    private static synchronized CommandHistoryStore shared() {
        if (shared == null) {
            shared = new CommandHistoryStore(FMLPaths.CONFIGDIR.get().resolve("cmdhelper"));
        }
        return shared;
    }

    // ---- the shared instance, as the rest of the mod calls it -------------------------------------

    public static List<String> history() {
        return shared().historyList();
    }

    public static List<String> favorites() {
        return shared().favoritesList();
    }

    public static void record(String commandWithoutSlash) {
        shared().add(commandWithoutSlash);
    }

    public static boolean isFavorite(String commandWithoutSlash) {
        return shared().favorite(commandWithoutSlash);
    }

    public static void toggleFavorite(String commandWithoutSlash) {
        shared().flipFavorite(commandWithoutSlash);
    }

    public static void clearHistory() {
        shared().clearAllHistory();
    }

    public static boolean removeFromHistory(String commandWithoutSlash) {
        return shared().removeOne(commandWithoutSlash);
    }

    // ---- the logic -----------------------------------------------------------------------------------

    public List<String> historyList() {
        return history;
    }

    public List<String> favoritesList() {
        return favorites;
    }

    public void add(String commandWithoutSlash) {
        history.remove(commandWithoutSlash);
        history.add(0, commandWithoutSlash);
        while (history.size() > HISTORY_CAP) {
            history.remove(history.size() - 1);
        }
        save(historyFile, history);
    }

    public boolean favorite(String commandWithoutSlash) {
        return favorites.contains(commandWithoutSlash);
    }

    public void flipFavorite(String commandWithoutSlash) {
        if (!favorites.remove(commandWithoutSlash)) {
            favorites.add(0, commandWithoutSlash);
            while (favorites.size() > FAVORITES_CAP) {
                favorites.remove(favorites.size() - 1);
            }
        }
        save(favoritesFile, favorites);
    }

    /** Empties the history list only. Starred commands are kept: clearing the log isn't meant to lose them. */
    public void clearAllHistory() {
        if (!history.isEmpty()) {
            history.clear();
            save(historyFile, history);
        }
    }

    /** Drops one entry from the history list (favorites untouched). @return whether it was there */
    public boolean removeOne(String commandWithoutSlash) {
        boolean removed = history.remove(commandWithoutSlash);
        if (removed) {
            save(historyFile, history);
        }
        return removed;
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
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(list), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort persistence; losing history isn't worth crashing over.
        }
    }
}
