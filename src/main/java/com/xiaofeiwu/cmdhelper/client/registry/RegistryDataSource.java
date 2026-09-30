package com.xiaofeiwu.cmdhelper.client.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads item / block / entity-type data straight from the live Forge registries
 * (vanilla + every loaded mod) instead of maintaining a hand-written ID list.
 */
public final class RegistryDataSource {

    private static final Map<String, String> MOD_NAME_CACHE = new ConcurrentHashMap<>();

    private static List<RegistryEntry> items;
    private static List<RegistryEntry> blocks;
    private static List<RegistryEntry> entityTypes;
    private static List<RegistryEntry> livingEntityTypes;
    private static Map<String, String> itemNames;
    private static Map<String, String> blockNames;
    private static Map<String, String> entityNames;
    private static List<String> blockIds;
    private static Map<String, String> blockLabels;

    // Air/water/lava have no item form, so they'd otherwise sink into the alphabetical
    // middle of the block list — yet "fill with air" (clear an area) is the most common /fill.
    private static final List<String> PINNED_BLOCKS = List.of("minecraft:air", "minecraft:water", "minecraft:lava");

    private RegistryDataSource() {
    }

    /** Drops every cached list. Display names are baked in at build time, so anything that
     *  changes them (switching language, reloading resource packs) has to call this. */
    public static void invalidate() {
        items = null;
        blocks = null;
        entityTypes = null;
        livingEntityTypes = null;
        blockIds = null;
        blockLabels = null;
        itemNames = null;
        blockNames = null;
        entityNames = null;
        MOD_NAME_CACHE.clear();
    }

    public static List<RegistryEntry> items() {
        if (items == null) {
            items = build(ForgeRegistries.ITEMS.getEntries(), RegistryEntry.Kind.ITEM,
                    item -> Component.translatable(item.getDescriptionId()).getString());
        }
        return items;
    }

    public static List<RegistryEntry> blocks() {
        if (blocks == null) {
            List<RegistryEntry> built = build(ForgeRegistries.BLOCKS.getEntries(), RegistryEntry.Kind.BLOCK,
                    block -> Component.translatable(block.getDescriptionId()).getString());
            List<RegistryEntry> pinned = new ArrayList<>();
            for (String id : PINNED_BLOCKS) {
                built.stream().filter(e -> e.idString().equals(id)).findFirst().ifPresent(pinned::add);
            }
            built.removeAll(pinned);
            built.addAll(0, pinned);
            blocks = built;
        }
        return blocks;
    }

    /** Display name for a registry id, or the id itself when this game doesn't know it. Backed by
     *  the same cached lists as the pickers, so explaining a command costs a map lookup. */
    public static String nameOf(RegistryEntry.Kind kind, String idString) {
        Map<String, String> names = switch (kind) {
            case ITEM -> itemNames != null ? itemNames : (itemNames = index(items()));
            case BLOCK -> blockNames != null ? blockNames : (blockNames = index(blocks()));
            case ENTITY_TYPE -> entityNames != null ? entityNames : (entityNames = index(entityTypes()));
        };
        return names.getOrDefault(idString, idString);
    }

    private static Map<String, String> index(List<RegistryEntry> entries) {
        Map<String, String> map = new HashMap<>(entries.size() * 2);
        for (RegistryEntry e : entries) {
            map.put(e.idString(), e.displayName());
        }
        return map;
    }

    /** Every block id, in {@link #blocks()} order — the option list for "replace only this block". */
    public static List<String> blockIds() {
        if (blockIds == null) {
            blockIds = blocks().stream().map(RegistryEntry::idString).toList();
        }
        return blockIds;
    }

    /** id -> "显示名 [id]" for {@link #blockIds()}, built once instead of on every screen open. */
    public static Map<String, String> blockLabels() {
        if (blockLabels == null) {
            Map<String, String> labels = new HashMap<>();
            for (RegistryEntry e : blocks()) {
                labels.put(e.idString(), e.displayName() + " [" + e.idString() + "]");
            }
            blockLabels = labels;
        }
        return blockLabels;
    }

