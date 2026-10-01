package com.xiaofeiwu.cmdhelper.client.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintStoreTest {

    @TempDir
    Path dir;

    private static Blueprint sample(String name, long created) {
        return new Blueprint(name, 10, 6, 12,
                List.of("minecraft:air", "minecraft:stone", "minecraft:oak_stairs[facing=north]", "abridged:bridge_block"),
                List.of(new Cuboid(0, 0, 0, 9, 5, 11, 0), new Cuboid(1, 0, 1, 8, 0, 10, 1), new Cuboid(2, 1, 2, 2, 1, 2, 2)),
                created);
    }

    @Test
    void saveThenList_roundTripsEverything() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        store.save(sample("我的房子", 5));
        Blueprint loaded = store.list().get(0);
        assertEquals(sample("我的房子", 5), loaded);
    }

    @Test
    void newestFirst() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        store.save(sample("旧的", 1));
        store.save(sample("新的", 9));
        assertEquals("新的", store.list().get(0).name());
        assertEquals("旧的", store.list().get(1).name());
    }

    @Test
    void aTakenName_getsASuffix_insteadOfOverwriting() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        Blueprint first = store.save(sample("房子", 1));
        Blueprint second = store.save(sample("房子", 2));
        assertEquals("房子", first.name());
        assertEquals("房子 (2)", second.name());
        assertEquals(2, store.list().size());
    }

    @Test
    void illegalFileNameCharacters_areReplaced() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        Blueprint saved = store.save(sample("a/b:c*d?", 1));
        assertFalse(saved.name().contains("/"));
        assertFalse(saved.name().contains(":"));
        assertTrue(Files.exists(dir.resolve(saved.name() + ".json")));
    }

    @Test
    void emptyNameGetsADefault() {
        assertEquals("蓝图", BlueprintStore.fileSafe("   "));
        assertEquals("蓝图", BlueprintStore.fileSafe(null));
    }

    @Test
    void veryLongNamesAreCut() {
        assertTrue(BlueprintStore.fileSafe("长".repeat(200)).length() <= 60);
    }

    @Test
    void delete_removesTheFile() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        Blueprint saved = store.save(sample("房子", 1));
        assertTrue(store.delete(saved.name()));
        assertTrue(store.list().isEmpty());
        assertFalse(store.delete(saved.name()));
    }

    @Test
    void aBrokenOrForeignFile_isSkipped_notFatal() throws IOException {
        BlueprintStore store = new BlueprintStore(dir);
        store.save(sample("好的", 1));
        Files.writeString(dir.resolve("坏的.json"), "{ nope");
        Files.writeString(dir.resolve("别的.json"), "{\"hello\":1}");
        assertEquals(1, store.list().size());
    }

    @Test
    void aFileWithAnOutOfRangePaletteIndex_isRejected() throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("坏索引.json"),
                "{\"version\":1,\"name\":\"x\",\"size\":[1,1,1],\"palette\":[\"minecraft:stone\"],\"cuboids\":[[0,0,0,0,0,0,5]]}");
        assertTrue(new BlueprintStore(dir).list().isEmpty());
    }

    @Test
    void missingFolder_listsNothing() {
        assertTrue(new BlueprintStore(dir.resolve("nope")).list().isEmpty());
    }

    @Test
    void namespaces_listTheModsUsed_withoutAir_inOrder() {
        assertEquals(java.util.List.of("minecraft", "abridged"), List.copyOf(sample("x", 1).namespaces()));
    }

    @Test
    void airIsRecognisedInAllItsForms() {
        assertTrue(Blueprint.isAir("minecraft:air"));
        assertTrue(Blueprint.isAir("minecraft:cave_air"));
        assertTrue(Blueprint.isAir("minecraft:void_air"));
        assertFalse(Blueprint.isAir("minecraft:stone"));
    }
}
