package com.xiaofeiwu.cmdhelper.client.blueprint;

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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Blueprints on disk: one JSON file each under config/cmdhelper/blueprints/. Readable and editable by
 * hand, and a file copied to another save's config folder is all it takes to move a blueprint.
 */
public final class BlueprintStore {

    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new Gson();
    private static BlueprintStore shared;

    private final Path dir;

    /** Separate from the singleton so tests can use a temp directory. */
    public BlueprintStore(Path dir) {
        this.dir = dir;
    }

    public static synchronized BlueprintStore shared() {
        if (shared == null) {
            shared = new BlueprintStore(FMLPaths.CONFIGDIR.get().resolve("cmdhelper").resolve("blueprints"));
        }
        return shared;
    }

    /** A name that is safe as a file name on every system; never empty. */
    static String fileSafe(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60).trim();
        }
        return cleaned.isEmpty() ? "蓝图" : cleaned;
    }

    private Path fileFor(String name) {
        return dir.resolve(fileSafe(name) + ".json");
    }

    /**
     * Saves under the given name; if that name is taken a " (2)", " (3)"... is added rather than
     * overwriting someone's earlier scan.
     * @return the blueprint as saved (its name may differ from the one passed in)
     */
    public Blueprint save(Blueprint blueprint) throws IOException {
        String base = fileSafe(blueprint.name());
        String name = base;
        for (int n = 2; Files.exists(fileFor(name)); n++) {
            name = base + " (" + n + ")";
        }
        Blueprint saved = new Blueprint(name, blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(),
                blueprint.palette(), blueprint.cuboids(), blueprint.createdAtMillis());
        Files.createDirectories(dir);
        Files.writeString(fileFor(name), GSON.toJson(toJson(saved)), StandardCharsets.UTF_8);
        return saved;
    }

    /** All readable blueprints, newest first. Files that can't be read are skipped, not fatal. */
    public List<Blueprint> list() {
        List<Blueprint> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                try {
                    result.add(fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()));
                } catch (Exception ignored) {
                    // a broken or foreign file: leave it alone
                }
            }
        } catch (IOException ignored) {
            // unreadable folder: show nothing rather than fail the screen
        }
        result.sort(Comparator.comparingLong(Blueprint::createdAtMillis).reversed());
        return result;
    }

    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(fileFor(name));
        } catch (IOException e) {
            return false;
        }
    }

    // ---- JSON ---------------------------------------------------------------------------------------

    static JsonObject toJson(Blueprint b) {
        JsonObject o = new JsonObject();
        o.addProperty("version", FORMAT_VERSION);
        o.addProperty("name", b.name());
        o.addProperty("created", b.createdAtMillis());
        JsonArray size = new JsonArray();
        size.add(b.sizeX());
        size.add(b.sizeY());
        size.add(b.sizeZ());
        o.add("size", size);
        JsonArray palette = new JsonArray();
        b.palette().forEach(palette::add);
        o.add("palette", palette);
        // [x0, y0, z0, x1, y1, z1, paletteIndex] per box
        JsonArray boxes = new JsonArray();
        for (Cuboid c : b.cuboids()) {
            JsonArray row = new JsonArray();
            row.add(c.x0());
            row.add(c.y0());
            row.add(c.z0());
            row.add(c.x1());
            row.add(c.y1());
            row.add(c.z1());
            row.add(c.paletteIndex());
            boxes.add(row);
        }
        o.add("cuboids", boxes);
        return o;
    }

    static Blueprint fromJson(JsonObject o) {
        JsonArray size = o.getAsJsonArray("size");
        List<String> palette = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("palette")) {
            palette.add(e.getAsString());
        }
        List<Cuboid> cuboids = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("cuboids")) {
            JsonArray r = e.getAsJsonArray();
            int idx = r.get(6).getAsInt();
            if (idx < 0 || idx >= palette.size()) {
                throw new IllegalArgumentException("palette index out of range");
            }
            cuboids.add(new Cuboid(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(),
                    r.get(3).getAsInt(), r.get(4).getAsInt(), r.get(5).getAsInt(), idx));
        }
        return new Blueprint(o.get("name").getAsString(), size.get(0).getAsInt(), size.get(1).getAsInt(),
                size.get(2).getAsInt(), palette, cuboids, o.has("created") ? o.get("created").getAsLong() : 0L);
    }
}
