package com.xiaofeiwu.cmdhelper.client.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinyinTest {

    @Test
    void producesFullPinyinAndInitials() {
        assertEquals(List.of("shitou", "st"), Pinyin.searchTerms("石头"));
    }

    @Test
    void umlautIsWrittenAsV_likeEveryPinyinInputMethod() {
        assertEquals(List.of("lv", "l"), Pinyin.searchTerms("绿"));
        assertEquals(List.of("nv", "n"), Pinyin.searchTerms("女"));
    }

    @Test
    void plainEnglishText_costsNothing() {
        assertTrue(Pinyin.searchTerms("Diamond Sword").isEmpty());
        assertTrue(Pinyin.searchTerms("").isEmpty());
    }

    @Test
    void latinLettersAndDigitsInsideChineseNamesAreKept_punctuationDropped() {
        assertEquals(List.of("huangjiaboss2", "hjboss2"), Pinyin.searchTerms("皇家 Boss-2"));
    }

    @Test
    void commonPolyphone_yieldsBothReadingsAsSeparateVariants() {
        List<String> terms = Pinyin.searchTerms("长矛");
        assertTrue(terms.contains("zhangmao"), terms.toString());
        assertTrue(terms.contains("changmao"), terms.toString());
        assertTrue(terms.contains("zm"));
        assertTrue(terms.contains("cm"));
    }

    @Test
    void rarePolyphoneReadingIsNotAdded() {
        // 石 also reads "dan" in the raw data, but that's not a reading anyone means in a block name.
        assertFalse(Pinyin.searchTerms("石头").stream().anyMatch(t -> t.contains("dan")));
    }

    @Test
    void variantCountIsBounded() {
        // Many polyphonic characters must not multiply into a pile of variants.
        List<String> terms = Pinyin.searchTerms("长长长长长长长长长长");
        assertTrue(terms.size() <= 2 * (1 + 4), "got " + terms.size());
    }

    @Test
    void searchMatcher_findsChineseNamesByFullPinyinAndInitials() {
        assertTrue(SearchMatcher.matches("原木", "minecraft:oak_log", "Minecraft", "yuanmu"));
        assertTrue(SearchMatcher.matches("原木", "minecraft:oak_log", "Minecraft", "ym"));
        assertTrue(SearchMatcher.matches("原木", "minecraft:oak_log", "Minecraft", "yuan"));
    }

    @Test
    void searchMatcher_pinyinKeywordsCombineWithOtherKeywords() {
        assertTrue(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "shi tai"));
        assertTrue(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "st slab"));
        assertFalse(SearchMatcher.matches("石头台阶", "minecraft:stone_slab", "Minecraft", "shi tie"));
    }

    @Test
    void searchMatcher_initialsDoNotMatchAcrossUnrelatedLetters() {
        assertFalse(SearchMatcher.matches("原木", "minecraft:oak_log", "Minecraft", "ymu"));
    }

    @Test
    void searchMatcher_findsChineseModNamesByPinyin() {
        assertTrue(SearchMatcher.matches("Thing", "cmdhelper:thing", "生存指令助手", "zhiling"));
    }

    @Test
    void searchMatcher_englishQueriesStillBehaveAsBefore() {
        assertFalse(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "iron"));
        assertTrue(SearchMatcher.matches("石头", "minecraft:stone", "Minecraft", "stone"));
    }

    @Test
    void textKey_addsPinyinForDropdownLabels() {
        String key = SearchMatcher.textKey("替换: 石头 [minecraft:stone]");
        assertTrue(key.contains("shitou"));
        assertTrue(SearchMatcher.matchesKey(key, SearchMatcher.keywords("shitou")));
        assertTrue(SearchMatcher.matchesKey(key, SearchMatcher.keywords("minecraft:stone")));
    }

    @Test
    void bundledTable_isLoadedAndCoversCommonMinecraftCharacters() {
        for (String name : new String[]{"钻石", "下界合金", "苦力怕", "末影龙", "红石", "附魔", "村民", "僵尸"}) {
            assertFalse(Pinyin.searchTerms(name).isEmpty(), name);
        }
    }
}
