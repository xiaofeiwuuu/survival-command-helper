package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.CommandTreeReader;
import com.xiaofeiwu.cmdhelper.client.command.FillModeLabels;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import com.xiaofeiwu.cmdhelper.client.command.RelativeRegion;
import com.xiaofeiwu.cmdhelper.client.preview.FillPreview;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;
import com.xiaofeiwu.cmdhelper.client.registry.BlockFacing;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistryGridWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistrySearchWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FillScreen extends CmdHelperScreen {

    private enum CoordMode {MANUAL, CENTERED, FORWARD}

    private static final Map<CoordMode, String> COORD_MODE_LABELS = Map.of(
            CoordMode.MANUAL, "手动输入坐标",
            CoordMode.CENTERED, "以自己为中心",
            CoordMode.FORWARD, "从自己往前铺开"
    );

    private static final String DEFAULT_MODE = "(默认)";
    private static final String NO_FILTER = "(不限制，全部替换)";
    private static final int COORD_BOX_W = 34;
    private static final int COORD_GROUP_W = 3 * COORD_BOX_W + 12;
    private static final int COORD_LABEL_W = 22;
    private static final int USE_CURRENT_BTN_W = 46;

    private CoordMode coordMode = CoordMode.MANUAL;
    private String filterBlockId = NO_FILTER;
    private boolean includeFloor = true;

    // MANUAL
    private CoordinateFields from;
    private CoordinateFields to;
    // CENTERED
    private EditBox radiusBox;
    // FORWARD
    private EditBox lengthBox;
    private EditBox widthBox;
    // CENTERED + FORWARD share this
    private EditBox heightBox;
    private Checkbox includeFloorCheckbox;

    private RegistryGridWidget grid;
    private RegistryEntry selectedBlock;
    private String mode = DEFAULT_MODE;
    private String facing = BlockFacing.NONE;
    private String half = BlockFacing.HALF_NONE;
    private int paginationY;

    public FillScreen(Screen parent) {
        super(Component.literal("填充方块 /fill"), parent);
    }

    @Override
    protected void init() {
        // The checkbox is about to be rebuilt; keep whatever the player last ticked.
        if (includeFloorCheckbox != null) {
            includeFloor = includeFloorCheckbox.selected();
        }
        super.init();

        int centerX = this.width / 2;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 145, 20,
                List.of(CoordMode.values()), coordMode, COORD_MODE_LABELS::get,
                value -> this.minecraft.setScreen(rebuiltWithCoordMode(value)))));

        List<String> blockIds = RegistryDataSource.blockIds();
        List<String> filterOptions = new ArrayList<>(blockIds.size() + 1);
        filterOptions.add(NO_FILTER);
        filterOptions.addAll(blockIds);
        Map<String, String> filterLabels = RegistryDataSource.blockLabels();

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 5, 40, 145, 20,
                filterOptions, filterBlockId,
                id -> "替换: " + (id.equals(NO_FILTER) ? NO_FILTER : filterLabels.getOrDefault(id, id)),
                value -> this.filterBlockId = value)));

        switch (coordMode) {
            case MANUAL -> initManualRow(centerX);
            case CENTERED -> initCenteredRow(centerX);
            case FORWARD -> initForwardRow(centerX);
        }

        if (coordMode == CoordMode.CENTERED || coordMode == CoordMode.FORWARD) {
            String label = "包含脚下一层（从脚下方块 Y-1 开始往上填）";
            int checkboxWidth = this.font.width(label) + 30;
            this.includeFloorCheckbox = this.addRenderableWidget(
                    new Checkbox(centerX - 150, 92, checkboxWidth, 18, Component.literal(label), includeFloor));
        }

        List<String> modes = new ArrayList<>();
        modes.add(DEFAULT_MODE);
        CommandTreeReader.findNode("fill", "from", "to", "block").ifPresentOrElse(
                node -> modes.addAll(CommandTreeReader.literalChildNames(node)),
                () -> modes.addAll(List.of("replace", "keep", "destroy", "hollow", "outline"))
        );

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 118, 190, 18,
                modes, mode, FillModeLabels::labelFor, value -> this.mode = value)));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 118, 140, 18,
                BlockFacing.LABELS, facing, s -> "朝向: " + s, value -> this.facing = value)));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 144, 300, 18,
                BlockFacing.HALF_LABELS, half, s -> "上/下半: " + s, value -> this.half = value)));

        EditBox searchBox = new EditBox(this.font, centerX - 150, 170, 190, 18, Component.literal("搜索方块"));
        searchBox.setHint(Component.literal("搜索方块：中文名 / 拼音 / ID / 模组名"));
        remember("search", searchBox);
        searchBox.setResponder(q -> this.grid.setQuery(q));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.blocks());
        String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, 170, 106, 18,
                modNames, savedMod, s -> s,
                value -> {
                    saveState("mod", value);
                    this.grid.setModFilter(value);
                })));

        int gridTop = 192;
        int rows = Math.max(1, ((this.height - 72) - gridTop) / RegistryGridWidget.CELL_SIZE);
        this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                RegistryDataSource.blocks(), entry -> this.selectedBlock = entry);
        // After a resize/mode switch the grid is brand new: put back the filter, the search and
        // the highlight, so what's on screen matches the block the command will actually use.
        this.grid.setSelected(selectedBlock);
        if (!savedMod.equals(RegistryGridWidget.ALL_MODS)) {
            this.grid.setModFilter(savedMod);
        }
        if (!searchBox.getValue().isEmpty()) {
            this.grid.setQuery(searchBox.getValue());
        }
        paginationY = gridTop + grid.pixelHeight() + 6;
        addPaginationButtons(grid, paginationY);

        this.addRenderableWidget(Button.builder(Component.literal("半透明预览"), b -> {
            Supplier<FillPreview.Plan> supplier = planSupplier();
            if (supplier != null) {
                FillPreview.start(supplier);
                this.minecraft.setScreen(null);
            }
        }).bounds(centerX - 85, this.height - 26, 90, 18).build());

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

    private FillScreen rebuiltWithCoordMode(CoordMode newMode) {
        FillScreen next = new FillScreen(parent);
        next.coordMode = newMode;
        next.mode = this.mode;
        next.facing = this.facing;
        next.half = this.half;
        next.filterBlockId = this.filterBlockId;
        next.includeFloor = this.includeFloorCheckbox != null ? this.includeFloorCheckbox.selected() : this.includeFloor;
        next.selectedBlock = this.selectedBlock;
        passInputsTo(next);
        return next;
    }

    private void initManualRow(int centerX) {
        int x0 = centerX - 190;
        int fromFieldX = x0 + COORD_LABEL_W;
        this.from = CoordinateFields.create(this.font, fromFieldX, 66, COORD_BOX_W, 18);
        rememberCoords("from", from);
        addCoordWidgets(from);
        int fromBtnX = fromFieldX + COORD_GROUP_W + 4;
        this.addRenderableWidget(Button.builder(Component.literal("用当前"), b -> {
            var p = this.minecraft.player;
            if (p != null) from.fillFrom(p.getX(), p.getY(), p.getZ());
        }).bounds(fromBtnX, 66, USE_CURRENT_BTN_W, 18).build());

        int toLabelX = fromBtnX + USE_CURRENT_BTN_W + 10;
        int toFieldX = toLabelX + COORD_LABEL_W;
        this.to = CoordinateFields.create(this.font, toFieldX, 66, COORD_BOX_W, 18);
        rememberCoords("to", to);
        addCoordWidgets(to);
        int toBtnX = toFieldX + COORD_GROUP_W + 4;
        this.addRenderableWidget(Button.builder(Component.literal("用当前"), b -> {
            var p = this.minecraft.player;
            if (p != null) to.fillFrom(p.getX(), p.getY(), p.getZ());
        }).bounds(toBtnX, 66, USE_CURRENT_BTN_W, 18).build());
    }

    private void initCenteredRow(int centerX) {
        int x0 = centerX - 150;
        this.radiusBox = new EditBox(this.font, x0 + 26, 66, 50, 18, Component.literal("半径"));
        this.radiusBox.setValue("5");
        this.radiusBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("radius", this.radiusBox);
        this.addRenderableWidget(this.radiusBox);

        int hX = x0 + 26 + 50 + 20;
        this.heightBox = new EditBox(this.font, hX + 26, 66, 50, 18, Component.literal("高度"));
        this.heightBox.setValue("1");
        this.heightBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("centeredHeight", this.heightBox);
        this.addRenderableWidget(this.heightBox);
    }

    private void initForwardRow(int centerX) {
        int x0 = centerX - 150;
        this.lengthBox = new EditBox(this.font, x0 + 26, 66, 46, 18, Component.literal("长度"));
        this.lengthBox.setValue("10");
        this.lengthBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("length", this.lengthBox);
        this.addRenderableWidget(this.lengthBox);

        int wX = x0 + 26 + 46 + 14;
        this.widthBox = new EditBox(this.font, wX + 26, 66, 46, 18, Component.literal("宽度"));
        this.widthBox.setValue("3");
        this.widthBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("width", this.widthBox);
        this.addRenderableWidget(this.widthBox);

        int hX = wX + 26 + 46 + 14;
        this.heightBox = new EditBox(this.font, hX + 26, 66, 46, 18, Component.literal("高度"));
        this.heightBox.setValue("3");
        this.heightBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        remember("forwardHeight", this.heightBox);
        this.addRenderableWidget(this.heightBox);
    }

    private void addCoordWidgets(CoordinateFields f) {
        this.addRenderableWidget(f.xBox);
        this.addRenderableWidget(f.yBox);
        this.addRenderableWidget(f.zBox);
    }

    /** Why no command can be built yet, or null when the form is complete and consistent. */
    private String problem() {
        if (selectedBlock == null) {
            return "请先在下方选择要填充的方块";
        }
        if (coordMode == CoordMode.MANUAL && (!from.isComplete() || !to.isComplete())) {
            return "起点和终点的坐标还没填完整";
        }
        // "replace only <block>" is only part of /fill's `replace` form. With keep/hollow/
        // outline/destroy the filter used to be dropped without a word, so the player thought
        // they were swapping one block type and were actually filling the whole region.
        boolean filterActive = !filterBlockId.equals(NO_FILTER);
        if (filterActive && !mode.equals(DEFAULT_MODE) && !mode.equals("replace")) {
            return "「替换: 指定方块」只在默认/替换模式下生效，请改模式或取消替换过滤（当前：" + FillModeLabels.labelFor(mode) + "）";
        }
        return null;
    }

    @Override
    protected Preview preview() {
        String problem = problem();
        if (problem != null) {
            return Preview.problem(problem);
        }
        Supplier<FillPreview.Plan> supplier = planSupplier();
        FillPreview.Plan plan = supplier == null ? null : supplier.get();
        if (plan == null) {
            return Preview.problem("暂时无法生成命令（玩家不在世界中）");
        }
        List<String> warnings = new ArrayList<>();
        RegionBounds bounds = RegionBounds.of(plan.from(), plan.to());
        if (bounds.exceedsFillLimit()) {
            warnings.add("区域共 " + bounds.volume() + " 格，超过默认上限 " + RegionBounds.FILL_BLOCK_LIMIT + "，服务器可能拒绝");
        }
        String ignored = BlockFacing.resolve(selectedBlock.id(), facing, half).ignoredNote();
        if (ignored != null) {
            warnings.add(ignored);
        }
        return warnings.isEmpty() ? Preview.ok(plan.command()) : Preview.warn(plan.command(), String.join("；", warnings));
    }

    private String buildCommand() {
        Supplier<FillPreview.Plan> supplier = planSupplier();
        FillPreview.Plan plan = supplier == null ? null : supplier.get();
        return plan == null ? null : plan.command();
    }

    /**
     * Snapshots every input now and returns a recipe that recomputes the region from the player's
     * position each time it's called — so "centered on me" / "in front of me" stay live while the
     * preview is showing. Deliberately captures no screen fields: the screen is gone by then.
     * Null when the form isn't complete enough to build a command.
     */
    private Supplier<FillPreview.Plan> planSupplier() {
        if (problem() != null) {
            return null;
        }
        boolean filterActive = !filterBlockId.equals(NO_FILTER);
        String modeArg;
        if (mode.equals(DEFAULT_MODE)) {
            modeArg = filterActive ? "replace " + filterBlockId : null;
        } else if (mode.equals("replace")) {
            modeArg = filterActive ? "replace " + filterBlockId : "replace";
        } else {
            modeArg = mode;
        }
        String blockId = selectedBlock.idString() + BlockFacing.stateSuffix(selectedBlock.id(), facing, half);

        boolean floor = includeFloorCheckbox != null ? includeFloorCheckbox.selected() : includeFloor;
        switch (coordMode) {
            case MANUAL -> {
                if (!from.isComplete() || !to.isComplete()) {
                    return null;
                }
                RelativeRegion.Corners fixed = new RelativeRegion.Corners(from.coordString(), to.coordString());
                return () -> plan(fixed, blockId, modeArg);
            }
            case CENTERED -> {
                int radius = parseIntOr(radiusBox.getValue(), 0);
                int height = parseIntOr(heightBox.getValue(), 1);
                return () -> {
                    var p = Minecraft.getInstance().player;
                    if (p == null) {
                        return null;
                    }
                    int startY = RelativeRegion.baseY((int) Math.floor(p.getY()), floor);
                    return plan(RelativeRegion.centered(
                            (int) Math.floor(p.getX()), startY, (int) Math.floor(p.getZ()), radius, height),
                            blockId, modeArg);
                };
            }
            case FORWARD -> {
                int length = parseIntOr(lengthBox.getValue(), 1);
                int width = parseIntOr(widthBox.getValue(), 1);
                int height = parseIntOr(heightBox.getValue(), 1);
                return () -> {
                    var p = Minecraft.getInstance().player;
                    if (p == null) {
                        return null;
                    }
                    int startY = RelativeRegion.baseY((int) Math.floor(p.getY()), floor);
                    Direction playerFacing = p.getDirection();
                    Direction right = playerFacing.getClockWise();
                    return plan(RelativeRegion.forward(
                            (int) Math.floor(p.getX()), startY, (int) Math.floor(p.getZ()),
                            playerFacing.getStepX(), playerFacing.getStepZ(), right.getStepX(), right.getStepZ(),
                            length, width, height),
                            blockId, modeArg);
                };
            }
            default -> {
                return null;
            }
        }
    }

    private static FillPreview.Plan plan(RelativeRegion.Corners c, String blockId, String modeArg) {
        return new FillPreview.Plan(c.from(), c.to(), CommandBuilders.fill(c.from(), c.to(), blockId, modeArg));
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
        switch (coordMode) {
            case MANUAL -> {
                int centerX = this.width / 2;
                int x0 = centerX - 190;
                int toLabelX = x0 + COORD_LABEL_W + COORD_GROUP_W + 4 + USE_CURRENT_BTN_W + 10;
                guiGraphics.drawString(this.font, "起点", x0, from.xBox.getY() + 5, COLOR_MUTED, false);
                guiGraphics.drawString(this.font, "终点", toLabelX, to.xBox.getY() + 5, COLOR_MUTED, false);
            }
            case CENTERED -> {
                guiGraphics.drawString(this.font, "半径", radiusBox.getX() - 26, radiusBox.getY() + 5, COLOR_MUTED, false);
                guiGraphics.drawString(this.font, "高度", heightBox.getX() - 26, heightBox.getY() + 5, COLOR_MUTED, false);
            }
            case FORWARD -> {
                guiGraphics.drawString(this.font, "长度", lengthBox.getX() - 26, lengthBox.getY() + 5, COLOR_MUTED, false);
                guiGraphics.drawString(this.font, "宽度", widthBox.getX() - 26, widthBox.getY() + 5, COLOR_MUTED, false);
                guiGraphics.drawString(this.font, "高度", heightBox.getX() - 26, heightBox.getY() + 5, COLOR_MUTED, false);
            }
        }
        grid.render(guiGraphics, mouseX, mouseY);
        drawPaginationLabel(guiGraphics, grid, paginationY);
        if (!isAnyDropdownOpen()) {
            grid.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }
}
