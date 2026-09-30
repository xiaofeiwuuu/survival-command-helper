package com.xiaofeiwu.cmdhelper.client.registry;

import java.util.Locale;

/**
 * Plain-string search matching, kept free of any Minecraft/Forge class so it can be
 * unit tested without booting the game.
 *
 * An entry's searchable text is folded into one lower-cased "key" once, when the entry is
 * created — a typed query then costs a single contains() per keyword per entry instead of
 * re-lowering and re-splitting four strings on every keystroke for every entry in the registry.
 * A query is split on whitespace and every keyword must be found, in any order. For names
 * containing Chinese, the key also carries their pinyin (full and initials), so a player can
 * type "yuanmu" or "ym" to find 原木.
 */
public final class SearchMatcher {

    // Fields are joined with a character that can't appear in a typed query, so a keyword
    // can never accidentally match across the boundary between two fields.
    private static final char FIELD_SEPARATOR = '\n';

    private SearchMatcher() {
    }

    public static String searchKey(String displayName, String idString, String modName) {
        String path = idString.contains(":") ? idString.substring(idString.indexOf(':') + 1) : idString;
        StringBuilder key = new StringBuilder(displayName.length() + idString.length() + modName.length() + 16);
        key.append(displayName).append(FIELD_SEPARATOR)
                .append(idString).append(FIELD_SEPARATOR)
                .append(path.replace('_', ' ')).append(FIELD_SEPARATOR)
                .append(modName);
        String lowered = key.toString().toLowerCase(Locale.ROOT);
        return withPinyin(lowered, displayName, modName);
    }

    /** Same folding for a single piece of text, e.g. a dropdown option's label. */
    public static String textKey(String text) {
        return withPinyin(text.toLowerCase(Locale.ROOT), text);
    }

    // Pinyin goes in as extra fields after the real ones, so typing "shitou" finds 石头 while a
    // keyword still can't straddle two fields. Names with no Chinese add nothing at all.
    private static String withPinyin(String lowered, String... sources) {
        StringBuilder out = null;
        for (String source : sources) {
            for (String term : Pinyin.searchTerms(source)) {
                if (out == null) {
                    out = new StringBuilder(lowered);
                }
                out.append(FIELD_SEPARATOR).append(term);
            }
        }
        return out == null ? lowered : out.toString();
    }

    /** Lower-cases and splits once, so the caller can reuse the result across every entry. */
    public static String[] keywords(String query) {
        if (query == null) {
            return new String[0];
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? new String[0] : trimmed.toLowerCase(Locale.ROOT).split("\\s+");
    }

    public static boolean matchesKey(String searchKey, String[] keywords) {
        for (String keyword : keywords) {
            if (!searchKey.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matches(String displayName, String idString, String modName, String query) {
        return matchesKey(searchKey(displayName, idString, modName), keywords(query));
    }
}
