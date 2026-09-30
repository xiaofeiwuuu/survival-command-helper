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

class CommandHistoryStoreTest {

    @TempDir
    Path dir;

    private CommandHistoryStore fresh() {
        return new CommandHistoryStore(dir);
    }

    @Test
    void recordsNewestFirst_andDeduplicates() {
        CommandHistoryStore store = fresh();
        store.add("a");
        store.add("b");
        store.add("a");
        assertEquals(List.of("a", "b"), store.historyList());
    }

    @Test
    void isCappedAtTwenty() {
        CommandHistoryStore store = fresh();
        for (int i = 0; i < 25; i++) {
            store.add("cmd " + i);
        }
        assertEquals(20, store.historyList().size());
        assertEquals("cmd 24", store.historyList().get(0));
    }

    @Test
    void persistsAcrossRestarts() {
        fresh().add("give @s minecraft:stone 1");
        assertEquals(List.of("give @s minecraft:stone 1"), fresh().historyList());
    }

    // ---- clearing ----------------------------------------------------------------------------------

    @Test
    void clearAllHistory_emptiesTheListAndTheFile() {
        CommandHistoryStore store = fresh();
        store.add("a");
        store.add("b");
        store.clearAllHistory();
        assertTrue(store.historyList().isEmpty());
        assertTrue(fresh().historyList().isEmpty(), "the clear must be saved, not just in memory");
    }

    @Test
    void clearAllHistory_keepsTheFavorites() {
        CommandHistoryStore store = fresh();
        store.add("a");
        store.flipFavorite("a");
        store.clearAllHistory();
        assertTrue(store.favorite("a"));
        assertTrue(fresh().favorite("a"));
    }

    @Test
    void clearingAnEmptyHistory_isHarmless() {
        CommandHistoryStore store = fresh();
        store.clearAllHistory();
        assertTrue(store.historyList().isEmpty());
        assertFalse(Files.exists(dir.resolve("history.json")), "nothing to write for an already-empty list");
    }

    @Test
    void removeOne_dropsOnlyThatEntry_andReportsWhetherItExisted() {
        CommandHistoryStore store = fresh();
        store.add("a");
        store.add("b");
        store.add("c");
        assertTrue(store.removeOne("b"));
        assertFalse(store.removeOne("b"));
        assertEquals(List.of("c", "a"), store.historyList());
        assertEquals(List.of("c", "a"), fresh().historyList());
    }

    @Test
    void removeOne_leavesTheStarredCopyAlone() {
        CommandHistoryStore store = fresh();
        store.add("a");
        store.flipFavorite("a");
        store.removeOne("a");
        assertTrue(store.favorite("a"));
    }

    // ---- favorites still behave -----------------------------------------------------------------

    @Test
    void toggleFavorite_addsThenRemoves() {
        CommandHistoryStore store = fresh();
        store.flipFavorite("a");
        assertTrue(store.favorite("a"));
        store.flipFavorite("a");
        assertFalse(store.favorite("a"));
    }

    @Test
    void corruptFilesStartEmpty() throws IOException {
        Files.writeString(dir.resolve("history.json"), "{ nope");
        Files.writeString(dir.resolve("favorites.json"), "also nope");
        CommandHistoryStore store = fresh();
        assertTrue(store.historyList().isEmpty());
        assertTrue(store.favoritesList().isEmpty());
    }
}
