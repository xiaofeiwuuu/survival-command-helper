package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBoundsTest {

    @Test
    void of_normalisesCornersOrder() {
        RegionBounds b = RegionBounds.of("5 70 -3", "-5 64 3");
        assertEquals(new RegionBounds(-5, 64, -3, 5, 70, 3), b);
    }

    @Test
    void volume_countsBothEndsInclusive() {
        RegionBounds b = RegionBounds.of("0 0 0", "9 2 4");
        assertEquals(10, b.sizeX());
        assertEquals(3, b.sizeY());
        assertEquals(5, b.sizeZ());
        assertEquals(150, b.volume());
    }

    @Test
    void singleBlockHasVolumeOne() {
        assertEquals(1, RegionBounds.of("7 7 7", "7 7 7").volume());
    }

    @Test
    void fillLimit_boundaryIsInclusive() {
        // 32 * 32 * 32 = 32768 exactly: allowed. One more layer: over.
        assertFalse(RegionBounds.of("0 0 0", "31 31 31").exceedsFillLimit());
        assertTrue(RegionBounds.of("0 0 0", "31 32 31").exceedsFillLimit());
    }

    @Test
    void volume_doesNotOverflowInt() {
        assertTrue(RegionBounds.of("0 0 0", "5000 5000 5000").exceedsFillLimit());
    }

    @Test
    void of_rejectsMalformedCoords() {
        assertThrows(IllegalArgumentException.class, () -> RegionBounds.of("1 2", "3 4 5"));
    }
}
