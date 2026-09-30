package com.xiaofeiwu.cmdhelper.client.locate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Picks the label shown for a structure / biome / point-of-interest id in the locate screen's
 * dropdown, so it reads "平原村庄 [minecraft:village_plains]" instead of a bare id.
 *
 * A Chinese name is looked for in this order: what the player saved, this mod's vanilla table, then
 * the translation keys a mod might ship. If nothing is found the label falls back to the mod's
 * display name plus the id, so at least it's clear whose it is. No Minecraft classes: the lookups
 * are passed in.
 */
public final class LocateLabeler {

    public enum Kind {STRUCTURE, BIOME, POI}

    /** Translation lookup; returns null when the key doesn't exist (never the key itself). */
    public interface Lang {
        String translate(String key);
    }

    private final Function<String, String> savedNames;
    private final Function<String, String> modDisplayName;
    private final Lang lang;

    public LocateLabeler(Function<String, String> savedNames, Function<String, String> modDisplayName, Lang lang) {
        this.savedNames = savedNames;
        this.modDisplayName = modDisplayName;
        this.lang = lang;
    }

    /** The Chinese name we know for this id, or null if there isn't one. */
    public String nameOf(Kind kind, String id) {
        String saved = usable(savedNames.apply(id));
        if (saved != null) {
            return saved;
        }
        String builtIn = switch (kind) {
            case STRUCTURE -> VanillaLocateNames.STRUCTURES.get(id);
            case POI -> VanillaLocateNames.POI_TYPES.get(id);
            case BIOME -> null;
        };
        if (builtIn != null) {
            return builtIn;
        }
        for (String key : langKeys(kind, id)) {
            String translated = usable(lang.translate(key));
            if (translated != null) {
                return translated;
            }
        }
        return null;
    }

    public String label(Kind kind, String id) {
        String name = nameOf(kind, id);
        if (name != null) {
            return name + " [" + id + "]";
        }
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        String mod = usable(modDisplayName.apply(namespace));
        return (mod != null ? mod : namespace) + " · " + path.replace('_', ' ').replace('/', ' ') + " [" + id + "]";
    }

    /** Translation keys a mod might reasonably use, most likely first. */
    static List<String> langKeys(Kind kind, String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return List.of();
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        String dotted = path.replace('/', '.');
        List<String> keys = new ArrayList<>();
        String[] prefixes = switch (kind) {
            case STRUCTURE -> new String[]{"structure.", "structures.", "worldgen.structure."};
            case BIOME -> new String[]{"biome."};
            case POI -> new String[]{"poi.", "point_of_interest."};
        };
        for (String prefix : prefixes) {
            keys.add(prefix + ns + "." + dotted);
        }
        String noun = switch (kind) {
            case STRUCTURE -> "structure";
            case BIOME -> "biome";
            case POI -> "poi";
        };
        keys.add(ns + "." + noun + "." + dotted);
        if (kind == Kind.POI && ns.equals("minecraft")) {
            keys.add("entity.minecraft.villager." + dotted); // POI types named after villager professions
        }
        return keys;
    }

    private static String usable(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
