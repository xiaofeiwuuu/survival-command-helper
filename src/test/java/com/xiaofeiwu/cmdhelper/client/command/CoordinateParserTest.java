package com.xiaofeiwu.cmdhelper.client.command;

import com.xiaofeiwu.cmdhelper.client.command.CoordinateParser.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinateParserTest {

    private static final Optional<Coordinates> EXPECTED = Optional.of(new Coordinates(10, 64, -5));

    private static Optional<Coordinates> p(String text) {
        return CoordinateParser.parse(text);
    }

    @Test
    void whatTheMainMenuCopyButtonProduces() {
        assertEquals(EXPECTED, p("10 64 -5"));
    }

    @Test
    void separators_asked_for() {
        assertEquals(EXPECTED, p("10, 64, -5"));   // English comma + space
        assertEquals(EXPECTED, p("10,64,-5"));     // English comma, no spaces
        assertEquals(EXPECTED, p("10，64，-5"));   // Chinese comma
        assertEquals(EXPECTED, p("10， 64， -5")); // Chinese comma + space
        assertEquals(EXPECTED, p("10   64\t-5"));  // runs of spaces and tabs
    }

    @Test
    void otherSeparators() {
        assertEquals(EXPECTED, p("10、64、-5"));
        assertEquals(EXPECTED, p("10；64；-5"));
        assertEquals(EXPECTED, p("10;64;-5"));
        assertEquals(EXPECTED, p("10 / 64 / -5"));
        assertEquals(EXPECTED, p("10\n64\n-5"));
        assertEquals(EXPECTED, p("10　64　-5")); // ideographic (full-width) space
    }

    @Test
    void wrappedInBrackets() {
        assertEquals(EXPECTED, p("(10, 64, -5)"));
        assertEquals(EXPECTED, p("[10, 64, -5]"));
        assertEquals(EXPECTED, p("（10，64，-5）"));
        assertEquals(EXPECTED, p("【10，64，-5】"));
    }

    @Test
    void withLetterLabels() {
        assertEquals(EXPECTED, p("X: 10 Y: 64 Z: -5"));
        assertEquals(EXPECTED, p("x=10, y=64, z=-5"));
        assertEquals(EXPECTED, p("X：10  Y：64  Z：-5")); // Chinese colon
        assertEquals(EXPECTED, p("坐标 10 64 -5"));
    }

    @Test
    void theF3ScreenStyle_withDecimals_areFloored() {
        assertEquals(Optional.of(new Coordinates(10, 64, -6)), p("XYZ: 10.123 / 64.00000 / -5.500"));
    }

    @Test
    void decimalsFloorTowardMinusInfinity_notTowardZero() {
        // block -6 contains x = -5.5; truncating would wrongly give -5
        assertEquals(Optional.of(new Coordinates(-11, 0, 0)), p("-10.3 0 0"));
        assertEquals(Optional.of(new Coordinates(10, 0, 0)), p("10.9 0 0"));
    }

    @Test
    void fullWidthDigitsAndMinusFromAChineseInputMethod() {
        assertEquals(EXPECTED, p("１０ ６４ －５"));
        assertEquals(EXPECTED, p("10 64 −5"));   // U+2212 minus
        assertEquals(EXPECTED, p("10 64 –5"));   // en dash
    }

    @Test
    void aWholeCommand_yieldsItsCoordinates() {
        assertEquals(EXPECTED, p("/tp @s 10 64 -5"));
        assertEquals(EXPECTED, p("teleport @s 10 64 -5"));
    }

    @Test
    void negativeZeroAndPlainZero() {
        assertEquals(Optional.of(new Coordinates(0, 0, 0)), p("0 0 0"));
        assertEquals(Optional.of(new Coordinates(0, 0, 0)), p("0,0,0"));
    }

    @Test
    void tooFewNumbers_isIncomplete() {
        assertTrue(p("10 64").isEmpty());
        assertTrue(p("10").isEmpty());
        assertTrue(p("").isEmpty());
        assertTrue(p("   ").isEmpty());
        assertTrue(p(null).isEmpty());
        assertTrue(p("没有数字").isEmpty());
    }

    @Test
    void tooManyNumbers_isAmbiguous() {
        assertTrue(p("10 64 -5 3").isEmpty());
        assertTrue(p("Block: 10 64 -5 Chunk: 0 4 -1").isEmpty());
    }

    @Test
    void numbersTooBigForTheBoxes_areRejected() {
        assertTrue(p("1000000 0 0").isEmpty());
        assertEquals(Optional.of(new Coordinates(999999, 0, -999999)), p("999999 0 -999999"));
        assertTrue(p("99999999999999999999 0 0").isEmpty());
    }

    @Test
    void aNumberGluedToLettersStillCounts() {
        assertEquals(EXPECTED, p("x10y64z-5"));
    }
}
