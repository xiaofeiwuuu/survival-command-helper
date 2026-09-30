package com.xiaofeiwu.cmdhelper.client.forceload;

import com.xiaofeiwu.cmdhelper.client.forceload.ForceLoadedChunks.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForceLoadedChunksTest {

    @Test
    void parsesTheEnglishMultipleMessage() {
        assertEquals(List.of(new Chunk(0, 0), new Chunk(3, -2)),
                ForceLoadedChunks.parse("2 force loaded chunks were found in minecraft:overworld at: [0, 0], [3, -2]"));
    }

    @Test
    void parsesTheRealMessageSeenInTheChineseClient() {
        // Copied from an actual game screenshot of /forceload query.
        assertEquals(List.of(new Chunk(7, 0), new Chunk(6, 10)),
                ForceLoadedChunks.parse("在minecraft:overworld内找到2个强制加载的区块：[7, 0], [6, 10]"));
    }

    @Test
    void parsesTheSingleMessage() {
        assertEquals(List.of(new Chunk(-7, 12)),
                ForceLoadedChunks.parse("A force loaded chunk was found in minecraft:the_nether at: [-7, 12]"));
    }

    @Test
    void wordingInAnotherLanguageDoesNotMatter() {
        assertEquals(List.of(new Chunk(1, 2), new Chunk(-3, 4)),
                ForceLoadedChunks.parse("在 minecraft:overworld 中找到了 2 个被强制加载的区块，位于：[1, 2], [-3, 4]"));
    }

    @Test
    void keepsServerOrderAndDropsDuplicates() {
        assertEquals(List.of(new Chunk(5, 5), new Chunk(1, 1)),
                ForceLoadedChunks.parse("[5, 5], [1, 1], [5, 5]"));
    }

    @Test
    void toleratesMissingSpacesAfterTheComma() {
        assertEquals(List.of(new Chunk(1, 2)), ForceLoadedChunks.parse("[1,2]"));
    }

    @Test
    void messagesWithoutChunksGiveAnEmptyList() {
        assertTrue(ForceLoadedChunks.parse("No force loaded chunks were found in minecraft:overworld").isEmpty());
        assertTrue(ForceLoadedChunks.parse("").isEmpty());
        assertTrue(ForceLoadedChunks.parse(null).isEmpty());
    }

    @Test
    void dimensionIdsAndCountsAreNotMistakenForChunks() {
        assertTrue(ForceLoadedChunks.parse("3 chunks in mymod:some_dim").isEmpty());
    }

    @Test
    void absurdCoordinateIsSkippedNotCrashed() {
        assertEquals(List.of(new Chunk(1, 1)), ForceLoadedChunks.parse("[99999999999, 1], [1, 1]"));
    }

    @Test
    void blockCoordinates_ofPositiveChunk() {
        Chunk c = new Chunk(2, 3);
        assertEquals(32, c.minBlockX());
        assertEquals(48, c.minBlockZ());
        assertEquals(40, c.centerBlockX());
        assertEquals(56, c.centerBlockZ());
    }

    @Test
    void blockCoordinates_ofNegativeChunkStayInsideThatChunk() {
        // chunk -1 spans blocks -16..-1; its "centre" block must be -8, not something in chunk 0 or -2.
        Chunk c = new Chunk(-1, -1);
        assertEquals(-16, c.minBlockX());
        assertEquals(-8, c.centerBlockX());
        assertEquals(-8, c.centerBlockZ());
        assertEquals(-1, Math.floorDiv(c.centerBlockX(), 16));
    }
}
