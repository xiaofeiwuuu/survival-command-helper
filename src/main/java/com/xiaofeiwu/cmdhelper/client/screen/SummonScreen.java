package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SummonScreen extends CmdHelperScreen {

    private static final String NO_ROTATION = "不设置";
    private static final String USE_CURRENT = "跟我现在看的方向一样";

    // Minecraft yaw: 0=南 90=西 180=北 270=东.
    private static final Map<String, Double> YAW_PRESETS = new LinkedHashMap<>();

    static {
        YAW_PRESETS.put(NO_ROTATION, null);
        YAW_PRESETS.put("朝南", 0.0);
        YAW_PRESETS.put("朝西", 90.0);
        YAW_PRESETS.put("朝北", 180.0);
        YAW_PRESETS.put("朝东", 270.0);
        YAW_PRESETS.put(USE_CURRENT, null);
    }

    private RegistryGridWidget grid;
    private RegistryEntry selectedEntity;
    private CoordinateFields pos;
    private String rotationChoice = NO_ROTATION;
    private double snapshotYaw;
    private double snapshotPitch;
    private int paginationY;

    public SummonScreen(Screen parent) {
        super(Component.literal("召唤生物 /summon"), parent);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int labelX = centerX - 190;
        int fieldX = centerX - 156;

        this.pos = CoordinateFields.create(this.font, fieldX, 40, 55, 18);
        pos.withTooltips("召唤位置");
        rememberCoords("pos", pos);
        this.addRenderableWidget(pos.xBox);
        this.addRenderableWidget(pos.yBox);
        this.addRenderableWidget(pos.zBox);
        this.addRenderableWidget(Button.builder(Component.literal("用当前坐标"), b -> {
            var p = this.minecraft.player;
            if (p != null) pos.fillFrom(p.getX(), p.getY(), p.getZ());
        }).bounds(centerX + 29, 40, 90, 18).tooltip(Tooltip.create(Component.literal("把你现在站的位置填进左边的坐标格子\n（小数会向下取整）"))).build());
        this.addRenderableWidget(pos.createPasteBox(this.font, centerX + 123, 40, 76, 18));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(fieldX, 64, 224, 18,
                List.copyOf(YAW_PRESETS.keySet()), rotationChoice, s -> s,
                value -> {
                    this.rotationChoice = value;
                    if (value.equals(USE_CURRENT)) {
                        var p = this.minecraft.player;
                        if (p != null) {
                            snapshotYaw = p.getYRot();
                            snapshotPitch = p.getXRot();
                        }
                    }
                })));

        EditBox searchBox = new EditBox(this.font, centerX - 150, 92, 190, 18, Component.literal("搜索生物"));
        tip(searchBox, "搜索生物\n可输入中文名、拼音（全拼或首字母）、ID 或模组名。\n多个词用空格分隔，须全部匹配，如「石头 台阶」。");
        searchBox.setHint(Component.literal("搜索生物：中文名 / 拼音 / ID / 模组名"));
        remember("search", searchBox);
        searchBox.setResponder(q -> this.grid.setQuery(q));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.entityTypes());
        String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 92, 106, 18,
                modNames, savedMod, s -> s,
                value -> {
                    saveState("mod", value);
                    this.grid.setModFilter(value);
                })));

        int gridTop = 114;
        int rows = Math.max(1, ((this.height - 72) - gridTop) / RegistryGridWidget.CELL_SIZE);
        this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                RegistryDataSource.entityTypes(), entry -> this.selectedEntity = entry);
        this.grid.setSelected(selectedEntity);
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
        if (selectedEntity == null) {
            return Preview.problem("请先在下方选择要召唤的生物");
        }
        if (!pos.isComplete()) {
            return Preview.problem("位置坐标还没填完整");
        }
        return Preview.ok(buildCommand());
    }

    private String buildCommand() {
        if (selectedEntity == null || !pos.isComplete()) {
            return null;
        }
        String yaw = "";
        String pitch = "";
        if (rotationChoice.equals(USE_CURRENT)) {
            yaw = String.valueOf(snapshotYaw);
            pitch = String.valueOf(snapshotPitch);
        } else {
            Double presetYaw = YAW_PRESETS.get(rotationChoice);
            if (presetYaw != null) {
                yaw = String.valueOf(presetYaw);
                pitch = "0";
            }
        }
        return CommandBuilders.summon(selectedEntity.idString(), pos.coordString(), yaw, pitch);
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
        int labelX = this.width / 2 - 190;
        guiGraphics.drawString(this.font, "位置", labelX, pos.xBox.getY() + 5, COLOR_MUTED, false);
        guiGraphics.drawString(this.font, "朝向", labelX, 64 + 5, COLOR_MUTED, false);
        grid.render(guiGraphics, mouseX, mouseY);
        drawPaginationLabel(guiGraphics, grid, paginationY);
        if (!isAnyDropdownOpen()) {
            grid.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }
}
