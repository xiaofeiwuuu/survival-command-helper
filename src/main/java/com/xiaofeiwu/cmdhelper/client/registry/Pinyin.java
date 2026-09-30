package com.xiaofeiwu.cmdhelper.client.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns Chinese text into the pinyin strings a player would type to find it: the full
 * spelling ("shitou" for 石头) and the initials ("st"). Backed by a small bundled table
 * (assets/cmdhelper/pinyin.txt, GB2312 characters, derived from MIT-licensed
 * mozillazg/pinyin-data) — no Minecraft/Forge class, so it's unit-testable.
 *
 * Polyphonic characters: the table keeps a second reading only where it's genuinely common
 * (长 zhang/chang, 传 chuan/zhuan ...). Each such character yields one extra variant of the
 * whole text with the alternative reading swapped in, so 长矛 is found by both "zhangmao"
 * and "changmao" without exploding into every combination.
 */
public final class Pinyin {

    private static final String RESOURCE = "/assets/cmdhelper/pinyin.txt";
    // A name with many polyphonic characters would otherwise spawn many near-identical variants.
    private static final int MAX_ALTERNATE_VARIANTS = 4;

    private Pinyin() {
    }

    /** Lazy holder: the table is read once, the first time anything asks for pinyin. */
    private static final class Table {
        static final Map<Character, String[]> READINGS = load();

        private static Map<Character, String[]> load() {
            Map<Character, String[]> map = new HashMap<>(8192);
            try (InputStream in = Pinyin.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    return map; // no table bundled: search just falls back to non-pinyin matching
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#' || line.length() < 3 || line.charAt(1) != ':') {
                        continue;
                    }
                    map.put(line.charAt(0), line.substring(2).split(","));
                }
            } catch (IOException e) {
                // A broken table must never take the menus down with it.
            }
            return map;
        }
    }

    /**
     * Lower-case search strings for {@code text}: for each reading variant, its full pinyin
     * followed by its initials. Empty when the text has no Chinese character we know, so plain
     * English names cost nothing.
     */
    public static List<String> searchTerms(String text) {
        Map<Character, String[]> table = Table.READINGS;
        boolean anyKnown = false;
        for (int i = 0; i < text.length(); i++) {
            if (table.containsKey(text.charAt(i))) {
                anyKnown = true;
                break;
            }
        }
        if (!anyKnown) {
            return List.of();
        }

        Set<String> terms = new LinkedHashSet<>();
        addVariant(terms, text, table, -1);
        int alternates = 0;
        for (int i = 0; i < text.length() && alternates < MAX_ALTERNATE_VARIANTS; i++) {
            String[] readings = table.get(text.charAt(i));
            if (readings != null && readings.length > 1) {
                addVariant(terms, text, table, i);
                alternates++;
            }
        }
        return List.copyOf(terms);
    }

    /** @param alternateAt index whose second reading to use, or -1 for the main reading everywhere */
    private static void addVariant(Set<String> terms, String text, Map<Character, String[]> table, int alternateAt) {
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String[] readings = table.get(c);
            if (readings != null) {
                String reading = (i == alternateAt && readings.length > 1) ? readings[1] : readings[0];
                full.append(reading);
                initials.append(reading.charAt(0));
            } else if (Character.isLetterOrDigit(c)) {
                // Latin letters / digits / characters outside the table stand for themselves,
                // so "皇家Boss" is still findable as "huangjiaboss".
                char lower = Character.toLowerCase(c);
                full.append(lower);
                initials.append(lower);
            }
            // spaces and punctuation are dropped: nobody types them into a pinyin query
        }
        terms.add(full.toString().toLowerCase(Locale.ROOT));
        terms.add(initials.toString().toLowerCase(Locale.ROOT));
    }
}
