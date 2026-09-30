package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.hostile.HostileClear;
import com.xiaofeiwu.cmdhelper.client.hostile.HostileScan;
import com.xiaofeiwu.cmdhelper.client.hostile.HostileTypes;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryEntry;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistryGridWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistrySearchWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KillScreen extends CmdHelperScreen {

    public enum Mode {
        RANGE_ALL("当前范围内所有实体"),
        HOSTILE("当前范围内敌对生物（可编辑名单）"),
        RANGE_TYPE("当前范围内指定生物"),
        NEAREST("最近的一个"),
        CUSTOM("自定义目标选择器");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Mode mode;

    private RegistryEntry selectedEntity;
    private EditBox rangeBox;
    private EditBox customBox;
    private RegistryGridWidget grid;
    private int paginationY;

    // HOSTILE mode
    private static final int HOSTILE_ROW_HEIGHT = 16;
    private static final int HOSTILE_LIST_TOP = 106;
    // Types on the hostile list that this game actually has, resolved once per init() rather than
    // per frame (the list can only change on HostileListScreen, and coming back re-runs init()).
    private HostileClear.Resolved hostile = HostileClear.resolve();
    private final Set<String> skippedThisRun = new HashSet<>();

    public KillScreen(Screen parent) {
        this(parent, Mode.RANGE_TYPE);
    }

    public KillScreen(Screen parent, Mode mode) {
        super(Component.translatable("screen.cmdhelper.kill.title"), parent);
        this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        int modeWidth = mode == Mode.CUSTOM ? 300 : 190;
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, modeWidth, 20,
                List.of(Mode.values()), mode, m -> m.label,
                value -> {
                    KillScreen next = new KillScreen(parent, value);
                    passInputsTo(next);
                    this.minecraft.setScreen(next);
                })));

        if (mode != Mode.CUSTOM) {
            this.rangeBox = new EditBox(this.font, centerX + 66, 41, 84, 18, Component.literal("范围"));
            this.rangeBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
            if (mode == Mode.HOSTILE) {
                // The one-click button on the main menu uses this same range, so it's saved
                // (not carried over from whatever range was typed in another mode).
                this.rangeBox.setValue(String.valueOf(HostileTypes.shared().range()));
                remember("hostileRange", this.rangeBox);
                this.rangeBox.setResponder(v -> {
                    try {
                        int typed = Integer.parseInt(v);
                        if (typed >= 1) {
                            HostileTypes.shared().setRange(typed);
                        }
                    } catch (NumberFormatException ignored) {
                        // empty / half-typed: keep the last saved range
                    }
                });
            } else {
                this.rangeBox.setValue("10");
                remember("range", this.rangeBox);
            }
            this.addRenderableWidget(this.rangeBox);
        }

        int y = 66;

        if (mode == Mode.HOSTILE) {
            hostile = HostileClear.resolve();
            this.addRenderableWidget(Button.builder(Component.literal("编辑敌对名单…"),
                    b -> this.minecraft.setScreen(new HostileListScreen(this)))
                    .bounds(centerX - 150, y, 110, 18).build());
        } else if (mode == Mode.CUSTOM) {
            this.customBox = new EditBox(this.font, centerX - 150, y, 300, 18, Component.literal("自定义选择器"));
            this.customBox.setHint(Component.literal("例如：@e[tag=boss]  （不含中括号外的 kill）"));
            this.customBox.setValue("@e[");
            remember("custom", this.customBox);
            this.addRenderableWidget(this.customBox);
            this.setInitialFocus(this.customBox);
        } else if (mode == Mode.RANGE_TYPE || mode == Mode.NEAREST) {
            EditBox searchBox = new EditBox(this.font, centerX - 150, y, 190, 18, Component.literal("搜索生物"));
            searchBox.setHint(Component.literal(mode == Mode.NEAREST ? "搜索生物（可留空）" : "搜索生物：中文名 / 拼音 / ID / 模组名"));
            remember("search", searchBox);
            searchBox.setResponder(q -> this.grid.setQuery(q));
            this.addRenderableWidget(searchBox);
            this.setInitialFocus(searchBox);

            List<String> modNames = RegistrySearchWidget.modNamesIn(RegistryDataSource.livingEntityTypes());
            String savedMod = savedState("mod", RegistryGridWidget.ALL_MODS);
            this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 44, y, 106, 18,
                    modNames, savedMod, s -> s,
                    value -> {
                        saveState("mod", value);
                        this.grid.setModFilter(value);
                    })));
            y += 26;

            int gridTop = y;
            int rows = Math.max(1, ((this.height - 104) - gridTop) / RegistryGridWidget.CELL_SIZE);
            this.grid = new RegistryGridWidget(centerX - 150, gridTop, 300, rows,
                    RegistryDataSource.livingEntityTypes(), entry -> this.selectedEntity = entry);
            this.grid.setSelected(selectedEntity);
            if (!savedMod.equals(RegistryGridWidget.ALL_MODS)) {
                this.grid.setModFilter(savedMod);
            }
            if (!searchBox.getValue().isEmpty()) {
                this.grid.setQuery(searchBox.getValue());
            }
            paginationY = gridTop + grid.pixelHeight() + 6;
            addPaginationButtons(grid, paginationY);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> {
            if (mode == Mode.HOSTILE) {
                runHostile();
                return;
            }
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.execute(cmd);
            }
        }).bounds(centerX + 10, this.height - 26, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            if (mode == Mode.HOSTILE) {
                List<String> commands = currentHostileCommands();
                if (!commands.isEmpty()) {
                    CommandExecutor.copyRaw(String.join("\n", commands.stream().map(c -> "/" + c).toList()));
                }
                return;
            }
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX + 105, this.height - 26, 90, 18).build());
    }

    // ---- HOSTILE mode: look around first, then kill only what is actually there ----------------

    private List<HostileScan.Found> scanSurroundings() {
        return HostileClear.scan(hostile, rangeOrDefault());
    }

    private List<String> currentHostileCommands() {
        return HostileScan.commands(scanSurroundings(), skippedThisRun, rangeOrDefault());
    }

    private String hostileName(String id) {
        return hostile.nameOf(id);
    }

    /** Scans again at the moment of the click — what was on screen a second ago may already be dead or gone. */
    private void runHostile() {
        List<HostileScan.Found> found = scanSurroundings();
        List<String> commands = HostileScan.commands(found, skippedThisRun, rangeOrDefault());
        if (commands.isEmpty()) {
            return;
        }
        CommandExecutor.executeBatch(commands, "清除敌对生物：" + describeFound(found));
    }

    private String describeFound(List<HostileScan.Found> found) {
        return HostileClear.describe(hostile, found, skippedThisRun);
    }

    private boolean hostileRowClicked(double mouseX, double mouseY, int button) {
        int left = this.width / 2 - 150;
        if (button != 0 || mouseX < left || mouseX >= left + 300 || mouseY < HOSTILE_LIST_TOP) {
            return false;
        }
        int row = (int) ((mouseY - HOSTILE_LIST_TOP) / HOSTILE_ROW_HEIGHT);
        List<HostileScan.Found> found = scanSurroundings();
        if (row < 0 || row >= found.size() || row >= maxHostileRows()) {
            return false;
        }
        String id = found.get(row).typeId();
        if (!skippedThisRun.remove(id)) {
            skippedThisRun.add(id);
        }
        return true;
    }

    private int maxHostileRows() {
        return Math.max(1, (this.height - 60 - HOSTILE_LIST_TOP) / HOSTILE_ROW_HEIGHT);
    }

    private void renderHostileList(GuiGraphics g, int mouseX, int mouseY) {
        int left = this.width / 2 - 150;
        List<HostileScan.Found> found = scanSurroundings();
        if (found.isEmpty()) {
            g.drawCenteredString(this.font, "附近没有名单里的敌对生物", this.width / 2, HOSTILE_LIST_TOP + 12, COLOR_MUTED);
            return;
        }
        g.drawString(this.font, "附近发现的敌对生物（点击一行可本次跳过）：", left, HOSTILE_LIST_TOP - 12, COLOR_MUTED, false);
        int shown = Math.min(found.size(), maxHostileRows());
        for (int i = 0; i < shown; i++) {
            HostileScan.Found f = found.get(i);
            int rowY = HOSTILE_LIST_TOP + i * HOSTILE_ROW_HEIGHT;
            boolean skipped = skippedThisRun.contains(f.typeId());
            boolean hovered = mouseX >= left && mouseX < left + 300 && mouseY >= rowY && mouseY < rowY + HOSTILE_ROW_HEIGHT;
            g.fill(left, rowY, left + 300, rowY + HOSTILE_ROW_HEIGHT - 1,
                    hovered ? 0xFF3A3A48 : (i % 2 == 0 ? 0x501A1A22 : 0x50151519));
            // checkbox, drawn from rectangles so it doesn't depend on the font having a tick glyph
            g.fill(left + 4, rowY + 3, left + 12, rowY + 11, 0xFF8A8A9A);
            g.fill(left + 5, rowY + 4, left + 11, rowY + 10, skipped ? 0xFF17171C : 0xFF55DD77);
            int textColor = skipped ? COLOR_MUTED : COLOR_TEXT;
            g.drawString(this.font, hostileName(f.typeId()), left + 18, rowY + 4, textColor, false);
            g.drawString(this.font, f.typeId(), left + 18 + this.font.width(hostileName(f.typeId())) + 8,
                    rowY + 4, 0xFF6A6A7A, false);
            String count = "×" + f.count();
            g.drawString(this.font, count, left + 300 - 6 - this.font.width(count), rowY + 4, textColor, false);
        }
        if (found.size() > shown) {
            g.drawString(this.font, "…还有 " + (found.size() - shown) + " 种未显示（窗口太矮）", left, HOSTILE_LIST_TOP + shown * HOSTILE_ROW_HEIGHT + 2, COLOR_MUTED, false);
        }
    }

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.HOSTILE) {
            return hostileRowClicked(mouseX, mouseY, button);
        }
        return grid != null && grid.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return grid != null && grid.mouseScrolled(mouseX, mouseY, delta);
    }

    private Preview hostilePreview() {
        int range = rangeOrDefault();
        if (HostileTypes.shared().ids().isEmpty()) {
            return Preview.problem("敌对名单是空的，点「编辑敌对名单」添加要清除的生物");
        }
        List<HostileScan.Found> found = scanSurroundings();
        if (found.isEmpty()) {
            return Preview.problem("范围 " + range + " 格内没有名单里的敌对生物，不会发送任何命令");
        }
        List<String> commands = HostileScan.commands(found, skippedThisRun, range);
        if (commands.isEmpty()) {
            return Preview.problem("发现的敌对生物都被取消勾选了");
        }
        // The client only knows about entities it has loaded, so a range past the player's render
        // distance can silently miss the far ones. Say so instead of implying a full sweep.
        int renderBlocks = Minecraft.getInstance().options.renderDistance().get() * 16;
        String warning = range > renderBlocks
                ? "范围 " + range + " 超过你的渲染距离约 " + renderBlocks + " 格，更远处没加载的敌对生物不会被发现"
                : null;
        return Preview.summary("将清除：" + describeFound(found) + "（共 " + commands.size() + " 条命令，只针对扫描到的）", warning);
    }

    @Override
    protected Preview preview() {
        if (mode == Mode.HOSTILE) {
            return hostilePreview();
        }
        String problem = switch (mode) {
            case CUSTOM -> CommandBuilders.selectorProblem(customBox.getValue());
            case RANGE_TYPE -> selectedEntity == null ? "请先在下方选择要清除的生物" : null;
            case RANGE_ALL, NEAREST, HOSTILE -> null;
        };
        return problem != null ? Preview.problem(problem) : Preview.ok(buildCommand());
    }

    private String buildCommand() {
        return switch (mode) {
            case CUSTOM -> {
                String raw = customBox.getValue().trim();
                yield CommandBuilders.selectorProblem(raw) != null ? null : CommandBuilders.killCustom(raw);
            }
            case RANGE_ALL -> CommandBuilders.killRangeAll(rangeOrDefault());
            case RANGE_TYPE -> selectedEntity == null ? null
                    : CommandBuilders.killRangeType(selectedEntity.idString(), rangeOrDefault());
            case NEAREST -> CommandBuilders.killNearest(
                    selectedEntity == null ? null : selectedEntity.idString(), rangeOrDefault());
            case HOSTILE -> null; // several commands, decided at click time: see runHostile()
        };
    }

    private int rangeOrDefault() {
        try {
            return Integer.parseInt(rangeBox.getValue());
        } catch (NumberFormatException e) {
            return mode == Mode.HOSTILE ? HostileTypes.shared().range() : 10;
        }
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (mode == Mode.HOSTILE) {
            renderHostileList(guiGraphics, mouseX, mouseY);
        }
        if (grid != null) {
            grid.render(guiGraphics, mouseX, mouseY);
            drawPaginationLabel(guiGraphics, grid, paginationY);
        }
        if (rangeBox != null) {
            guiGraphics.drawString(this.font, "范围", this.width / 2 + 44, rangeBox.getY() + 5, COLOR_MUTED, false);
        }
        if (grid != null && !isAnyDropdownOpen()) {
            grid.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }
}
