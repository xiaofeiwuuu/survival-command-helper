package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.registry.SearchMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A real click-to-open dropdown (vanilla only ships CycleButton, which cycles one value
 * at a time on click — not what most players expect from a "choose one of these" control).
 * When there are more than a handful of options, typing while it's open filters them —
 * scrolling through 100+ mod names one at a time isn't a real option.
 */
public class DropdownWidget<T> extends AbstractWidget {

    private static final int ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int FILTER_MIN_OPTIONS = 6;

    private static final int COLOR_BG = 0xFF232330;
    private static final int COLOR_BG_HOVER = 0xFF32323E;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A6C;
    private static final int COLOR_BORDER_DARK = 0xFF15151A;
    private static final int COLOR_OVERLAY_BG = 0xF61A1A22;
    private static final int COLOR_FILTER_BG = 0xFF15151B;
    private static final int COLOR_ROW_HOVER = 0xFF3A3A48;
    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_MUTED = 0xFF8A8A9A;
    private static final int COLOR_ARROW = 0xFFA8A8B4;

    private final List<T> options;
    private final Function<T, String> labelFn;
    private final Consumer<T> onSelect;
    private final boolean filterable;
    private T selected;
    private boolean open = false;
    private int scrollOffset = 0;
    private final StringBuilder filterText = new StringBuilder();
    private List<T> filtered;
    // Search keys (lower-cased label + pinyin), parallel to `options`, built on the first filter
    // keystroke. Option lists can be every block in a modpack, and labelFn may allocate — so
    // don't redo it per key.
    private List<String> searchKeys;

    public DropdownWidget(int x, int y, int width, int height, List<T> options, T initial,
                           Function<T, String> labelFn, Consumer<T> onSelect) {
        super(x, y, width, height, Component.literal(labelFn.apply(initial)));
        this.options = options;
        this.filtered = options;
        this.labelFn = labelFn;
        this.selected = initial;
        this.onSelect = onSelect;
        this.filterable = options.size() > FILTER_MIN_OPTIONS;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public T getSelected() {
        return selected;
    }

    private int filterRowCount() {
        return filterable ? 1 : 0;
    }

    private void applyFilter() {
        String[] keywords = SearchMatcher.keywords(filterText.toString());
        if (keywords.length == 0) {
            filtered = options;
        } else {
            if (searchKeys == null) {
                searchKeys = new ArrayList<>(options.size());
                for (T option : options) {
                    searchKeys.add(SearchMatcher.textKey(labelFn.apply(option)));
                }
            }
            List<T> matches = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                if (SearchMatcher.matchesKey(searchKeys.get(i), keywords)) {
                    matches.add(options.get(i));
                }
            }
            filtered = matches;
        }
        scrollOffset = 0;
    }

    private int overlayHeightFor(int optionCount) {
        return filterRowCount() * ROW_HEIGHT + Math.min(optionCount, MAX_VISIBLE_ROWS) * ROW_HEIGHT;
    }

    /** Opens downward like a normal dropdown unless the full-size popup would run off the bottom
     *  of the screen (small window / big GUI scale), in which case it opens upward. Decided from
     *  the unfiltered option count, so typing a filter doesn't make the popup flip sides. */
    private boolean opensUpward() {
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int below = getY() + height;
        return below + overlayHeightFor(options.size()) > screenHeight
                && getY() - overlayHeightFor(options.size()) >= 0;
    }

    private int overlayTop() {
        return opensUpward() ? getY() - overlayHeightFor(filtered.size()) : getY() + height;
    }

    private int listTop() {
        return overlayTop() + filterRowCount() * ROW_HEIGHT;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        g.fill(getX(), getY(), getX() + width, getY() + height, hovered ? COLOR_BG_HOVER : COLOR_BG);
        drawBorder(g, getX(), getY(), width, height);

        var font = Minecraft.getInstance().font;
        String label = labelFn.apply(selected);
        int maxTextWidth = width - 20;
        if (font.width(label) > maxTextWidth) {
            label = font.plainSubstrByWidth(label, maxTextWidth - font.width("…")) + "…";
        }
        g.drawString(font, label, getX() + 6, getY() + (height - 8) / 2, COLOR_TEXT, false);
        g.drawString(font, open ? "▲" : "▼", getX() + width - 13, getY() + (height - 8) / 2, COLOR_ARROW, false);
    }