    /** Every non-player entity type — used where summoning/targeting a minecart, boat, TNT,
     *  armor stand etc. is a legitimate thing to want (e.g. /summon). */
    public static List<RegistryEntry> entityTypes() {
        if (entityTypes == null) {
            entityTypes = build(ForgeRegistries.ENTITY_TYPES.getEntries(), RegistryEntry.Kind.ENTITY_TYPE,
                    type -> type.getDescription().getString());
            // "player" is a registered entity type but isn't a real, pickable target here:
            // /summon rejects it outright, and letting it show up in a "kill nearby mobs"
            // search is just an invitation to accidentally target yourself or someone else.
            entityTypes.removeIf(e -> e.id().toString().equals("minecraft:player"));
        }
        return entityTypes;
    }

    /** Same as {@link #entityTypes()} but also drops MobCategory.MISC — minecarts, boats, TNT,
     *  armor stands, item frames, paintings, falling blocks, XP orbs, end crystals... none of
     *  them are "生物" a player means by "kill nearby mobs", so a kill picker shouldn't show them. */
    public static List<RegistryEntry> livingEntityTypes() {
        if (livingEntityTypes == null) {
            livingEntityTypes = new ArrayList<>(entityTypes());
            livingEntityTypes.removeIf(e -> {
                var type = ForgeRegistries.ENTITY_TYPES.getValue(e.id());
                return type != null && type.getCategory() == MobCategory.MISC;
            });
        }
        return livingEntityTypes;
    }

    private static <T> List<RegistryEntry> build(
            java.util.Collection<Map.Entry<net.minecraft.resources.ResourceKey<T>, T>> entries,
            RegistryEntry.Kind kind,
            java.util.function.Function<T, String> displayNameFn
    ) {
        // Vanilla first (it's what most people are looking for by default), then everything
        // else grouped by mod, with a locale-aware collator so Chinese names sort sensibly
        // instead of by raw UTF-16 code point (which would push every Chinese name behind
        // every ASCII one and bury "原木" under a pile of English/symbol-prefixed mod items).
        // Collator.compare is slow, and a sort makes ~n·log n of them — so each name is turned
        // into a CollationKey once up front and the sort compares those instead.
        Collator collator = Collator.getInstance(Locale.CHINA);
        Map<String, CollationKey> modKeys = new HashMap<>();
        List<Sortable> sortables = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<T>, T> entry : entries) {
            ResourceLocation id = entry.getKey().location();
            String name;
            try {
                name = displayNameFn.apply(entry.getValue());
            } catch (Exception e) {
                name = id.getPath();
            }
            RegistryEntry built = new RegistryEntry(id, name, modName(id.getNamespace()), kind);
            sortables.add(new Sortable(built,
                    !id.getNamespace().equals("minecraft"),
                    modKeys.computeIfAbsent(built.modName(), collator::getCollationKey),
                    collator.getCollationKey(name)));
        }
        sortables.sort(Comparator
                .comparing(Sortable::notVanilla)
                .thenComparing(Sortable::modKey)
                .thenComparing(Sortable::nameKey));
        List<RegistryEntry> result = new ArrayList<>(sortables.size());
        for (Sortable sortable : sortables) {
            result.add(sortable.entry());
        }
        return result;
    }

    private record Sortable(RegistryEntry entry, boolean notVanilla, CollationKey modKey, CollationKey nameKey) {
    }

    /** The mod's display name for a namespace ("abridged" -> "Abridged"), or the namespace itself. */
    public static String modNameOf(String namespace) {
        return modName(namespace);
    }

    private static String modName(String namespace) {
        return MOD_NAME_CACHE.computeIfAbsent(namespace, ns ->
                ModList.get().getModContainerById(ns)
                        .map(c -> c.getModInfo().getDisplayName())
                        .orElse(ns));
    }
}
