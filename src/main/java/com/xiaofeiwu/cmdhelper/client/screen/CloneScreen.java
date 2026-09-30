package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CloneCalc;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.CloneMode;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.MaskMode;
import com.xiaofeiwu.cmdhelper.client.command.CommandDescriber;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import com.xiaofeiwu.cmdhelper.client.history.CloneHistoryStore;
import com.xiaofeiwu.cmdhelper.client.preview.FillPreview;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryNames;
import com.xiaofeiwu.cmdhelper.client.widget.CloneHistoryList;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * /clone — copy a box of blocks somewhere else.
 *
 * Follows the checklist in "clone 注意点.md": the screen always works out and shows the source area
 * (both corners inclusive, size = difference + 1), the destination start, and the area the copy will
 * occupy, before any command exists. The maths lives in {@link CloneCalc}.
 */
public class CloneScreen extends CmdHelperScreen {

    private enum DestMode {MANUAL, DIRECTION}

    /** What the numbers in the 东南西北上下 boxes count. */
    private enum Unit {SOURCE_SIZE, BLOCKS}

    private static final Map<DestMode, String> DEST_LABELS = Map.of(
            DestMode.MANUAL, "目标：手动坐标",
            DestMode.DIRECTION, "目标：往某方向移动"
    );
    private static final Map<Unit, String> UNIT_LABELS = Map.of(
            Unit.SOURCE_SIZE, "单位：源尺寸的倍数",
            Unit.BLOCKS, "单位：格数"
    );
    private static final Map<MaskMode, String> MASK_LABELS = Map.of(
            MaskMode.REPLACE, "遮罩：全部替换",
            MaskMode.MASKED, "遮罩：只复制非空气"
    );
    private static final Map<CloneMode, String> MODE_LABELS = Map.of(
            CloneMode.AUTO, "模式：自动（重叠才加force）",
            CloneMode.NORMAL, "模式：普通（不可重叠）",
            CloneMode.FORCE, "模式：强制（可重叠）",
            CloneMode.MOVE, "模式：移动（清除源）"
    );

    // East is +X and south is +Z — Minecraft's own axes.
    private static final String[] DIRECTION_LABELS = {"东", "南", "西", "北", "上", "下"};
    private static final String[] DIRECTION_KEYS = {"dirEast", "dirSouth", "dirWest", "dirNorth", "dirUp", "dirDown"};
    private static final int EAST = 0, SOUTH = 1, WEST = 2, NORTH = 3, UP = 4, DOWN = 5;

    private static final int BOX_W = 34;
    private static final int GROUP_W = 3 * BOX_W + 12;
    private static final int LABEL_W = 40;
    private static final int DIRECTION_LABEL_W = 12;
    private static final int DIRECTION_BOX_W = 32;
    private static final int DIRECTION_STRIDE = 50;
    private static final long CONFIRM_WINDOW_MILLIS = 3000;

    private final DestMode destMode;
    private MaskMode maskMode = MaskMode.REPLACE;
    private CloneMode cloneMode = CloneMode.AUTO;
    // "用当前" fills the block the player stands ON (Y-1) rather than the one their feet are in.
    private boolean sourceFromBelowFeet = true;
    private boolean sourceToBelowFeet = true;
    private boolean destinationBelowFeet = true;
    // 1 east = one source-width east (right beside the source). Sharing the boundary layer makes each
    // step one block shorter, so neighbouring copies share the layer where they touch.
    private Unit unit = Unit.SOURCE_SIZE;
    private boolean shareBoundaryLayer = false;

    private CoordinateFields sourceFrom;
    private CoordinateFields sourceTo;
    private CoordinateFields destination;                 // MANUAL
    private final EditBox[] directionBoxes = new EditBox[6]; // DIRECTION: 东 南 西 北 上 下
    private Checkbox sourceFromCheckbox;
    private Checkbox sourceToCheckbox;
    private Checkbox destinationCheckbox;
    private Checkbox shareCheckbox;

