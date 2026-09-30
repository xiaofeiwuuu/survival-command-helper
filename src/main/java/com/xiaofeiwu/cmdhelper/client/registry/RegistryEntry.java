package com.xiaofeiwu.cmdhelper.client.registry;

import net.minecraft.resources.ResourceLocation;

public record RegistryEntry(ResourceLocation id, String displayName, String modName, Kind kind, String searchKey) {

    public enum Kind {
        ITEM,
        BLOCK,
        ENTITY_TYPE
    }

    public RegistryEntry(ResourceLocation id, String displayName, String modName, Kind kind) {
        this(id, displayName, modName, kind, SearchMatcher.searchKey(displayName, id.toString(), modName));
    }

    public String idString() {
        return id.toString();
    }

    public boolean matches(String[] keywords) {
        return SearchMatcher.matchesKey(searchKey, keywords);
    }
}
