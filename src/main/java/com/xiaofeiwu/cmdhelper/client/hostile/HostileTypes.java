package com.xiaofeiwu.cmdhelper.client.hostile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The player's own definition of "hostile" — which mob types count, and how far the one-click clear
 * looks — kept in config/cmdhelper/hostile_types.json.
 *
 * Minecraft has no command-visible notion of "hostile" (no selector field, no entity tag, no NBT),
 * so this mod keeps the definition itself — and lets the player edit it, since which mobs are
 * "enemies" is a matter of taste (endermen? piglins?) and modded mobs need adding by hand.
 *
 * File format: {"types":[...],"range":16}. The first version of this file was a bare JSON array of
 * ids; that is still read (range falls back to the default) and gets rewritten in the new shape on
 * the next change. The file is only written once the player changes something; until then the defaults apply, so
 * a future change to the defaults still reaches anyone who never customised the list.
 */
public final class HostileTypes {

    /** What the list starts as: the everyday overworld/nether nuisances. */
    public static final List<String> DEFAULTS = List.of(
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:slime",
            "minecraft:phantom",
            "minecraft:enderman",
            "minecraft:zombie_villager",
            "minecraft:drowned"
    );

    public static final int DEFAULT_RANGE = 16;
    public static final int MAX_RANGE = 9999;

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Gson GSON = new Gson();

    private static HostileTypes shared;

    private final Path file;
    private final List<String> ids;
    private int range = DEFAULT_RANGE;

    /** Separate from the singleton so tests can point it at a temp file. */
    public HostileTypes(Path file) {
        this.file = file;
        this.ids = new ArrayList<>(DEFAULTS);
        load();
    }

    public static synchronized HostileTypes shared() {
        if (shared == null) {
            shared = new HostileTypes(FMLPaths.CONFIGDIR.get().resolve("cmdhelper").resolve("hostile_types.json"));
        }
        return shared;
    }

    /** How many blocks around the player the one-click clear looks. */
    public int range() {
        return range;
    }

    /** Clamped to 1..{@value #MAX_RANGE}; saved straight away. */
    public void setRange(int blocks) {
        int clamped = Math.max(1, Math.min(MAX_RANGE, blocks));
        if (clamped != range) {
            range = clamped;
            save();
        }
    }

    public List<String> ids() {
        return Collections.unmodifiableList(ids);
    }

    public boolean contains(String id) {
        String normalized = normalize(id);
        return normalized != null && ids.contains(normalized);
    }

    /** Fast path for per-frame checks: the id must already be normalised (registry ids are). */
    public boolean containsNormalized(String id) {
        return ids.contains(id);
    }

    /** @return true if the list changed (false for duplicates and malformed ids) */
    public boolean add(String id) {
        String normalized = normalize(id);
        if (normalized == null || ids.contains(normalized)) {
            return false;
        }
        ids.add(normalized);
        save();
        return true;
    }

    public boolean remove(String id) {
        String normalized = normalize(id);
        if (normalized == null || !ids.remove(normalized)) {
            return false;
        }
        save();
        return true;
    }

    /** Adds the id if absent, removes it if present. @return true if it is in the list afterwards */
    public boolean toggle(String id) {
        if (contains(id)) {
            remove(id);
            return false;
        }
        return add(id);
    }

    public void resetToDefaults() {
        ids.clear();
        ids.addAll(DEFAULTS);
        save();
    }

    /** Lower-cases, trims and defaults the namespace to minecraft; null if it can't be a valid id. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return null;
        }
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        return VALID_ID.matcher(id).matches() ? id : null;
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonArray types = null;
            if (root.isJsonArray()) {
                types = root.getAsJsonArray();            // first-version file: just the ids
            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                if (object.has("types") && object.get("types").isJsonArray()) {
                    types = object.getAsJsonArray("types");
                }
                if (object.has("range") && object.get("range").isJsonPrimitive()) {
                    try {
                        range = Math.max(1, Math.min(MAX_RANGE, object.get("range").getAsInt()));
                    } catch (NumberFormatException ignored) {
                        // A mangled range must not throw the player's type list away with it.
                    }
                }
            }
            if (types != null) {
                Set<String> result = new LinkedHashSet<>();
                for (JsonElement element : types) {
                    String id = element.isJsonPrimitive() ? normalize(element.getAsString()) : null;
                    if (id != null) {
                        result.add(id);
                    }
                }
                // An explicitly emptied list is a valid choice and must stay empty.
                ids.clear();
                ids.addAll(result);
            }
        } catch (Exception ignored) {
            // Corrupt file: fall back to the defaults rather than break the kill menu.
            ids.clear();
            ids.addAll(DEFAULTS);
            range = DEFAULT_RANGE;
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject object = new JsonObject();
            object.add("types", GSON.toJsonTree(ids));
            object.addProperty("range", range);
            Files.writeString(file, GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort, same as the history file: losing the edit isn't worth a crash.
        }
    }
}