    /** Must be called last, after everything else on the screen, so the popup paints on top. */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        // Item icons (in the registry grid this popup usually sits above) render themselves
        // 150 units forward in the pose stack so they visually pop in front of flat UI — push
        // further than that or this popup gets depth-tested away behind whatever icons are
        // underneath it, even though it's drawn later.
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        renderOverlayContent(g, mouseX, mouseY);
        g.pose().popPose();
    }

    private void renderOverlayContent(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int rows = Math.min(filtered.size(), MAX_VISIBLE_ROWS);
        int overlayTop = overlayTop();
        int listTop = listTop();
        int overlayHeight = overlayHeightFor(filtered.size());

        g.fill(getX(), overlayTop, getX() + width, overlayTop + overlayHeight, COLOR_OVERLAY_BG);
        drawBorder(g, getX(), overlayTop, width, overlayHeight);

        if (filterable) {
            g.fill(getX() + 1, overlayTop + 1, getX() + width - 1, listTop - 1, COLOR_FILTER_BG);
            String shown = filterText.length() == 0 ? "输入筛选…" : filterText.toString() + "_";
            g.drawString(font, shown, getX() + 5, overlayTop + (ROW_HEIGHT - 8) / 2, filterText.length() == 0 ? COLOR_MUTED : COLOR_TEXT, false);
            g.hLine(getX(), getX() + width - 1, listTop - 1, COLOR_BORDER_DARK);
        }

        if (filtered.isEmpty()) {
            g.drawString(font, "没有匹配项", getX() + 6, listTop + (ROW_HEIGHT - 8) / 2, COLOR_MUTED, false);
        }

        for (int i = 0; i < rows; i++) {
            T opt = filtered.get(scrollOffset + i);
            int rowY = listTop + i * ROW_HEIGHT;
            boolean rowHovered = mouseX >= getX() && mouseX < getX() + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (rowHovered) {
                g.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            }
            String label = labelFn.apply(opt);
            int maxTextWidth = width - 10;
            if (font.width(label) > maxTextWidth) {
                label = font.plainSubstrByWidth(label, maxTextWidth - font.width("…")) + "…";
            }
            g.drawString(font, label, getX() + 6, rowY + (ROW_HEIGHT - 8) / 2, COLOR_TEXT, false);
        }

        if (filtered.size() > MAX_VISIBLE_ROWS) {
            int trackX = getX() + width - 3;
            int listHeight = rows * ROW_HEIGHT;
            g.fill(trackX, listTop, trackX + 2, listTop + listHeight, 0xFF0A0A0D);
            int maxOffset = filtered.size() - MAX_VISIBLE_ROWS;
            int thumbHeight = Math.max(8, listHeight * MAX_VISIBLE_ROWS / filtered.size());
            int thumbY = listTop + (listHeight - thumbHeight) * scrollOffset / Math.max(1, maxOffset);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFF8A8A9A);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, COLOR_BORDER_LIGHT);
        g.fill(x, y, x + 1, y + h, COLOR_BORDER_LIGHT);
        g.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER_DARK);
        g.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER_DARK);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (open) {
            int rows = Math.min(filtered.size(), MAX_VISIBLE_ROWS);
            int listTop = listTop();
            if (mouseX >= getX() && mouseX < getX() + width && mouseY >= listTop && mouseY < listTop + rows * ROW_HEIGHT) {
                int index = scrollOffset + (int) ((mouseY - listTop) / ROW_HEIGHT);
                if (index >= 0 && index < filtered.size()) {
                    selected = filtered.get(index);
                    setMessage(Component.literal(labelFn.apply(selected)));
                    onSelect.accept(selected);
                    open = false;
                }
                return true;
            }
            // Clicking the filter strip (or the closed-button area while open) keeps it open
            // so typing still works; anywhere else closes the popup without selecting.
            int overlayTop = overlayTop();
            boolean onFilterStrip = filterable && mouseY >= overlayTop && mouseY < listTop;
            boolean onOwnButton = isMouseOver(mouseX, mouseY);
            if (!onFilterStrip && !onOwnButton) {
                open = false;
            }
            return true;
        }
        if (isMouseOver(mouseX, mouseY)) {
            open = true;
            scrollOffset = 0;
            filterText.setLength(0);
            filtered = options;
            return true;
        }
        return false;
    }

    /** While open, the popup owns the scroll wheel — otherwise it'd fall through to whatever
     *  scrollable widget (usually the big item list) happens to sit underneath the popup. */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open) {
            return false;
        }
        int maxOffset = Math.max(0, filtered.size() - MAX_VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    /** While open, every keystroke feeds the filter box instead of reaching other widgets. */
    public boolean charTyped(char c, int modifiers) {
        if (!open || !filterable || Character.isISOControl(c)) {
            return open;
        }
        filterText.append(c);
        applyFilter();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) {
            return false;
        }
        if (filterable && keyCode == GLFW.GLFW_KEY_BACKSPACE && filterText.length() > 0) {
            filterText.deleteCharAt(filterText.length() - 1);
            applyFilter();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            return true;
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
