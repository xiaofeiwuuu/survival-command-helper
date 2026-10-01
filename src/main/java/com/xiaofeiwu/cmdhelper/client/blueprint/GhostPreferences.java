package com.xiaofeiwu.cmdhelper.client.blueprint;

/** The player's choice between the real-block ghost and plain coloured boxes, shared by every preview. */
public final class GhostPreferences {

    /** true: show the actual block models, see-through; false: coloured boxes. Toggled with the V key. */
    public static boolean realBlocks = true;

    private GhostPreferences() {
    }
}
