package com.xiaofeiwu.cmdhelper.client.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchMatcherTest {

    @Test
    void emptyQuery_matchesEverything() {
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", ""));
    }

    @Test
    void matchesByChineseDisplayName() {
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", "苦力怕"));
    }

    @Test
    void matchesByPartialChineseName_evenWhenNotAPrefix() {
        // "皇家苦力怕" should still turn up when searching the base mob name, like vanilla "苦力怕"
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", "苦力怕"));
        assertTrue(SearchMatcher.matches("冰霜苦力怕", "xxxmod:frost_creeper", "XXX Mod", "苦力怕"));
    }

    @Test
    void matchesByEnglishId() {
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", "royal_creeper"));
    }

    @Test
    void matchesByIdPathWithSpacesInsteadOfUnderscores() {
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", "royal creeper"));
    }

    @Test
    void matchesByModNamespacePrefix_listsWholeMod() {
        assertTrue(SearchMatcher.matches("皇家苦力怕", "xxxmod:royal_creeper", "XXX Mod", "xxxmod:"));
    }

    @Test
    void matchesByModDisplayName() {
        assertTrue(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "minecraft"));
    }

    @Test
    void isCaseInsensitive() {
        assertTrue(SearchMatcher.matches("Diamond Sword", "minecraft:diamond_sword", "Minecraft", "DIAMOND"));
    }

    @Test
    void multipleKeywords_matchInAnyOrder() {
        assertTrue(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "stone slab"));
        assertTrue(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "slab stone"));
        assertTrue(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "石头 台阶"));
    }

    @Test
    void multipleKeywords_allMustMatch() {
        assertFalse(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "stone stairs"));
    }

    @Test
    void surroundingWhitespace_isIgnored() {
        assertTrue(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "  石头  "));
        assertTrue(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "   "));
    }

    @Test
    void lowerCasing_isLocaleIndependent() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            // In a Turkish default locale "I".toLowerCase() is dotless "ı" and would miss "iron".
            assertTrue(SearchMatcher.matches("Iron Ingot", "minecraft:iron_ingot", "Minecraft", "IRON"));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void keywordCannotMatchAcrossFieldBoundaries() {
        // display name ends "ab", id starts "cd" — "abcd" must not match the joined text.
        assertFalse(SearchMatcher.matches("ab", "cd:x", "M", "abcd"));
    }

    @Test
    void unrelatedQuery_doesNotMatch() {
        assertFalse(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "苦力怕"));
    }
}
