package com.xiaofeiwu.cmdhelper.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A plain scrollable, paginated text list (one string per row) — the same screen-driven
 * render()/mouseClicked()/mouseScrolled() pattern as RegistryGridWidget, just single-column
 * and text instead of an icon grid. Used for the command history / favorites list.
 *
 * With a describer set, each row becomes two lines: the description on top, the raw command
 * underneath in a muted colour. A command the describer can't explain just shows on its own.
 */
public class SimpleListWidget {

    public static final int SINGLE_LINE_ROW_HEIGHT = 16;
    public static final int TWO_LINE_ROW_HEIGHT = 26;

    private final int x;
    private final int y;
    private final int width;
    private final int rows;
    private final int rowHeight;
    private final Consumer<String> onPick;
    private Function<String, String> describer;
    // The describer runs every frame for every visible row; the answer for a given command is fixed.
    private final Map<String, String> descriptionCache = new HashMap<>();

    private List<String> items = List.of();
    private int page = 0;
    private String selected;
    private String hovered;
    private String emptyMessage = "空";

    public SimpleListWidget(int x, int y, int width, int rows, Consumer<String> onPick) {
        this(x, y, width, rows, SINGLE_LINE_ROW_HEIGHT, onPick);
    }

    public SimpleListWidget(int x, int y, int width, int rows, int rowHeight, Consumer<String> onPick) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.rows = rows;
        this.rowHeight = rowHeight;
        this.onPick = onPick;
    }

    /** Turns a row's raw text into an explanation to show above it; null means "no explanation". */
    public void setDescriber(Function<String, String> describer) {
        this.describer = describer;
        this.descriptionCache.clear();
    }

    /** The explanation for one row's text, or null. Cached. */
    public String descriptionOf(String item) {
        if (describer == null) {
            return null;
        }
        String cached = descriptionCache.get(item);
        if (cached == null) {
            String d = describer.apply(item);
            cached = d == null ? "" : d;
            descriptionCache.put(item, cached);
        }
        return cached.isEmpty() ? null : cached;
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
    }

    public void setItems(List<String> items) {
        this.items = items;
        if (page >= totalPages()) {
            page = 0;
        }
        if (selected != null && !items.contains(selected)) {
            selected = null;
        }
    }

    public int totalPages() {
        return Math.max(1, (items.size() + rows - 1) / rows);
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

    public String getSelected() {
        return selected;
    }

    public void setSelected(String selected) {
        this.selected = selected;
    }

    public int pixelHeight() {
        return rows * rowHeight;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        hovered = null;
        var font = Minecraft.getInstance().font;

        if (items.isEmpty()) {
            g.drawCenteredString(font, emptyMessage, x + width / 2, y + pixelHeight() / 2 - 4, 0xFFA8A8B4);
            return;
        }

        int start = page * rows;
        int end = Math.min(items.size(), start + rows);
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int rowY = y + idx * rowHeight;
            String item = items.get(i);

            boolean isHovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + rowHeight;
            boolean isSelected = item.equals(selected);
            if (isHovered) {
                hovered = item;
            }
            g.fill(x, rowY, x + width, rowY + rowHeight - 1,
                    isSelected ? 0xFF5A4A1A : (isHovered ? 0xFF3A3A48 : (idx % 2 == 0 ? 0x501A1A22 : 0x50151519)));

            int maxWidth = width - 8;
            String description = descriptionOf(item);
            if (description != null && rowHeight >= TWO_LINE_ROW_HEIGHT) {
                g.drawString(font, ellipsize(font, description, maxWidth), x + 4, rowY + 4, 0xFFEDEDED, false);
                g.drawString(font, ellipsize(font, "/" + item, maxWidth), x + 4, rowY + 15, 0xFF8A8A9A, false);
            } else {
                // Nothing to explain (or single-line mode): the command, vertically centred.
                g.drawString(font, ellipsize(font, "/" + item, maxWidth), x + 4,
                        rowY + (rowHeight - 9) / 2, 0xFFEDEDED, false);
            }
        }
    }

    private static String ellipsize(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text
                : font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || items.isEmpty()) {
            return false;
        }
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + pixelHeight()) {
            return false;
        }
        int row = (int) ((mouseY - y) / rowHeight);
        int idx = page * rows + row;
        if (idx >= 0 && idx < items.size()) {
            selected = items.get(idx);
            onPick.accept(selected);
            return true;
        }
        return false;
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
