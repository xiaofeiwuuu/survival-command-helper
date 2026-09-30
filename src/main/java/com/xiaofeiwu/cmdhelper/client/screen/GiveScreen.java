package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.CommandTreeReader;
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

import java.util.List;

public class GiveScreen extends CmdHelperScreen {

    private static final List<String> TARGETS = List.of("@s", "@p", "@a", "@r");
    private static final List<String> TARGET_LABELS = List.of("自己 (@s)", "最近的玩家 (@p)", "所有玩家 (@a)", "随机玩家 (@r)");

    // The server may allow far more (up to Integer.MAX_VALUE), but there's no real reason to
    // give yourself a nine-digit stack of anything — cap the UI at a sane amount.
    private static final int UI_COUNT_CAP = 9999;

    private String target = "@s";
    private RegistryEntry selectedItem;
    private EditBox countBox;
    private RegistryGridWidget grid;
    private int paginationY;

    private int countMin = 1;
    private int countMax = UI_COUNT_CAP;

    public GiveScreen(Screen parent) {
        super(Component.translatable("screen.cmdhelper.give.title"), parent);
    }

    @Override
    protected void init() {
        super.init();

        CommandTreeReader.findNode("give", "targets", "item", "count")
                .flatMap(CommandTreeReader::integerBounds)
                .ifPresent(bounds -> {
                    countMin = bounds[0];
                    countMax = Math.min(bounds[1], UI_COUNT_CAP);
                });

        int centerX = this.width / 2;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 190, 20,
                TARGETS, target, t -> TARGET_LABELS.get(TARGETS.indexOf(t)),
                value -> this.target = value)));

        this.countBox = new EditBox(this.font, centerX + 66, 41, 84, 18, Component.translatable("gui.cmdhelper.count"));
        this.countBox.setValue("1");
        this.countBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("count", this.countBox);
        this.addRenderableWidget(this.countBox);

        EditBox searchBox = new EditBox(this.font, centerX - 150, 66, 190, 18, Component.translatable("gui.cmdhelper.search_item"));
        searchBox.setHint(Component.literal("搜索物品：中文名 / 拼音 / ID / 模组名"));
        remember("search", searchBox);
        searchBox.setResponder(q -> this.grid.setQuery(q));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.items());
        String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 66, 106, 18,
                modNames, savedMod, s -> s,
                value -> {
                    saveState("mod", value);
                    this.grid.setModFilter(value);
                })));

        int gridTop = 90;
        int rows = Math.max(1, ((this.height - 72) - gridTop) / RegistryGridWidget.CELL_SIZE);
        this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                RegistryDataSource.items(), entry -> this.selectedItem = entry);
        this.grid.setSelected(selectedItem);
        if (!savedMod.equals(RegistryGridWidget.ALL_MODS)) {
            this.grid.setModFilter(savedMod);
        }
        if (!searchBox.getValue().isEmpty()) {
            this.grid.setQuery(searchBox.getValue());
        }

        this.paginationY = gridTop + grid.pixelHeight() + 6;
        addPaginationButtons(grid, paginationY);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.execute(cmd);
            }
        }).bounds(centerX + 10, this.height - 26, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX + 105, this.height - 26, 90, 18).build());
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
    protected Preview preview() {
        if (selectedItem == null) {
            return Preview.problem("请先在下方选择要给予的物品");
        }
        int typed = parseIntOr(countBox.getValue(), 1);
        int clamped = Math.max(countMin, Math.min(countMax, typed));
        String command = buildCommand();
        return typed == clamped ? Preview.ok(command)
                : Preview.warn(command, "数量 " + typed + " 超出允许范围 " + countMin + "~" + countMax + "，将按 " + clamped + " 发送");
    }

    private String buildCommand() {
        if (selectedItem == null) {
            return null;
        }
        int count = parseIntOr(countBox.getValue(), 1);
        return CommandBuilders.give(target, selectedItem.idString(), count, countMin, countMax);
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.drawString(this.font, "数量", centerX() + 44, countBox.getY() + 5, COLOR_MUTED, false);
        grid.render(guiGraphics, mouseX, mouseY);
        drawPaginationLabel(guiGraphics, grid, paginationY);
        if (!isAnyDropdownOpen()) {
            grid.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private int centerX() {
        return this.width / 2;
    }
}
