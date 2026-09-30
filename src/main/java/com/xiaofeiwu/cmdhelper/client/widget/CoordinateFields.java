package com.xiaofeiwu.cmdhelper.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Three X/Y/Z boxes that show up on every coordinate-taking command (fill, setblock, tp, summon). */
public final class CoordinateFields {

    public final EditBox xBox;
    public final EditBox yBox;
    public final EditBox zBox;

    private CoordinateFields(EditBox xBox, EditBox yBox, EditBox zBox) {
        this.xBox = xBox;
        this.yBox = yBox;
        this.zBox = zBox;
    }

    public static CoordinateFields create(Font font, int x, int y, int boxWidth, int boxHeight) {
        EditBox xBox = new EditBox(font, x, y, boxWidth, boxHeight, Component.literal("X"));
        EditBox yBox = new EditBox(font, x + boxWidth + 6, y, boxWidth, boxHeight, Component.literal("Y"));
        EditBox zBox = new EditBox(font, x + 2 * (boxWidth + 6), y, boxWidth, boxHeight, Component.literal("Z"));
        EditBox[] all = {xBox, yBox, zBox};
        for (EditBox box : all) {
            box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,6}"));
        }
        return new CoordinateFields(xBox, yBox, zBox);
    }

    public void fillFrom(double x, double y, double z) {
        xBox.setValue(String.valueOf((int) Math.floor(x)));
        yBox.setValue(String.valueOf((int) Math.floor(y)));
        zBox.setValue(String.valueOf((int) Math.floor(z)));
    }

    public boolean isComplete() {
        return isValid(xBox) && isValid(yBox) && isValid(zBox);
    }

    public String coordString() {
        return intOr(xBox) + " " + intOr(yBox) + " " + intOr(zBox);
    }

    private static boolean isValid(EditBox box) {
        String v = box.getValue();
        return !v.isBlank() && !v.equals("-");
    }

    private static String intOr(EditBox box) {
        try {
            return String.valueOf(Integer.parseInt(box.getValue()));
        } catch (NumberFormatException e) {
            return "0";
        }
    }
}
