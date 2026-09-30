package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;
import com.xiaofeiwu.cmdhelper.client.registry.SearchMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An icon grid (many items per row, like the creative inventory) with page-turning instead
 * of scrolling — a single-column list wastes almost all of the screen width once you're
 * looking at hundreds of items. Not a real Minecraft widget (AbstractWidget doesn't fit a
 * grid well); the owning screen drives it directly: render()/mouseClicked()/mouseScrolled()
 * each frame/event, and renderTooltip() last so hover text paints on top of everything.
 */
public class RegistryGridWidget {

    public static final String ALL_MODS = RegistrySearchWidget.ALL_MODS;
    public static final int CELL_SIZE = 20;
    private static final int ICON_SIZE = 16;

    private final List<RegistryEntry> all;
    private final Consumer<RegistryEntry> onPick;
    private final int x;
    private final int y;
    private final int columns;
    private final int rows;
    private final int itemsPerPage;

    private String query = "";
    private String modFilter = ALL_MODS;
    private List<RegistryEntry> filtered;
    private int page = 0;
    private RegistryEntry hovered;
    private RegistryEntry selected;
    // Lets a screen flag entries that are "in" something (e.g. the hostile list) with a green tick.
    private Predicate<RegistryEntry> marked = e -> false;
    // Icon lookups (registry query + new ItemStack) used to run for every visible cell every
    // frame. Entries are the same instances for the widget's whole life, so identity is enough.
    private final Map<RegistryEntry, ItemStack> iconCache = new IdentityHashMap<>();

    /** width sets the (auto-computed) column count; rows is a fixed row count chosen by the screen. */
    public RegistryGridWidget(int x, int y, int width, int rows, List<RegistryEntry> all, Consumer<RegistryEntry> onPick) {
        this.x = x;
        this.y = y;
        this.columns = Math.max(1, width / CELL_SIZE);
        this.rows = Math.max(1, rows);
        this.itemsPerPage = columns * this.rows;
        this.all = all;
        this.onPick = onPick;
        rebuild();
    }

    /** Total pixel height this grid occupies, for screens to lay out whatever comes after it. */
    public int pixelHeight() {
        return rows * CELL_SIZE;
    }

    public void setQuery(String query) {
        this.query = query;
        rebuild();
    }

    public void setModFilter(String modName) {
        this.modFilter = modName;
        rebuild();
    }

    /** Re-highlights an entry the owning screen already remembers, e.g. after a window resize
     *  rebuilt this widget — otherwise the screen would still act on a pick nobody can see. */
    public void setSelected(RegistryEntry entry) {
        this.selected = entry;
    }

    /** Entries for which this returns true are drawn with a green tick; asked afresh every frame. */
    public void setMarked(Predicate<RegistryEntry> marked) {
        this.marked = marked;
    }

    private void rebuild() {
        String[] keywords = SearchMatcher.keywords(query);
        this.filtered = all.stream()
                .filter(e -> modFilter.equals(ALL_MODS) || e.modName().equals(modFilter))
                .filter(e -> e.matches(keywords))
                .collect(Collectors.toList());
        this.page = 0;
    }

