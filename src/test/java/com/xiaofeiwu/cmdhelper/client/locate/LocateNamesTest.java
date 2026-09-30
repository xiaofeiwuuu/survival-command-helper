package com.xiaofeiwu.cmdhelper.client.locate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocateNamesTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("cmdhelper").resolve("locate_names.json");
    }

    @Test
    void startsEmpty_andWritesNothingUntilSaved() {
        LocateNames names = new LocateNames(file());
        assertNull(names.get("abridged:bridge"));
        assertFalse(Files.exists(file()));
    }

    @Test
    void savedNamePersistsAcrossRestarts_includingChinese() {
        new LocateNames(file()).set("abridged:bridge", "拱桥");
        assertEquals("拱桥", new LocateNames(file()).get("abridged:bridge"));
    }

    @Test
    void idsAreNormalised() {
        LocateNames names = new LocateNames(file());
        assertTrue(names.set("  Abridged:Bridge ", "拱桥"));
        assertEquals("拱桥", names.get("abridged:bridge"));
    }

    @Test
    void blankNameRemovesTheEntry() {
        LocateNames names = new LocateNames(file());
        names.set("abridged:bridge", "拱桥");
        names.set("abridged:bridge", "   ");
        assertNull(new LocateNames(file()).get("abridged:bridge"));
    }

    @Test
    void invalidIdIsRejectedAndNothingChanges() {
        LocateNames names = new LocateNames(file());
        assertFalse(names.set("not an id", "x"));
        assertFalse(names.set(null, "x"));
        assertTrue(names.all().isEmpty());
    }

    @Test
    void overlongNameIsCut() {
        LocateNames names = new LocateNames(file());
        names.set("a:b", "长".repeat(100));
        assertEquals(LocateNames.MAX_NAME_LENGTH, names.get("a:b").length());
    }

    @Test
    void corruptFile_isTreatedAsEmpty() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ nope");
        assertTrue(new LocateNames(file()).all().isEmpty());
    }

    @Test
    void handEditedFile_keepsValidEntriesOnly() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{\"A:B\":\"甲\", \"bad id\":\"x\", \"c:d\":\"\", \"e:f\":5}");
        LocateNames names = new LocateNames(file());
        assertEquals("甲", names.get("a:b"));
        assertNull(names.get("c:d"));
    }
}
