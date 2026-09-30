package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Small shared helper for building the "按模组筛选" dropdown's option list. The actual
 * item/block/entity picker is {@link RegistryGridWidget}.
 */
public final class RegistrySearchWidget {

    /** Shown in the mod-filter dropdown to mean "don't filter by mod". */
    public static final String ALL_MODS = "全部模组";

    private RegistrySearchWidget() {
    }

    /** Distinct mod display names present in {@code entries}, in the same vanilla-first order they're stored in. */
    public static List<String> modNamesIn(List<RegistryEntry> entries) {
        List<String> names = entries.stream().map(RegistryEntry::modName).distinct().collect(Collectors.toList());
        List<String> result = new ArrayList<>();
        result.add(ALL_MODS);
        result.addAll(names);
        return result;
    }
}
