package com.xiaofeiwu.cmdhelper.client.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloneHistoryStoreTest {

    @TempDir
    Path dir;

    private static final String A = "clone 0 0 0 9 5 11 20 0 0";
    private static final String B = "clone -1267 73 -684 -1258 78 -673 -1267 78 -684 replace force";

    private Path file() {
        return dir.resolve("cmdhelper").resolve("clone_history.json");
    }

    @Test
    void startsEmpty_andWritesNothingUntilRecorded() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        assertTrue(store.commands().isEmpty());
        assertFalse(Files.exists(file()));
    }

    @Test
    void newestFirst_andPersisted() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        store.record(A);
        store.record(B);
        assertEquals(List.of(B, A), new CloneHistoryStore(file()).commands());
    }

    @Test
    void repeatingACommand_movesItToTheFrontWithoutDuplicating() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        store.record(A);
        store.record(B);
        store.record(A);
        assertEquals(List.of(A, B), store.commands());
    }

    @Test
    void extraSpacesDoNotMakeADuplicate() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        store.record(A);
        store.record("clone  0 0 0   9 5 11 20 0 0 ");
        assertEquals(1, store.commands().size());
    }

    @Test
    void onlyClonesThatCanBeReadBackAreKept() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        assertFalse(store.record("fill 0 0 0 1 1 1 minecraft:stone"));
        assertFalse(store.record("clone 0 0 0 1 1 1 5 5 5 filtered minecraft:stone"));
        assertFalse(store.record(null));
        assertTrue(store.commands().isEmpty());
    }

    @Test
    void isCappedAtThirty_dropsTheOldest() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        for (int i = 0; i < CloneHistoryStore.CAP + 5; i++) {
            store.record("clone 0 0 0 1 1 1 " + i + " 0 0");
        }
        assertEquals(CloneHistoryStore.CAP, store.commands().size());
        assertEquals("clone 0 0 0 1 1 1 " + (CloneHistoryStore.CAP + 4) + " 0 0", store.commands().get(0));
        assertFalse(store.commands().contains("clone 0 0 0 1 1 1 0 0 0"));
    }

    @Test
    void clear_emptiesAndPersists() {
        CloneHistoryStore store = new CloneHistoryStore(file());
        store.record(A);
        store.clear();
        assertTrue(new CloneHistoryStore(file()).commands().isEmpty());
    }

    @Test
    void corruptFile_startsEmpty() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ nope");
        assertTrue(new CloneHistoryStore(file()).commands().isEmpty());
    }

    @Test
    void junkEntriesInAHandEditedFile_areDropped() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "[\"" + A + "\", \"say hi\", null, \"" + A + "\"]");
        assertEquals(List.of(A), new CloneHistoryStore(file()).commands());
    }
}
