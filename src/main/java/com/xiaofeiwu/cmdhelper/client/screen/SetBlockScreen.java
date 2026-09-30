package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.CommandTreeReader;
import com.xiaofeiwu.cmdhelper.client.command.FillModeLabels;
import com.xiaofeiwu.cmdhelper.client.registry.BlockFacing;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistryGridWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistrySearchWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class SetBlockScreen extends CmdHelperScreen {

    private static final String DEFAULT_MODE = "(默认)";

    private CoordinateFields pos;
    private RegistryGridWidget grid;
    private RegistryEntry selectedBlock;
    private String mode = DEFAULT_MODE;
    private String facing = BlockFacing.NONE;
    private String half = BlockFacing.HALF_NONE;
    private int paginationY;

    public SetBlockScreen(Screen parent) {
        super(Component.literal("放置方块 /setblock"), parent);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        this.pos = CoordinateFields.create(this.font, centerX - 156, 42, 50, 18);
        pos.withTooltips("放置位置");
        rememberCoords("pos", pos);
        this.addRenderableWidget(pos.xBox);
        this.addRenderableWidget(pos.yBox);
        this.addRenderableWidget(pos.zBox);

        this.addRenderableWidget(Button.builder(Component.literal("使用当前坐标"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                pos.fillFrom(p.getX(), p.getY(), p.getZ());
            }
        }).bounds(centerX + 14, 42, 90, 18).tooltip(Tooltip.create(Component.literal("把你现在站的位置填进左边的坐标格子\n（小数会向下取整）"))).build());
        this.addRenderableWidget(pos.createPasteBox(this.font, centerX + 108, 42, 90, 18));

        List<String> modes = new ArrayList<>();
        modes.add(DEFAULT_MODE);
        CommandTreeReader.findNode("setblock", "pos", "block").ifPresentOrElse(
                node -> modes.addAll(CommandTreeReader.literalChildNames(node)),
                () -> modes.addAll(List.of("destroy", "keep", "replace"))
        );

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 66, 190, 18,
                modes, mode, FillModeLabels::labelFor, value -> this.mode = value)));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 66, 140, 18,
                BlockFacing.LABELS, facing, s -> "朝向: " + s, value -> this.facing = value)));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 92, 300, 18,
                BlockFacing.HALF_LABELS, half, s -> "上/下半: " + s, value -> this.half = value)));

        EditBox searchBox = new EditBox(this.font, centerX - 150, 118, 190, 18, Component.literal("搜索方块"));
        tip(searchBox, "搜索方块\n可输入中文名、拼音（全拼或首字母）、ID 或模组名。\n多个词用空格分隔，须全部匹配，如「石头 台阶」。");
        searchBox.setHint(Component.literal("搜索方块：中文名 / 拼音 / ID / 模组名"));
        remember("search", searchBox);
        searchBox.setResponder(q -> this.grid.setQuery(q));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.blocks());
        String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 118, 106, 18,
                modNames, savedMod, s -> s,
                value -> {
                    saveState("mod", value);
                    this.grid.setModFilter(value);
                })));

        int gridTop = 140;
        int rows = Math.max(1, ((this.height - 72) - gridTop) / RegistryGridWidget.CELL_SIZE);
        this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                RegistryDataSource.blocks(), entry -> this.selectedBlock = entry);
        this.grid.setSelected(selectedBlock);
        if (!savedMod.equals(RegistryGridWidget.ALL_MODS)) {
            this.grid.setModFilter(savedMod);
        }
        if (!searchBox.getValue().isEmpty()) {
            this.grid.setQuery(searchBox.getValue());
        }
        paginationY = gridTop + grid.pixelHeight() + 6;
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
    protected Preview preview() {
        if (selectedBlock == null) {
            return Preview.problem("请先在下方选择要放置的方块");
        }
        if (!pos.isComplete()) {
            return Preview.problem("坐标还没填完整");
        }
        String note = BlockFacing.resolve(selectedBlock.id(), facing, half).ignoredNote();
        return note == null ? Preview.ok(buildCommand()) : Preview.warn(buildCommand(), note);
    }

    private String buildCommand() {
        if (selectedBlock == null || !pos.isComplete()) {
            return null;
        }
        String modeArg = mode.equals(DEFAULT_MODE) ? null : mode;
        String blockId = selectedBlock.idString() + BlockFacing.stateSuffix(selectedBlock.id(), facing, half);
        return CommandBuilders.setBlock(pos.coordString(), blockId, modeArg);
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
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.drawString(this.font, "坐标", this.width / 2 - 190, pos.xBox.getY() + 5, COLOR_MUTED, false);
        grid.render(guiGraphics, mouseX, mouseY);
        drawPaginationLabel(guiGraphics, grid, paginationY);
        if (!isAnyDropdownOpen()) {
            grid.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }
}
