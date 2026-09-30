package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CloneCalc;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.CloneMode;
import com.xiaofeiwu.cmdhelper.client.command.CloneCalc.MaskMode;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import com.xiaofeiwu.cmdhelper.client.preview.FillPreview;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private enum DestMode {MANUAL, OFFSET}

    private static final Map<DestMode, String> DEST_LABELS = Map.of(
            DestMode.MANUAL, "目标：手动坐标",
            DestMode.OFFSET, "目标：相对源偏移"
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

    private static final int BOX_W = 34;
    private static final int GROUP_W = 3 * BOX_W + 12;
    private static final int LABEL_W = 40;
    private static final long CONFIRM_WINDOW_MILLIS = 3000;

    private final DestMode destMode;
    private MaskMode maskMode = MaskMode.REPLACE;
    private CloneMode cloneMode = CloneMode.AUTO;

    private CoordinateFields sourceFrom;
    private CoordinateFields sourceTo;
    private CoordinateFields destination;   // MANUAL
    private CoordinateFields offset;        // OFFSET

    private Button executeButton;
    private long moveConfirmUntil;

    public CloneScreen(Screen parent) {
        this(parent, DestMode.MANUAL);
    }

    private CloneScreen(Screen parent, DestMode destMode) {
        super(Component.literal("复制区域 /clone"), parent);
        this.destMode = destMode;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int left = centerX - 150;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(left, 40, 148, 18,
                List.of(MaskMode.values()), maskMode, MASK_LABELS::get, v -> this.maskMode = v)));
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX + 2, 40, 148, 18,
                List.of(CloneMode.values()), cloneMode, MODE_LABELS::get, v -> this.cloneMode = v)));

        this.sourceFrom = coordinateRow(left, 66, "sourceFrom");
        this.sourceTo = coordinateRow(left, 88, "sourceTo");

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(left, 112, 150, 18,
                List.of(DestMode.values()), destMode, DEST_LABELS::get, this::switchDestMode)));

        if (destMode == DestMode.MANUAL) {
            this.destination = coordinateRow(left, 134, "destination");
        } else {
            this.offset = offsetRow(left, 134);
        }

        int bottom = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("半透明预览"), b -> startInWorldPreview())
                .bounds(centerX - 85, bottom, 90, 18).build());
        this.executeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"),
                b -> execute()).bounds(centerX + 10, bottom, 90, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            Result r = compute();
            if (r.problem() == null) {
                CommandExecutor.copyToClipboard(r.command());
            }
        }).bounds(centerX + 105, bottom, 90, 18).build());
    }

    private void switchDestMode(DestMode next) {
        CloneScreen screen = new CloneScreen(parent, next);
        screen.maskMode = this.maskMode;
        screen.cloneMode = this.cloneMode;
        passInputsTo(screen);
        this.minecraft.setScreen(screen);
    }

    /** Three X/Y/Z boxes plus a 用当前 button (the player's block, floored — never truncated). */
    private CoordinateFields coordinateRow(int left, int y, String key) {
        CoordinateFields fields = CoordinateFields.create(this.font, left + LABEL_W, y, BOX_W, 18);
        rememberCoords(key, fields);
        this.addRenderableWidget(fields.xBox);
        this.addRenderableWidget(fields.yBox);
        this.addRenderableWidget(fields.zBox);
        this.addRenderableWidget(Button.builder(Component.literal("用当前"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                fields.fillFrom(p.getX(), p.getY(), p.getZ());
            }
        }).bounds(left + LABEL_W + GROUP_W + 4, y, 46, 18).build());
        return fields;
    }

    private CoordinateFields offsetRow(int left, int y) {
        CoordinateFields fields = CoordinateFields.create(this.font, left + LABEL_W, y, BOX_W, 18);
        rememberCoords("offset", fields);
        this.addRenderableWidget(fields.xBox);
        this.addRenderableWidget(fields.yBox);
        this.addRenderableWidget(fields.zBox);
        int buttonsX = left + LABEL_W + GROUP_W + 4;
        // Stacking one copy on another: the offset is the source height, or one less to share a layer
        // (the note's example copies 73~78 to 78~83, so floor 78 belongs to both).
        this.addRenderableWidget(Button.builder(Component.literal("上叠"), b -> stackUp(fields, 0))
                .bounds(buttonsX, y, 40, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("上叠共用底层"), b -> stackUp(fields, 1))
                .bounds(buttonsX + 44, y, 80, 18).build());
        return fields;
    }

    private void stackUp(CoordinateFields fields, int sharedLayers) {
        if (!sourceFrom.isComplete() || !sourceTo.isComplete()) {
            return;
        }
        RegionBounds source = RegionBounds.of(sourceFrom.coordString(), sourceTo.coordString());
        fields.xBox.setValue("0");
        fields.yBox.setValue(String.valueOf(source.sizeY() - sharedLayers));
        fields.zBox.setValue("0");
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
            if (!offset.isComplete()) {
                return Result.problem("偏移量还没填完整");
            }
            int[] o = ints(offset);
            plan = CloneCalc.planWithOffset(source, o[0], o[1], o[2]);
        }
        return new Result(plan, CloneCalc.command(plan, maskMode, cloneMode),
                CloneCalc.warnings(plan, maskMode, cloneMode), null);
    }

    @Override
    protected Preview preview() {
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

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = this.width / 2 - 150;
        g.drawString(this.font, "源起点", left, 71, COLOR_MUTED, false);
        g.drawString(this.font, "源终点", left, 93, COLOR_MUTED, false);
        if (destMode == DestMode.MANUAL) {
            g.drawString(this.font, "目标起点", left, 139, COLOR_MUTED, false);
        } else {
            g.drawString(this.font, "偏移", left, 139, COLOR_MUTED, false);
        }
        g.drawString(this.font, "目标点=复制后区域的最小角", left + 156, 117, COLOR_MUTED, false);

        if (executeButton != null) {
            boolean confirming = cloneMode == CloneMode.MOVE && System.currentTimeMillis() < moveConfirmUntil;
            executeButton.setMessage(Component.literal(confirming ? "再点确认移动" : "执行"));
        }
        renderChecklist(g, left);
    }

    /** The note's pre-flight checklist ①–④, live. */
    private void renderChecklist(GuiGraphics g, int left) {
        if (!sourceFrom.isComplete() || !sourceTo.isComplete()) {
            return;
        }
        RegionBounds s = RegionBounds.of(sourceFrom.coordString(), sourceTo.coordString());
        int y = 160;
        g.drawString(this.font, ellipsize("源区域  X " + range(s.minX(), s.maxX()) + " (" + s.sizeX() + ")  Y "
                + range(s.minY(), s.maxY()) + " (" + s.sizeY() + ")  Z " + range(s.minZ(), s.maxZ()) + " ("
                + s.sizeZ() + ")", 300), left, y, COLOR_TEXT, false);

        Result r = compute();
        if (r.problem() != null) {
            g.drawString(this.font, "共 " + s.volume() + " 格", left, y + 10, COLOR_MUTED, false);
            return;
        }
        RegionBounds d = r.plan().destination();
        g.drawString(this.font, ellipsize("共 " + s.volume() + " 格    目标起点 (" + d.minX() + ", " + d.minY() + ", "
                + d.minZ() + ")", 300), left, y + 10, COLOR_MUTED, false);
        boolean overlaps = r.plan().overlaps();
        g.drawString(this.font, ellipsize("复制后  X " + range(d.minX(), d.maxX()) + "  Y " + range(d.minY(), d.maxY())
                + "  Z " + range(d.minZ(), d.maxZ()) + (overlaps ? "  （与源重叠）" : ""), 300),
                left, y + 20, overlaps ? COLOR_WARNING : COLOR_MUTED, false);
    }
}
