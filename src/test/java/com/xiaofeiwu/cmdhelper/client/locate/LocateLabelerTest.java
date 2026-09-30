package com.xiaofeiwu.cmdhelper.client.locate;

import com.xiaofeiwu.cmdhelper.client.locate.LocateLabeler.Kind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocateLabelerTest {

    private static LocateLabeler labeler(Map<String, String> saved, Map<String, String> lang) {
        return new LocateLabeler(saved::get, ns -> ns.equals("abridged") ? "Abridged" : null, lang::get);
    }

    private static LocateLabeler plain() {
        return labeler(Map.of(), Map.of());
    }

    @Test
    void vanillaStructures_haveChineseNames() {
        assertEquals("平原村庄 [minecraft:village_plains]", plain().label(Kind.STRUCTURE, "minecraft:village_plains"));
        assertEquals("要塞 [minecraft:stronghold]", plain().label(Kind.STRUCTURE, "minecraft:stronghold"));
    }

    @Test
    void vanillaPois_haveChineseNames() {
        assertEquals("盔甲匠（高炉） [minecraft:armorer]", plain().label(Kind.POI, "minecraft:armorer"));
    }

    @Test
    void everyVanillaStructureAndPoiTheScreenListsHasAName() {
        // The screen's built-in fallback lists must not contain an id this table can't name.
        for (String id : java.util.List.of("minecraft:ancient_city", "minecraft:trail_ruins", "minecraft:village_taiga",
                "minecraft:ruined_portal_swamp", "minecraft:shipwreck_beached")) {
            assertTrue(VanillaLocateNames.STRUCTURES.containsKey(id), id);
        }
        for (String id : java.util.List.of("minecraft:meeting", "minecraft:lodestone", "minecraft:nether_portal")) {
            assertTrue(VanillaLocateNames.POI_TYPES.containsKey(id), id);
        }
    }

    @Test
    void savedNameWinsOverEverything() {
        LocateLabeler l = labeler(Map.of("minecraft:village_plains", "我的村庄"), Map.of());
        assertEquals("我的村庄 [minecraft:village_plains]", l.label(Kind.STRUCTURE, "minecraft:village_plains"));
    }

    @Test
    void modTranslationKeyIsUsedWhenTheModShipsOne() {
        LocateLabeler l = labeler(Map.of(), Map.of("structure.abridged.bridge", "拱桥"));
        assertEquals("拱桥 [abridged:bridge]", l.label(Kind.STRUCTURE, "abridged:bridge"));
    }

    @Test
    void nestedPathsAreTriedWithDots() {
        LocateLabeler l = labeler(Map.of(), Map.of("structure.abridged.ruins.big", "大废墟"));
        assertEquals("大废墟", l.nameOf(Kind.STRUCTURE, "abridged:ruins/big"));
    }

    @Test
    void biomesUseTheGamesOwnTranslation() {
        LocateLabeler l = labeler(Map.of(), Map.of("biome.minecraft.plains", "平原"));
        assertEquals("平原 [minecraft:plains]", l.label(Kind.BIOME, "minecraft:plains"));
    }

    @Test
    void poiFallsBackToTheVillagerProfessionTranslation() {
        LocateLabeler l = labeler(Map.of(), Map.of("entity.minecraft.villager.nitwit", "傻子"));
        assertEquals("傻子", l.nameOf(Kind.POI, "minecraft:nitwit"));
    }

    @Test
    void unknownModdedId_fallsBackToModNameAndReadablePath_neverJustTheBareId() {
        assertEquals("Abridged · big bridge [abridged:big_bridge]", plain().label(Kind.STRUCTURE, "abridged:big_bridge"));
    }

    @Test
    void unknownModWithNoDisplayName_usesTheNamespace() {
        assertEquals("othermod · thing [othermod:thing]", plain().label(Kind.STRUCTURE, "othermod:thing"));
    }

    @Test
    void blankTranslationsAreIgnored() {
        LocateLabeler l = labeler(Map.of("abridged:bridge", "  "), Map.of("structure.abridged.bridge", ""));
        assertNull(l.nameOf(Kind.STRUCTURE, "abridged:bridge"));
    }

    @Test
    void biomeTableDoesNotLeakIntoOtherKinds() {
        assertNull(plain().nameOf(Kind.BIOME, "minecraft:village_plains"));
    }
}
