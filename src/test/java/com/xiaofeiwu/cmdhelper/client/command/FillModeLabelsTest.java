package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FillModeLabelsTest {

    @Test
    void knownKeyword_getsChineseLabel() {
        assertEquals("替换（全部换新）", FillModeLabels.labelFor("replace"));
    }

    @Test
    void unknownKeyword_fallsBackToItself() {
        // A future MC version might add a mode we haven't translated; showing the raw
        // keyword beats hiding the option or crashing.
        assertEquals("some_future_mode", FillModeLabels.labelFor("some_future_mode"));
    }
}
