package com.xiaofeiwu.cmdhelper.client.hostile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostileTypesTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("cmdhelper").resolve("hostile_types.json");
    }

    @Test
    void firstRun_usesTheDefaultsAndWritesNothing() {
        HostileTypes types = new HostileTypes(file());
        assertEquals(HostileTypes.DEFAULTS, types.ids());
        assertFalse(Files.exists(file()));
    }

    @Test
    void defaults_coverTheMobsTheUserAskedFor() {
        for (String id : List.of("zombie", "skeleton", "creeper", "slime", "phantom", "enderman",
                "zombie_villager", "drowned")) {
            assertTrue(HostileTypes.DEFAULTS.contains("minecraft:" + id), id);
        }
    }

    @Test
    void add_persistsAcrossRestarts() {
        new HostileTypes(file()).add("minecraft:witch");
        assertTrue(new HostileTypes(file()).contains("minecraft:witch"));
    }

    @Test
    void add_rejectsDuplicatesAndKeepsOrder() {
        HostileTypes types = new HostileTypes(file());
        assertFalse(types.add("minecraft:zombie"));
        assertTrue(types.add("mymod:royal_creeper"));
        assertEquals("mymod:royal_creeper", types.ids().get(types.ids().size() - 1));
    }

    @Test
    void add_normalisesCaseWhitespaceAndDefaultNamespace() {
        HostileTypes types = new HostileTypes(file());
        assertTrue(types.add("  Witch "));
        assertTrue(types.contains("minecraft:witch"));
        assertTrue(types.contains("MINECRAFT:WITCH"));
    }

    @Test
    void add_rejectsMalformedIds() {
        HostileTypes types = new HostileTypes(file());
        assertFalse(types.add(""));
        assertFalse(types.add("   "));
        assertFalse(types.add("bad id"));
        assertFalse(types.add("type=zombie]"));
        assertFalse(types.add(null));
        assertEquals(HostileTypes.DEFAULTS, types.ids());
    }

    @Test
    void remove_persists_andReportsWhetherAnythingChanged() {
        HostileTypes types = new HostileTypes(file());
        assertTrue(types.remove("minecraft:enderman"));
        assertFalse(types.remove("minecraft:enderman"));
        assertFalse(new HostileTypes(file()).contains("minecraft:enderman"));
    }

    @Test
    void emptiedList_staysEmpty_notRevertedToDefaults() {
        HostileTypes types = new HostileTypes(file());
        for (String id : HostileTypes.DEFAULTS) {
            types.remove(id);
        }
        assertTrue(new HostileTypes(file()).ids().isEmpty());
    }

    @Test
    void toggle_addsThenRemoves() {
        HostileTypes types = new HostileTypes(file());
        assertTrue(types.toggle("minecraft:witch"));
        assertFalse(types.toggle("minecraft:witch"));
        assertFalse(types.contains("minecraft:witch"));
    }

    @Test
    void resetToDefaults_undoesCustomisation() {
        HostileTypes types = new HostileTypes(file());
        types.add("minecraft:witch");
        types.remove("minecraft:zombie");
        types.resetToDefaults();
        assertEquals(HostileTypes.DEFAULTS, types.ids());
        assertEquals(HostileTypes.DEFAULTS, new HostileTypes(file()).ids());
    }

    @Test
    void corruptFile_fallsBackToDefaults() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ this is not json");
        assertEquals(HostileTypes.DEFAULTS, new HostileTypes(file()).ids());
    }

    @Test
    void fileWithJunkEntries_keepsOnlyTheValidOnes_deduplicated() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "[\"minecraft:witch\", \"WITCH\", \"bad id\", \"\", \"mymod:x\"]");
        assertEquals(List.of("minecraft:witch", "mymod:x"), new HostileTypes(file()).ids());
    }

    @Test
    void normalize_returnsNullForGarbage() {
        assertNull(HostileTypes.normalize("a b"));
    }

    @Test
    void range_defaultsAndPersists() {
        HostileTypes types = new HostileTypes(file());
        assertEquals(HostileTypes.DEFAULT_RANGE, types.range());
        types.setRange(30);
        assertEquals(30, new HostileTypes(file()).range());
    }

    @Test
    void range_isClamped() {
        HostileTypes types = new HostileTypes(file());
        types.setRange(0);
        assertEquals(1, types.range());
        types.setRange(1_000_000);
        assertEquals(HostileTypes.MAX_RANGE, types.range());
    }

    @Test
    void changingRange_keepsTheTypeList() {
        HostileTypes types = new HostileTypes(file());
        types.add("minecraft:witch");
        types.setRange(25);
        HostileTypes reloaded = new HostileTypes(file());
        assertTrue(reloaded.contains("minecraft:witch"));
        assertEquals(25, reloaded.range());
    }

    @Test
    void changingTypes_keepsTheRange() {
        HostileTypes types = new HostileTypes(file());
        types.setRange(40);
        types.add("minecraft:witch");
        assertEquals(40, new HostileTypes(file()).range());
    }

    @Test
    void legacyArrayFile_isStillReadWithDefaultRange_andUpgradedOnNextChange() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "[\"minecraft:witch\"]");
        HostileTypes types = new HostileTypes(file());
        assertEquals(List.of("minecraft:witch"), types.ids());
        assertEquals(HostileTypes.DEFAULT_RANGE, types.range());
        types.setRange(12);
        assertTrue(Files.readString(file()).contains("\"types\""));
        assertEquals(List.of("minecraft:witch"), new HostileTypes(file()).ids());
    }

    @Test
    void objectFileWithBadRange_keepsDefaultRange_andTheTypeList() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"types\":[\"minecraft:witch\"],\"range\":\"far\"}");
        HostileTypes types = new HostileTypes(file());
        assertEquals(HostileTypes.DEFAULT_RANGE, types.range());
        assertEquals(List.of("minecraft:witch"), types.ids());
    }
}
