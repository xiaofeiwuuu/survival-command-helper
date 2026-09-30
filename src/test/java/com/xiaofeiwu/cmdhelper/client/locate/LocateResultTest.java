package com.xiaofeiwu.cmdhelper.client.locate;

import com.xiaofeiwu.cmdhelper.client.locate.LocateResult.Position;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocateResultTest {

    @Test
    void parsesTheRealChineseMessageFromTheGame() {
        // Copied from an actual /locate structure result.
        assertEquals(Optional.of(new Position(-736, null, -720)),
                LocateResult.parse("最近的minecraft:ancient_city位于[-736, ~, -720]（1201个方块外）"));
    }

    @Test
    void parsesTheEnglishMessage() {
        assertEquals(Optional.of(new Position(-736, null, -720)),
                LocateResult.parse("The nearest minecraft:ancient_city is at [-736, ~, -720] (1201 blocks away)"));
    }

    @Test
    void tildeMeansHeightUnknown_numberMeansKnown() {
        assertEquals(null, LocateResult.parse("[1, ~, 2]").orElseThrow().y());
        assertEquals(Integer.valueOf(64), LocateResult.parse("[1, 64, 2]").orElseThrow().y());
    }

    @Test
    void negativeHeightIsKept() {
        assertEquals(Integer.valueOf(-40), LocateResult.parse("[10, -40, 20]").orElseThrow().y());
    }

    @Test
    void failureMessagesHaveNoPosition() {
        assertTrue(LocateResult.parse("Could not find a structure of type \"minecraft:igloo\" nearby").isEmpty());
        assertTrue(LocateResult.parse("附近找不到类型为“minecraft:igloo”的结构").isEmpty());
    }

    @Test
    void nullAndEmpty() {
        assertTrue(LocateResult.parse(null).isEmpty());
        assertTrue(LocateResult.parse("").isEmpty());
    }

    @Test
    void absurdNumberIsNotACoordinate() {
        assertTrue(LocateResult.parse("[99999999999, ~, 1]").isEmpty());
    }
}
