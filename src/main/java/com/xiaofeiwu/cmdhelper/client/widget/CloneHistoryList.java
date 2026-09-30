package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.command.CloneCalc;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A narrow column of past clones, three short lines each: size, where the copy went, and the modes.
 * Click one to load it back into the clone screen; hover for the full explanation. Screen-driven like
 * the other list widgets here (render / mouseClicked / mouseScrolled, tooltip drawn last).
 */
public class CloneHistoryList {

    public static final int ROW_HEIGHT = 34;

    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_MUTED = 0xFF8A8A9A;
    private static final int COLOR_DIM = 0xFF6A6A7A;

    private final int x;
    private final int y;
    private final int width;
    private final int rows;
    private final Consumer<CloneCalc.Parsed> onPick;
    private final Function<String, String> describer;

    private List<String> commands = List.of();
    private int page = 0;
    private String hovered;

    public CloneHistoryList(int x, int y, int width, int rows, Consumer<CloneCalc.Parsed> onPick,
                            Function<String, String> describer) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.rows = Math.max(1, rows);
        this.onPick = onPick;
        this.describer = describer;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
        if (page >= totalPages()) {
            page = Math.max(0, totalPages() - 1);
        }
    }

    public int totalPages() {
        return Math.max(1, (commands.size() + rows - 1) / rows);
    }

    public int currentPage() {
        return page;
    }

    public void nextPage() {
        if (page < totalPages() - 1) {
            page++;
        }
    }

    public void prevPage() {
        if (page > 0) {
            page--;
        }
    }

    public int pixelHeight() {
        return rows * ROW_HEIGHT;
    }

    // ---- drawing -------------------------------------------------------------------------------

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        hovered = null;
        if (commands.isEmpty()) {
            g.drawCenteredString(font, "还没有执行过", x + width / 2, y + 12, COLOR_MUTED);
            g.drawCenteredString(font, "复制", x + width / 2, y + 24, COLOR_DIM);
            return;
        }
        int start = page * rows;
        int end = Math.min(commands.size(), start + rows);
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * ROW_HEIGHT;
            String command = commands.get(i);
            var parsed = CloneCalc.parse(command);
            boolean isHovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (isHovered) {
                hovered = command;
            }
            g.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 2,
                    isHovered ? 0xFF3A3A48 : ((i - start) % 2 == 0 ? 0x501A1A22 : 0x50151519));
            if (parsed.isEmpty()) {
                g.drawString(font, ellipsize(font, "/" + command, width - 8), x + 4, rowY + 4, COLOR_MUTED, false);
                continue;
            }
            CloneCalc.Parsed p = parsed.get();
            RegionBounds s = p.source();
            g.drawString(font, ellipsize(font, s.sizeX() + "×" + s.sizeY() + "×" + s.sizeZ() + " · " + s.volume() + "格",
                    width - 8), x + 4, rowY + 3, COLOR_TEXT, false);
            g.drawString(font, ellipsize(font, "→ (" + p.destX() + "," + p.destY() + "," + p.destZ() + ")", width - 8),
                    x + 4, rowY + 13, COLOR_MUTED, false);
            g.drawString(font, ellipsize(font, maskLabel(p.mask()) + " · " + modeLabel(p.mode()), width - 8),
                    x + 4, rowY + 23, COLOR_DIM, false);
        }
    }

    /** Call last, so the tooltip paints over everything else. */
    public void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hovered == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String description = describer.apply(hovered);
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (description != null) {
            lines.add(Component.literal(description));
        }
        lines.add(Component.literal("/" + hovered).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.literal("点击填入所有输入框").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        // Wrap so a long explanation doesn't run off the side of the screen.
        java.util.List<net.minecraft.util.FormattedCharSequence> wrapped = new java.util.ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, 220));
        }
        g.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    private static String maskLabel(CloneCalc.MaskMode mask) {
        return mask == CloneCalc.MaskMode.MASKED ? "非空气" : "替换";
    }

    private static String modeLabel(CloneCalc.CloneMode mode) {
        return switch (mode) {
            case AUTO -> "自动";
            case NORMAL -> "普通";
            case FORCE -> "强制";
            case MOVE -> "移动";
        };
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    // ---- input ---------------------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || commands.isEmpty() || mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + pixelHeight()) {
            return false;
        }
        int index = page * rows + (int) ((mouseY - y) / ROW_HEIGHT);
        if (index >= commands.size()) {
            return false;
        }
        CloneCalc.parse(commands.get(index)).ifPresent(onPick);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + pixelHeight()) {
            return false;
        }
        if (delta > 0) {
            prevPage();
        } else if (delta < 0) {
            nextPage();
        }
        return true;
    }
}
