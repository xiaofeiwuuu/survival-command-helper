package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.hostile.HostileTypes;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistryGridWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistrySearchWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Edits the list of mobs that count as "hostile" for the kill screen. Clicking a mob toggles it
 * in or out of the list, and the change is saved straight away (config/cmdhelper/hostile_types.json).
 */
public class HostileListScreen extends CmdHelperScreen {

    private RegistryGridWidget grid;
    private int paginationY;
    private String lastAction = "";

    public HostileListScreen(Screen parent) {
        super(Component.literal("敌对生物名单"), parent);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        EditBox searchBox = new EditBox(this.font, centerX - 150, 40, 190, 18, Component.literal("搜索生物"));
        tip(searchBox, "搜索生物（找到后点击即可加入 / 移出敌对名单）\n可输入中文名、拼音（全拼或首字母）、ID 或模组名。\n多个词用空格分隔，须全部匹配，如「石头 台阶」。");
        searchBox.setHint(Component.literal("搜索生物：中文名 / 拼音 / ID / 模组名"));
        remember("search", searchBox);
        searchBox.setResponder(q -> this.grid.setQuery(q));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.livingEntityTypes());
        String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 40, 106, 18,
                modNames, savedMod, s -> s,
                value -> {
                    saveState("mod", value);
                    this.grid.setModFilter(value);
                })));

        int gridTop = 64;
        int rows = Math.max(1, ((this.height - 72) - gridTop) / RegistryGridWidget.CELL_SIZE);
        this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                RegistryDataSource.livingEntityTypes(), this::toggle);
        this.grid.setMarked(entry -> HostileTypes.shared().containsNormalized(entry.idString()));
        if (!savedMod.equals(RegistryGridWidget.ALL_MODS)) {
            this.grid.setModFilter(savedMod);
        }
        if (!searchBox.getValue().isEmpty()) {
            this.grid.setQuery(searchBox.getValue());
        }
        paginationY = gridTop + grid.pixelHeight() + 6;
        addPaginationButtons(grid, paginationY);

        this.addRenderableWidget(Button.builder(Component.literal("恢复默认名单"), b -> {
            HostileTypes.shared().resetToDefaults();
            lastAction = "已恢复默认名单（" + HostileTypes.DEFAULTS.size() + " 种）";
        }).bounds(centerX - 60, this.height - 26, 120, 18).build());
    }

    private void toggle(RegistryEntry entry) {
        boolean nowIn = HostileTypes.shared().toggle(entry.idString());
        lastAction = (nowIn ? "已加入名单：" : "已从名单移除：") + entry.displayName();
    }

    /** Names in the saved list that this game doesn't have (a mod that's no longer installed). */
    private int uninstalledCount() {
        Set<String> known = new HashSet<>();
        for (RegistryEntry e : RegistryDataSource.entityTypes()) {
            known.add(e.idString());
        }
        int missing = 0;
        for (String id : HostileTypes.shared().ids()) {
            if (!known.contains(id)) {
                missing++;
            }
        }
        return missing;
    }

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        return grid.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return grid.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        grid.render(g, mouseX, mouseY);
        drawPaginationLabel(g, grid, paginationY);

        int total = HostileTypes.shared().ids().size();
        int missing = uninstalledCount();
        String summary = "名单共 " + total + " 种，带绿色 ✓ 的在名单里，点击生物加入 / 移出"
                + (missing > 0 ? "（另有 " + missing + " 种当前游戏里没有）" : "");
        g.drawCenteredString(this.font, summary, centerX, this.height - 47, COLOR_MUTED);
        if (!lastAction.isEmpty()) {
            g.drawCenteredString(this.font, lastAction, centerX, this.height - 37, COLOR_ACCENT);
        }
        if (!isAnyDropdownOpen()) {
            grid.renderTooltip(g, mouseX, mouseY);
        }
    }
}
