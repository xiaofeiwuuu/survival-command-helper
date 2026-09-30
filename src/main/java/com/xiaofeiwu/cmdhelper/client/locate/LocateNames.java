package com.xiaofeiwu.cmdhelper.client.locate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chinese names the player has given to structures / biomes / points of interest that have none
 * (mostly modded ones — no table shipped with this mod can know them), kept in
 * config/cmdhelper/locate_names.json as {"abridged:bridge": "拱桥", ...}. The file is written only
 * when the player saves a name; it can also be edited by hand.
 */
public final class LocateNames {

    public static final int MAX_NAME_LENGTH = 30;

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Gson GSON = new Gson();

    private static LocateNames shared;

    private final Path file;
    private final Map<String, String> names = new LinkedHashMap<>();

    /** Separate from the singleton so tests can point it at a temp file. */
    public LocateNames(Path file) {
        this.file = file;
        load();
    }

    public static synchronized LocateNames shared() {
        if (shared == null) {
            shared = new LocateNames(FMLPaths.CONFIGDIR.get().resolve("cmdhelper").resolve("locate_names.json"));
        }
        return shared;
    }

    /** The saved name for an id, or null. */
    public String get(String id) {
        String key = normalizeId(id);
        return key == null ? null : names.get(key);
    }

    public Map<String, String> all() {
        return Collections.unmodifiableMap(names);
    }

    /**
     * Saves a name; a blank name removes the entry (so "clear the box and save" resets it).
     * @return false if the id isn't a valid resource id (nothing changed)
     */
    public boolean set(String id, String name) {
        String key = normalizeId(id);
        if (key == null) {
            return false;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }
        if (trimmed.isEmpty()) {
            if (names.remove(key) != null) {
                save();
            }
        } else if (!trimmed.equals(names.put(key, trimmed))) {
            save();
        }
        return true;
    }

    static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return VALID_ID.matcher(id).matches() ? id : null;
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                String key = normalizeId(entry.getKey());
                if (key != null && entry.getValue().isJsonPrimitive()) {
                    String value = entry.getValue().getAsString().trim();
                    if (!value.isEmpty()) {
                        names.put(key, value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value);
                    }
                }
            }
        } catch (Exception ignored) {
            names.clear(); // unreadable file: behave as if there were none rather than break the screen
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject object = new JsonObject();
            names.forEach(object::addProperty);
            Files.writeString(file, GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best effort, like the other config files
        }
    }
}