    public int totalPages() {
        return Math.max(1, (filtered.size() + itemsPerPage - 1) / itemsPerPage);
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

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        hovered = null;
        var font = Minecraft.getInstance().font;

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "没有匹配项", x + columns * CELL_SIZE / 2, y + rows * CELL_SIZE / 2 - 4, 0xFFA8A8B4);
            return;
        }

        int start = page * itemsPerPage;
        int end = Math.min(filtered.size(), start + itemsPerPage);
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % columns;
            int row = idx / columns;
            int cellX = x + col * CELL_SIZE;
            int cellY = y + row * CELL_SIZE;
            RegistryEntry entry = filtered.get(i);

            boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE && mouseY >= cellY && mouseY < cellY + CELL_SIZE;
            boolean isSelected = entry.equals(selected);
            if (isHovered) {
                hovered = entry;
            }
            g.fill(cellX, cellY, cellX + CELL_SIZE - 1, cellY + CELL_SIZE - 1,
                    isSelected ? 0xFF5A4A1A : (isHovered ? 0xFF3A3A48 : (idx % 2 == 0 ? 0x501A1A22 : 0x50151519)));

            renderIcon(g, entry, cellX + 2, cellY + 2);

            if (marked.test(entry)) {
                drawMarkedTick(g, cellX, cellY);
            }

            if (isSelected) {
                drawSelectionBorder(g, cellX, cellY);
            }
        }
    }

    private void renderIcon(GuiGraphics g, RegistryEntry entry, int iconX, int iconY) {
        ItemStack stack = iconCache.computeIfAbsent(entry, RegistryGridWidget::iconFor);
        if (stack.isEmpty()) {
            // Air, water, lava, fire, technical blocks and modded oddities have no item form —
            // without this they'd be blank cells you can only identify by hovering.
            drawFallbackGlyph(g, entry, iconX, iconY);
        } else {
            g.renderItem(stack, iconX, iconY);
        }
    }

    private static ItemStack iconFor(RegistryEntry entry) {
        switch (entry.kind()) {
            case ITEM -> {
                var item = ForgeRegistries.ITEMS.getValue(entry.id());
                return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            }
            case BLOCK -> {
                Block block = ForgeRegistries.BLOCKS.getValue(entry.id());
                return block == null || block.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(block.asItem());
            }
            case ENTITY_TYPE -> {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entry.id());
                var egg = type != null ? SpawnEggItem.byId(type) : null;
                return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
            }
        }
        return ItemStack.EMPTY;
    }

    private static final int COLOR_MARKED = 0xFF55DD77;

    private void drawMarkedTick(GuiGraphics g, int cellX, int cellY) {
        int x2 = cellX + CELL_SIZE - 1;
        g.fill(cellX, cellY, x2, cellY + 1, COLOR_MARKED);
        g.fill(cellX, cellY + CELL_SIZE - 2, x2, cellY + CELL_SIZE - 1, COLOR_MARKED);
        g.fill(cellX, cellY, cellX + 1, cellY + CELL_SIZE - 1, COLOR_MARKED);
        g.fill(x2 - 1, cellY, x2, cellY + CELL_SIZE - 1, COLOR_MARKED);
        // A little "✓" in the top-right corner, drawn above the item icon.
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        g.drawString(Minecraft.getInstance().font, "✓", x2 - 8, cellY + 1, COLOR_MARKED, true);
        g.pose().popPose();
    }

    private static final int COLOR_SELECTION_BORDER = 0xFFFFD34D;

    private void drawSelectionBorder(GuiGraphics g, int cellX, int cellY) {
        int x2 = cellX + CELL_SIZE - 1;
        int y2 = cellY + CELL_SIZE - 1;
        g.fill(cellX, cellY, x2, cellY + 2, COLOR_SELECTION_BORDER);
        g.fill(cellX, y2 - 2, x2, y2, COLOR_SELECTION_BORDER);
        g.fill(cellX, cellY, cellX + 2, y2, COLOR_SELECTION_BORDER);
        g.fill(x2 - 2, cellY, x2, y2, COLOR_SELECTION_BORDER);
    }

    private void drawFallbackGlyph(GuiGraphics g, RegistryEntry entry, int iconX, int iconY) {
        var font = Minecraft.getInstance().font;
        String glyph = entry.displayName().isEmpty() ? "?" : entry.displayName().substring(0, 1);
        g.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF3A3A46);
        g.drawCenteredString(font, glyph, iconX + ICON_SIZE / 2, iconY + 4, 0xFFEDEDED);
    }

    /** Call this last, after everything else on the screen, so it paints on top. */
    public void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hovered == null) {
            return;
        }
        List<Component> lines = List.of(
                Component.literal(hovered.displayName()),
                Component.literal(hovered.idString()).withStyle(ChatFormatting.GRAY),
                Component.literal(hovered.modName()).withStyle(ChatFormatting.DARK_GRAY)
        );
        g.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || filtered.isEmpty()) {
            return false;
        }
        int gridWidth = columns * CELL_SIZE;
        int gridHeight = rows * CELL_SIZE;
        if (mouseX < x || mouseX >= x + gridWidth || mouseY < y || mouseY >= y + gridHeight) {
            return false;
        }
        int col = (int) ((mouseX - x) / CELL_SIZE);
        int row = (int) ((mouseY - y) / CELL_SIZE);
        int idx = page * itemsPerPage + row * columns + col;
        if (idx >= 0 && idx < filtered.size()) {
            selected = filtered.get(idx);
            onPick.accept(selected);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int gridWidth = columns * CELL_SIZE;
        int gridHeight = rows * CELL_SIZE;
        if (mouseX < x || mouseX >= x + gridWidth || mouseY < y || mouseY >= y + gridHeight) {
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
