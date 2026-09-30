package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.command.CoordinateParser;
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

    public void setAll(int x, int y, int z) {
        xBox.setValue(String.valueOf(x));
        yBox.setValue(String.valueOf(y));
        zBox.setValue(String.valueOf(z));
    }

    /**
     * A fourth box to paste "10 64 -5" (or "10，64，-5", "X: 10 Y: 64 Z: -5"...) into: as soon as it holds
     * exactly three numbers they're spread over X / Y / Z and the box empties. While the text can't be
     * read yet it turns red, so a paste that didn't work doesn't just sit there looking fine.
     * The copy-coordinates button on the main menu produces exactly this kind of text.
     */
    public EditBox createPasteBox(Font font, int x, int y, int width, int height) {
        return createPasteBox(font, x, y, width, height, () -> false);
    }

    /**
     * @param belowFeet asked at paste time: true means the pasted position is where the player's feet
     *                  are, so Y is lowered by one to the block they stand on — the same thing the
     *                  "Y-1" tick does for the 用当前 button. The copy-coordinates button on the main
     *                  menu copies the feet position, so without this a pasted floor was always missed.
     */
    public EditBox createPasteBox(Font font, int x, int y, int width, int height, java.util.function.BooleanSupplier belowFeet) {
        EditBox box = new EditBox(font, x, y, width, height, Component.literal("粘贴坐标"));
        box.setMaxLength(96);
        box.setHint(Component.literal("粘贴坐标"));
        box.setResponder(text -> {
            if (text.isBlank()) {
                box.setTextColor(0xE0E0E0);
                return;
            }
            var parsed = CoordinateParser.parse(text);
            if (parsed.isPresent()) {
                var c = parsed.get().shiftedY(belowFeet.getAsBoolean() ? -1 : 0);
                setAll(c.x(), c.y(), c.z());
                box.setValue("");
            } else {
                box.setTextColor(0xFF6B6B);
            }
        });
        return box;
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
