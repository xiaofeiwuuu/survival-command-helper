package com.xiaofeiwu.cmdhelper.client.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintVerifierTest {

    // palette: 0 = air (block kind 100), 1 = stone (block kind 200), 2 = torch (block kind 300)
    private static final int[] BLOCK_OF_PALETTE = {100, 200, 300};

    @Test
    void expectedGrid_marksPlacedCells_andLeavesTheRestUnchecked() {
        List<Cuboid> placed = List.of(new Cuboid(10, 5, 20, 11, 5, 20, 1));
        int[] grid = BlueprintVerifier.expectedGrid(placed, 10, 5, 20, 3, 1, 1);
        assertEquals(1, grid[0]);
        assertEquals(1, grid[1]);
        assertEquals(-1, grid[2]);
    }

    @Test
    void expectedGrid_ignoresAnythingOutsideTheRegion() {
        List<Cuboid> placed = List.of(new Cuboid(0, 0, 0, 100, 100, 100, 1));
        int[] grid = BlueprintVerifier.expectedGrid(placed, 0, 0, 0, 2, 2, 2);
        assertEquals(8, Arrays.stream(grid).filter(v -> v == 1).count());
    }

    @Test
    void whenEverythingMatches_nothingNeedsFixing() {
        int[] expected = {1, 1, 0};
        int[] actual = {200, 200, 100};
        assertTrue(BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 3, 1, 1).isEmpty());
    }

    @Test
    void aMissingBlock_isReportedWithTheBlockThatBelongsThere() {
        int[] expected = {1, 1, 1};
        int[] actual = {200, 100, 200}; // the middle one is air
        List<Cuboid> fixes = BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 3, 1, 1);
        assertEquals(List.of(new Cuboid(1, 0, 0, 1, 0, 0, 1)), fixes);
    }

    @Test
    void neighbouringWrongBlocksAreMergedIntoOneFix() {
        int[] expected = {1, 1, 1, 1};
        int[] actual = {200, 100, 100, 200};
        assertEquals(List.of(new Cuboid(1, 0, 0, 2, 0, 0, 1)),
                BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 4, 1, 1));
    }

    @Test
    void cellsThatWereNeverPlaced_areNotChecked() {
        // e.g. a block that was skipped for being from a missing mod: whatever is there is fine
        int[] expected = {-1, 1};
        int[] actual = {999, 200};
        assertTrue(BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 2, 1, 1).isEmpty());
    }

    @Test
    void sandThatFell_isFlaggedAtBothEnds() {
        // a 1x3 column: stone, stone(should be), torch(should be) but the torch popped off and the top is air
        int[] expected = {1, 1, 2};
        int[] actual = {200, 200, 100};
        List<Cuboid> fixes = BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 1, 3, 1);
        assertEquals(List.of(new Cuboid(0, 2, 0, 0, 2, 0, 2)), fixes);
    }

    @Test
    void theFixesCoverExactlyTheWrongCells() {
        int[] expected = new int[4 * 3 * 5];
        Arrays.fill(expected, 1);
        int[] actual = new int[expected.length];
        Arrays.fill(actual, 200);
        int[] wrongCells = {3, 4, 17, 30, 31, 32, 59};
        for (int i : wrongCells) {
            actual[i] = 100;
        }
        List<Cuboid> fixes = BlueprintVerifier.mismatches(expected, actual, BLOCK_OF_PALETTE, 4, 3, 5);
        long covered = fixes.stream().mapToLong(Cuboid::volume).sum();
        assertEquals(wrongCells.length, covered);
    }
}