    private Button executeButton;
    private Button clearButton;
    private long moveConfirmUntil;
    private long clearConfirmUntil;

    // Right-hand column of past clones. Only shown when the window is wide enough to fit it beside the form.
    private static final int HISTORY_WIDTH = 140;
    private CloneHistoryList history;
    private int historyLeft;
    private int historyPageY;

    // A short message ("no source", "too big to clear at once") shown for a couple of seconds.
    private String flashText = "";
    private long flashUntil;

    public CloneScreen(Screen parent) {
        this(parent, DestMode.MANUAL);
    }

    private CloneScreen(Screen parent, DestMode destMode) {
        super(Component.literal("复制区域 /clone"), parent);
        this.destMode = destMode;
    }

    private boolean hasHistoryColumn() {
        return this.width >= 300 + HISTORY_WIDTH + 24;
    }

    /** Centre of the form: shifted left to make room for the history column when there is one. */
    private int contentCenter() {
        return hasHistoryColumn() ? (this.width - HISTORY_WIDTH - 8) / 2 : this.width / 2;
    }

    @Override
    protected void init() {
        // The checkboxes are about to be rebuilt (window resize): keep what was ticked.
        rememberCheckboxes();
        super.init();
        int centerX = contentCenter();
        int left = centerX - 150;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(left, 40, 148, 18,
                List.of(MaskMode.values()), maskMode, MASK_LABELS::get, v -> this.maskMode = v)));
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 2, 40, 148, 18,
                List.of(CloneMode.values()), cloneMode, MODE_LABELS::get, v -> this.cloneMode = v)));

        this.sourceFrom = coordinateRow(left, 66, "sourceFrom", sourceFromBelowFeet, cb -> sourceFromCheckbox = cb);
        this.sourceTo = coordinateRow(left, 88, "sourceTo", sourceToBelowFeet, cb -> sourceToCheckbox = cb);

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(left, 112, 150, 18,
                List.of(DestMode.values()), destMode, DEST_LABELS::get, this::switchDestMode)));

        if (destMode == DestMode.MANUAL) {
            this.destination = coordinateRow(left, 134, "destination", destinationBelowFeet, cb -> destinationCheckbox = cb);
        } else {
            this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(left + 156, 112, 144, 18,
                    List.of(Unit.values()), unit, UNIT_LABELS::get, this::switchUnit)));
            directionRow(left, 134);
            if (unit == Unit.SOURCE_SIZE) {
                String label = "共用一层边界";
                this.shareCheckbox = this.addRenderableWidget(new Checkbox(left, 156,
                        this.font.width(label) + 24, 18, Component.literal(label), shareBoundaryLayer));
            }
        }

        int bottom = this.height - 26;
        // Four buttons across the 300-wide form: 预览 / 执行 / 复制命令 / 清除粘贴区域.
        this.addRenderableWidget(Button.builder(Component.literal("半透明预览"), b -> startInWorldPreview())
                .bounds(left, bottom, 72, 18).build());
        this.executeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"),
                b -> execute()).bounds(left + 76, bottom, 72, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            Result r = compute();
            if (r.problem() == null) {
                CommandExecutor.copyToClipboard(r.command());
            }
        }).bounds(left + 152, bottom, 72, 18).build());
        this.clearButton = this.addRenderableWidget(Button.builder(Component.literal("清除粘贴区"), b -> clearPasted())
                .bounds(left + 228, bottom, 72, 18).build());

        if (hasHistoryColumn()) {
            initHistoryColumn();
        } else {
            history = null;
        }
    }

    private void initHistoryColumn() {
        historyLeft = this.width - HISTORY_WIDTH - 8;
        int top = 50;
        int rows = Math.max(1, (this.height - 8 - 22 - top) / CloneHistoryList.ROW_HEIGHT);
        history = new CloneHistoryList(historyLeft, top, HISTORY_WIDTH, rows, this::loadFromHistory,
                command -> CommandDescriber.describe(command, RegistryNames.INSTANCE));
        history.setCommands(CloneHistoryStore.shared().commands());
        historyPageY = this.height - 24;
        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> history.prevPage())
                .bounds(historyLeft + HISTORY_WIDTH / 2 - 44, historyPageY, 20, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> history.nextPage())
                .bounds(historyLeft + HISTORY_WIDTH / 2 + 24, historyPageY, 20, 16).build());
    }

    /**
     * Puts a past clone back into every field: the source corners, the destination start (as plain
     * coordinates — that's exactly what was sent), and the two mode dropdowns. Done by building a
     * fresh screen whose remembered inputs are these values.
     */
    private void loadFromHistory(CloneCalc.Parsed p) {
        rememberCheckboxes();
        CloneScreen screen = new CloneScreen(parent, DestMode.MANUAL);
        screen.maskMode = p.mask();
        screen.cloneMode = p.mode();
        screen.sourceFromBelowFeet = this.sourceFromBelowFeet;
        screen.sourceToBelowFeet = this.sourceToBelowFeet;
        screen.destinationBelowFeet = this.destinationBelowFeet;
        screen.unit = this.unit;
        screen.shareBoundaryLayer = this.shareBoundaryLayer;
        RegionBounds s = p.source();
        screen.saveState("sourceFrom.x", String.valueOf(s.minX()));
        screen.saveState("sourceFrom.y", String.valueOf(s.minY()));
        screen.saveState("sourceFrom.z", String.valueOf(s.minZ()));
        screen.saveState("sourceTo.x", String.valueOf(s.maxX()));
        screen.saveState("sourceTo.y", String.valueOf(s.maxY()));
        screen.saveState("sourceTo.z", String.valueOf(s.maxZ()));
        screen.saveState("destination.x", String.valueOf(p.destX()));
        screen.saveState("destination.y", String.valueOf(p.destY()));
        screen.saveState("destination.z", String.valueOf(p.destZ()));
        this.minecraft.setScreen(screen);
    }

    private void rememberCheckboxes() {
        if (sourceFromCheckbox != null) {
            sourceFromBelowFeet = sourceFromCheckbox.selected();
        }
        if (sourceToCheckbox != null) {
            sourceToBelowFeet = sourceToCheckbox.selected();
        }
        if (destinationCheckbox != null) {
            destinationBelowFeet = destinationCheckbox.selected();
        }
        if (shareCheckbox != null) {
            shareBoundaryLayer = shareCheckbox.selected();
        }
    }

    private void switchUnit(Unit next) {
        rememberCheckboxes();
        this.unit = next;
        // The boundary checkbox only exists for the "multiples of the source" unit, so rebuild.
        this.rebuildWidgets();
    }

    private void switchDestMode(DestMode next) {
        rememberCheckboxes();
        CloneScreen screen = new CloneScreen(parent, next);
        screen.maskMode = this.maskMode;
        screen.cloneMode = this.cloneMode;
        screen.sourceFromBelowFeet = this.sourceFromBelowFeet;
        screen.sourceToBelowFeet = this.sourceToBelowFeet;
        screen.destinationBelowFeet = this.destinationBelowFeet;
        screen.unit = this.unit;
        screen.shareBoundaryLayer = this.shareBoundaryLayer;
        passInputsTo(screen);
        this.minecraft.setScreen(screen);
    }

    /**
     * Three X/Y/Z boxes, a 用当前 button, and a "脚下 Y-1" tick: when ticked, 用当前 fills the block the
     * player is standing on (feet Y minus 1) — the floor — instead of the air block at their feet.
     * Positions are floored, never truncated.
     */
    private CoordinateFields coordinateRow(int left, int y, String key, boolean belowFeet,
                                           java.util.function.Consumer<Checkbox> checkboxOut) {
        CoordinateFields fields = CoordinateFields.create(this.font, left + LABEL_W, y, BOX_W, 18);
        rememberCoords(key, fields);
        this.addRenderableWidget(fields.xBox);
        this.addRenderableWidget(fields.yBox);
        this.addRenderableWidget(fields.zBox);

        String label = "脚下 Y-1";
        int checkboxX = left + LABEL_W + GROUP_W + 4 + 46 + 4;
        Checkbox checkbox = this.addRenderableWidget(
                new Checkbox(checkboxX, y, this.font.width(label) + 24, 18, Component.literal(label), belowFeet));
        checkboxOut.accept(checkbox);

        this.addRenderableWidget(Button.builder(Component.literal("用当前"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                fields.fillFrom(p.getX(), p.getY() - (checkbox.selected() ? 1 : 0), p.getZ());
            }
        }).bounds(left + LABEL_W + GROUP_W + 4, y, 46, 18).build());
        return fields;
    }

    /** 东 南 西 北 上 下, each "how many blocks to move that way"; opposite directions cancel out. */
    private void directionRow(int left, int y) {
        for (int i = 0; i < 6; i++) {
            int labelX = left + i * DIRECTION_STRIDE;
            EditBox box = new EditBox(this.font, labelX + DIRECTION_LABEL_W, y, DIRECTION_BOX_W, 18,
                    Component.literal(DIRECTION_LABELS[i]));
            box.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
            remember(DIRECTION_KEYS[i], box);
            this.addRenderableWidget(box);
            directionBoxes[i] = box;
        }
    }

    private static int amount(EditBox box) {
        try {
            return box.getValue().isEmpty() ? 0 : Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- what the current inputs add up to ------------------------------------------------------

    private record Result(CloneCalc.Plan plan, String command, List<String> warnings, String problem) {
        static Result problem(String why) {
            return new Result(null, null, List.of(), why);
        }
    }

    private static int[] ints(CoordinateFields fields) {
        String[] parts = fields.coordString().split(" ");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private Result compute() {
        if (!sourceFrom.isComplete() || !sourceTo.isComplete()) {
            return Result.problem("源起点和源终点的坐标还没填完整");
        }
        RegionBounds source = RegionBounds.of(sourceFrom.coordString(), sourceTo.coordString());
        CloneCalc.Plan plan;
        if (destMode == DestMode.MANUAL) {
            if (!destination.isComplete()) {
                return Result.problem("目标起点的坐标还没填完整");
            }
            int[] d = ints(destination);
            plan = CloneCalc.plan(source, d[0], d[1], d[2]);
        } else {
            int east = amount(directionBoxes[EAST]);
            int south = amount(directionBoxes[SOUTH]);
            int west = amount(directionBoxes[WEST]);
            int north = amount(directionBoxes[NORTH]);
            int up = amount(directionBoxes[UP]);
            int down = amount(directionBoxes[DOWN]);
            int[] o = unit == Unit.SOURCE_SIZE
                    ? CloneCalc.directionalOffsetInSourceSizes(source, east, south, west, north, up, down, shareBoundaryLayer)
                    : CloneCalc.directionalOffset(east, south, west, north, up, down);
            if (o[0] == 0 && o[1] == 0 && o[2] == 0) {
                return Result.problem(unit == Unit.SOURCE_SIZE
                        ? "还没填往哪个方向移动几个源尺寸（东南西北上下，填 1 = 紧挨着源区域）"
                        : "还没填往哪个方向移动多少格（东南西北上下）");
            }
            plan = CloneCalc.planWithOffset(source, o[0], o[1], o[2]);
        }
        return new Result(plan, CloneCalc.command(plan, maskMode, cloneMode),
                CloneCalc.warnings(plan, maskMode, cloneMode), null);
    }

    @Override
    protected Preview preview() {
        // A message from a button (e.g. "nothing to clear") takes over the preview line for a moment.
        if (System.currentTimeMillis() < flashUntil) {
            return Preview.problem(flashText);
        }
        Result r = compute();
        if (r.problem() != null) {
            return Preview.problem(r.problem());
        }
        return r.warnings().isEmpty() ? Preview.ok(r.command())
                : Preview.warn(r.command(), String.join("；", r.warnings()));
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void execute() {
        Result r = compute();
        if (r.problem() != null) {
            return;
        }
        // "move" deletes the source: ask twice, like the other things that can't be undone.
        if (cloneMode == CloneMode.MOVE) {
            long now = System.currentTimeMillis();
            if (now > moveConfirmUntil) {
                moveConfirmUntil = now + CONFIRM_WINDOW_MILLIS;
                return;
            }
            moveConfirmUntil = 0;
        }
        CommandExecutor.execute(r.command());
    }

    private void flash(String text) {
        flashText = text;
        flashUntil = System.currentTimeMillis() + 3500;
    }

    /**
     * Wipes what the current fields would paste (or did paste): the destination area, minus the source
     * area, filled with air. Asks twice — it can't be undone, and the blocks that were there before the
     * paste are not brought back, only cleared. Uses whatever the fields say now, so load the clone from
     * the history first to clear an older one.
     */
    private void clearPasted() {
        Result r = compute();
        if (r.problem() != null) {
            flash("先填好源区域和目标，才知道要清除哪一块");
            return;
        }
        List<RegionBounds> boxes = CloneCalc.clearBoxes(r.plan());
        if (boxes.isEmpty()) {
            flash("目标区域完全在源区域里面，没有可清除的部分");
            return;
        }
        for (RegionBounds box : boxes) {
            if (box.exceedsFillLimit()) {
                flash("要清除的一块有 " + box.volume() + " 格，超过 " + RegionBounds.FILL_BLOCK_LIMIT + "，一条命令清不掉");
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (now > clearConfirmUntil) {
            clearConfirmUntil = now + CONFIRM_WINDOW_MILLIS;
            return;
        }
        clearConfirmUntil = 0;
        List<String> commands = CloneCalc.clearCommands(r.plan());
        if (commands.size() == 1) {
            CommandExecutor.execute(commands.get(0));
        } else {
            CommandExecutor.executeBatch(commands, "清除粘贴区域（" + commands.size() + " 条 fill，保留与源重叠的部分）");
        }
    }

    private void startInWorldPreview() {
        Result r = compute();
        if (r.problem() != null) {
            return;
        }
        RegionBounds s = r.plan().source();
        RegionBounds d = r.plan().destination();
        FillPreview.Plan plan = new FillPreview.Plan(
                s.minX() + " " + s.minY() + " " + s.minZ(), s.maxX() + " " + s.maxY() + " " + s.maxZ(), r.command(),
                d.minX() + " " + d.minY() + " " + d.minZ(), d.maxX() + " " + d.maxY() + " " + d.maxZ(), "复制预览");
        FillPreview.start(() -> plan);
        this.minecraft.setScreen(null);
    }

    // ---- drawing ---------------------------------------------------------------------------------

    private static String range(int min, int max) {
        return min + "~" + max;
    }

    /** Which way the player is facing, with the axis it points along, e.g. "北（Z-）". */
    private static String facingLabel(net.minecraft.core.Direction direction) {
        return switch (direction) {
            case NORTH -> "北（Z-）";
            case SOUTH -> "南（Z+）";
            case EAST -> "东（X+）";
            case WEST -> "西（X-）";
            default -> "-";
        };
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = contentCenter() - 150;
        // In the title bar, right-aligned: the directions below are compass directions, so say which
        // one the player is looking along.
        var player = this.minecraft.player;
        if (player != null) {
            String facing = "当前朝向：" + facingLabel(player.getDirection());
            g.drawString(this.font, facing, this.width - 8 - this.font.width(facing), 11, COLOR_ACCENT, false);
        }
        g.drawString(this.font, "源起点", left, 71, COLOR_MUTED, false);
        g.drawString(this.font, "源终点", left, 93, COLOR_MUTED, false);
        if (destMode == DestMode.MANUAL) {
            g.drawString(this.font, "目标起点", left, 139, COLOR_MUTED, false);
            g.drawString(this.font, "目标点=复制后区域的最小角", left + 156, 117, COLOR_MUTED, false);
        } else {
            for (int i = 0; i < 6; i++) {
                g.drawString(this.font, DIRECTION_LABELS[i], left + i * DIRECTION_STRIDE, 139, COLOR_MUTED, false);
            }
            int hintX = unit == Unit.SOURCE_SIZE ? left + 124 : left;
            g.drawString(this.font, "东=X+  南=Z+  西=X-  北=Z-", hintX, 161, COLOR_MUTED, false);
        }

        long now = System.currentTimeMillis();
        if (executeButton != null) {
            boolean confirming = cloneMode == CloneMode.MOVE && now < moveConfirmUntil;
            executeButton.setMessage(Component.literal(confirming ? "再点确认" : "执行"));
        }
        if (clearButton != null) {
            clearButton.setMessage(Component.literal(now < clearConfirmUntil ? "再点确认" : "清除粘贴区"));
        }
        renderChecklist(g, left, destMode == DestMode.MANUAL ? 160 : 182);
        renderHistoryColumn(g, mouseX, mouseY);
    }

    private void renderHistoryColumn(GuiGraphics g, int mouseX, int mouseY) {
        if (history == null) {
            return;
        }
        history.setCommands(CloneHistoryStore.shared().commands()); // picks up a clone that was just executed
        g.vLine(historyLeft - 6, HEADER_HEIGHT + 2, this.height, COLOR_BORDER);
        g.drawString(this.font, "复制历史（点击填入）", historyLeft, 38, COLOR_ACCENT, false);
        history.render(g, mouseX, mouseY);
        g.drawCenteredString(this.font, (history.currentPage() + 1) + "/" + history.totalPages(),
                historyLeft + HISTORY_WIDTH / 2, historyPageY + 4, COLOR_MUTED);
        if (!isAnyDropdownOpen()) {
            history.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        return history != null && history.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return history != null && history.mouseScrolled(mouseX, mouseY, delta);
    }

    /** The note's pre-flight checklist ①–④, live. */
    private void renderChecklist(GuiGraphics g, int left, int y) {
        if (!sourceFrom.isComplete() || !sourceTo.isComplete()) {
            return;
        }
        RegionBounds s = RegionBounds.of(sourceFrom.coordString(), sourceTo.coordString());
        g.drawString(this.font, ellipsize("源区域  X " + range(s.minX(), s.maxX()) + "  Y " + range(s.minY(), s.maxY())
                + "  Z " + range(s.minZ(), s.maxZ()), 300), left, y, COLOR_TEXT, false);
        // Lengths are inclusive: |end - start| + 1. In "multiples of the source" mode these are the
        // units the direction boxes count in, so they're spelled out.
        g.drawString(this.font, ellipsize("长度  X " + s.sizeX() + "   Y " + s.sizeY() + "   Z " + s.sizeZ()
                + "   （共 " + s.volume() + " 格，差值+1）", 300), left, y + 10, COLOR_ACCENT, false);

        Result r = compute();
        if (r.problem() != null) {
            return;
        }
        RegionBounds d = r.plan().destination();
        g.drawString(this.font, ellipsize("目标起点 (" + d.minX() + ", " + d.minY() + ", " + d.minZ() + ")", 300),
                left, y + 20, COLOR_MUTED, false);
        boolean overlaps = r.plan().overlaps();
        g.drawString(this.font, ellipsize("复制后  X " + range(d.minX(), d.maxX()) + "  Y " + range(d.minY(), d.maxY())
                + "  Z " + range(d.minZ(), d.maxZ()) + (overlaps ? "  （与源重叠）" : ""), 300),
                left, y + 30, overlaps ? COLOR_WARNING : COLOR_MUTED, false);
    }
}
