package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.blueprint.Blueprint;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPreview;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintScanner;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintStore;
import com.xiaofeiwu.cmdhelper.client.blueprint.PaletteBuilder;
import com.xiaofeiwu.cmdhelper.client.blueprint.Transform;
import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.SimpleListWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scan a building into a blueprint, and place a saved blueprint somewhere else.
 *
 * Left: the two corners of the building, a name, and the placement options. Right: the saved
 * blueprints, with what the selected one needs and what this game is missing of it. "预览放置" closes
 * the screen and shows the ghost where the crosshair points (see BlueprintPreview).
 */
public class BlueprintScreen extends CmdHelperScreen {

    // Kept between visits to the screen, so the player's choices aren't reset every time it opens.
    private static boolean clearAir = true;
    private static boolean verifyAfter = true;
    private static boolean belowFeet = true;

    private static final int LABEL_W = 28;
    private static final int BOX_W = 34;
    private static final int GROUP_W = 3 * BOX_W + 12;
    private static final long CONFIRM_WINDOW_MILLIS = 3000;

    private CoordinateFields from;
    private CoordinateFields to;
    private EditBox nameBox;
    private Checkbox feetCheckbox;
    private Checkbox airCheckbox;
    private Checkbox verifyCheckbox;
    private SimpleListWidget list;
    private Button deleteButton;

    private final Map<String, Blueprint> blueprints = new LinkedHashMap<>();
    private String selected;
    private String problem = "";
    private BlueprintScanner.Status lastScanStatus = BlueprintScanner.Status.IDLE;
    private long deleteConfirmUntil;

    // Details of the selected blueprint against this game (cached: computing it walks the palette).
    private String detailsFor;
    private PaletteBuilder.Prepared details;

    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int listTop;
    private int listBottom;

    public BlueprintScreen(Screen parent) {
        super(Component.literal("建筑蓝图"), parent);
        BlueprintScanner.acknowledge();
    }

    @Override
    public void removed() {
        storeOptions();
    }

    private void storeOptions() {
        if (feetCheckbox != null) {
            belowFeet = feetCheckbox.selected();
        }
        if (airCheckbox != null) {
            clearAir = airCheckbox.selected();
        }
        if (verifyCheckbox != null) {
            verifyAfter = verifyCheckbox.selected();
        }
    }

    @Override
    protected void init() {
        storeOptions();
        super.init();

        boolean twoColumns = this.width >= 500;
        leftW = 262;
        leftX = twoColumns ? 8 : this.width / 2 - leftW / 2;
        rightX = twoColumns ? leftX + leftW + 8 : leftX;
        rightW = twoColumns ? this.width - rightX - 8 : leftW;

        // ---- left: scan ----
        this.from = coordinateRow(40, "scanFrom", "起点");
        this.to = coordinateRow(62, "scanTo", "终点");

        this.feetCheckbox = tip(this.addRenderableWidget(new Checkbox(leftX + LABEL_W, 84,
                this.font.width("脚下 Y-1") + 24, 18, Component.literal("脚下 Y-1"), belowFeet)),
                "勾上：「用当前」和粘贴的坐标都取脚下那块方块（Y 减 1）。\n手动敲进格子的数字始终按字面使用。");

        this.nameBox = new EditBox(this.font, leftX, 106, 150, 18, Component.literal("蓝图名称"));
        this.nameBox.setHint(Component.literal("蓝图名称（可留空）"));
        this.nameBox.setMaxLength(40);
        remember("blueprintName", this.nameBox);
        tip(this.nameBox, "给这个蓝图起个名字，留空则按时间命名。\n重名时会自动加 (2)、(3)，不会覆盖旧的。");
        this.addRenderableWidget(this.nameBox);

        tip(this.addRenderableWidget(Button.builder(Component.literal("扫描并保存"), b -> scan())
                .bounds(leftX + 154, 106, 108, 18).build()),
                "读取起点到终点之间的所有方块，合并成最少的长方体，保存成蓝图。\n只能读取客户端已加载的区域，建筑要在渲染距离内；一次最多约 100 万格。\n箱子里的物品和实体不会保存。");

        this.airCheckbox = tip(this.addRenderableWidget(new Checkbox(leftX, 168,
                this.font.width("同时填空气（清除原有方块）") + 24, 18,
                Component.literal("同时填空气（清除原有方块）"), clearAir)),
                "勾上：蓝图里的空气也会填进去，把放置位置原有的方块清掉，和蓝图完全一样。\n"
                        + "不勾：只放方块，空气的位置保持原样（原有的地形不会被挖掉）。");
        this.verifyCheckbox = tip(this.addRenderableWidget(new Checkbox(leftX, 190,
                this.font.width("放置后校验并补漏") + 24, 18, Component.literal("放置后校验并补漏"), verifyAfter)),
                "放完后重新读一遍，找出和蓝图不一致的方块（掉落的门、落下的沙子、没放上的方块），\n只把这些补发一遍，最多两轮。\n目标区域要在渲染距离内才能校验。");

        // ---- right: saved blueprints ----
        int rows = Math.max(1, (this.height - 52 - 78) / SimpleListWidget.TWO_LINE_ROW_HEIGHT);
        listTop = twoColumns ? 52 : 232;
        if (!twoColumns) {
            rows = Math.max(1, (this.height - listTop - 78) / SimpleListWidget.TWO_LINE_ROW_HEIGHT);
        }
        this.list = new SimpleListWidget(rightX, listTop, rightW, rows, SimpleListWidget.TWO_LINE_ROW_HEIGHT, name -> {
            selected = name;
            deleteConfirmUntil = 0;
        });
        this.list.setPlainTitle(true);
        this.list.setEmptyMessage("还没有蓝图，先在左边扫描一个");
        listBottom = listTop + this.list.pixelHeight();
        reloadList(selected);

        int buttonsY = this.height - 26;
        tip(this.addRenderableWidget(Button.builder(Component.literal("预览放置"), b -> startPreview())
                .bounds(rightX, buttonsY, 100, 18).build()),
                "关闭界面，在世界里显示这个蓝图的半透明虚影（真实方块），跟着准星走。\n红色的是这个存档里没有的方块（会被跳过）。\n右键放置，左键取消；R 旋转，G 镜像，PageUp / PageDown 升降，\nV 在真实方块和彩色方框之间切换（虚影显示不对时用）。");
        this.deleteButton = tip(this.addRenderableWidget(Button.builder(Component.literal("删除"), b -> delete())
                .bounds(rightX + 104, buttonsY, 60, 18).build()),
                "删除选中的蓝图文件。3 秒内点两次才会执行。");
    }

    /** Label, three boxes, a paste box and 用当前, in the left column. */
    private CoordinateFields coordinateRow(int y, String key, String subject) {
        CoordinateFields fields = CoordinateFields.create(this.font, leftX + LABEL_W, y, BOX_W, 18);
        fields.withTooltips(subject);
        rememberCoords(key, fields);
        this.addRenderableWidget(fields.xBox);
        this.addRenderableWidget(fields.yBox);
        this.addRenderableWidget(fields.zBox);

        int pasteX = leftX + LABEL_W + GROUP_W + 4;
        EditBox paste = fields.createPasteBox(this.font, pasteX, y, 50, 18,
                () -> feetCheckbox != null && feetCheckbox.selected());
        tip(paste, "粘贴坐标\n把主菜单「复制坐标」复制的 x y z 粘贴到这里，会自动拆进左边三个格子。\n"
                + "勾选「脚下 Y-1」时，粘贴的 Y 会自动减 1（取脚下方块）。\n支持空格、中英文逗号、括号等写法。");
        this.addRenderableWidget(paste);

        tip(this.addRenderableWidget(Button.builder(Component.literal("用当前"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                fields.fillFrom(p.getX(), p.getY() - (feetCheckbox != null && feetCheckbox.selected() ? 1 : 0), p.getZ());
            }
        }).bounds(pasteX + 54, y, 46, 18).build()), "把你现在站的位置填进左边的坐标格子\n勾上「脚下 Y-1」时，Y 取脚下那块方块");
        return fields;
    }

    // ---- data ------------------------------------------------------------------------------------

    private void reloadList(String select) {
        blueprints.clear();
        for (Blueprint b : BlueprintStore.shared().list()) {
            blueprints.put(b.name(), b);
        }
        list.setDescriber(this::describe);
        list.setItems(new ArrayList<>(blueprints.keySet()));
        if (select != null && blueprints.containsKey(select)) {
            list.setSelected(select);
            selected = select;
        } else {
            selected = null;
        }
        detailsFor = null;
    }

    private String describe(String name) {
        Blueprint b = blueprints.get(name);
        return b == null ? null : b.sizeX() + "×" + b.sizeY() + "×" + b.sizeZ() + " · " + b.volume() + "格 · "
                + b.cuboids().size() + " 个方框";
    }

    private PaletteBuilder.Prepared detailsOfSelected() {
        if (selected == null || !blueprints.containsKey(selected)) {
            return null;
        }
        if (!selected.equals(detailsFor)) {
            details = PaletteBuilder.prepare(blueprints.get(selected), Transform.NONE);
            detailsFor = selected;
        }
        return details;
    }

    // ---- actions ---------------------------------------------------------------------------------

    private void scan() {
        if (!from.isComplete() || !to.isComplete()) {
            problem = "起点和终点的坐标还没填完整";
            return;
        }
        RegionBounds region = RegionBounds.of(from.coordString(), to.coordString());
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            name = "蓝图 " + new SimpleDateFormat("yyyy-MM-dd HH-mm").format(new Date());
        }
        String why = BlueprintScanner.start(name, region);
        problem = why == null ? "" : why;
    }

    private void startPreview() {
        Blueprint blueprint = selected == null ? null : blueprints.get(selected);
        if (blueprint == null) {
            problem = "先在右边选一个蓝图";
            return;
        }
        storeOptions();
        BlueprintPreview.start(blueprint, clearAir, verifyAfter);
        this.minecraft.setScreen(null);
    }

    /** Two clicks within a few seconds: a deleted blueprint file can't be brought back. */
    private void delete() {
        if (selected == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > deleteConfirmUntil) {
            deleteConfirmUntil = now + CONFIRM_WINDOW_MILLIS;
            return;
        }
        deleteConfirmUntil = 0;
        BlueprintStore.shared().delete(selected);
        reloadList(null);
    }

    // ---- input & drawing ---------------------------------------------------------------------------

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        return list != null && list.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return list != null && list.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.drawString(this.font, "起点", leftX, 45, COLOR_MUTED, false);
        g.drawString(this.font, "终点", leftX, 67, COLOR_MUTED, false);
        g.drawString(this.font, "放置选项", leftX, 154, COLOR_ACCENT, false);

        renderScanStatus(g);

        // how to place, with the player's actual key names
        String keys = "放置：选一个蓝图点「预览放置」，准星指向哪就放在哪。" + keyName(BlueprintPreview.ROTATE_KEY) + " 旋转，"
                + keyName(BlueprintPreview.MIRROR_KEY) + " 镜像，" + keyName(BlueprintPreview.UP_KEY) + " / "
                + keyName(BlueprintPreview.DOWN_KEY) + " 升降，" + keyName(BlueprintPreview.MODE_KEY)
                + " 切换真实方块 / 方框预览；右键放置，左键取消。";
        int y = 214;
        for (FormattedCharSequence line : this.font.split(Component.literal(keys), leftW)) {
            if (y + 9 > this.height - 8) {
                break;
            }
            g.drawString(this.font, line, leftX, y, COLOR_MUTED, false);
            y += 10;
        }

        g.drawString(this.font, "已保存的蓝图（点击选中）", rightX, listTop - 11, COLOR_ACCENT, false);
        list.render(g, mouseX, mouseY);
        String pageLabel = list.totalPages() > 1 ? "第 " + (list.currentPage() + 1) + "/" + list.totalPages() + " 页（滚轮翻页）" : "";
        if (!pageLabel.isEmpty()) {
            g.drawString(this.font, pageLabel, rightX + rightW - this.font.width(pageLabel), listTop - 11, COLOR_MUTED, false);
        }
        renderDetails(g);

        if (deleteButton != null) {
            deleteButton.setMessage(Component.literal(System.currentTimeMillis() < deleteConfirmUntil ? "再点确认" : "删除"));
        }
    }

    private static String keyName(net.minecraft.client.KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }

    private void renderScanStatus(GuiGraphics g) {
        BlueprintScanner.Status status = BlueprintScanner.status();
        if (status != lastScanStatus) {
            if (status == BlueprintScanner.Status.DONE) {
                reloadList(newestName());
            }
            lastScanStatus = status;
        }
        int y = 130;
        if (status == BlueprintScanner.Status.RUNNING) {
            g.drawString(this.font, "扫描中… " + (int) (BlueprintScanner.progress() * 100) + "%", leftX, y, COLOR_ACCENT, false);
        } else if (!problem.isEmpty()) {
            drawWrapped(g, problem, y, COLOR_ERROR);
        } else if (status == BlueprintScanner.Status.DONE) {
            drawWrapped(g, BlueprintScanner.message(), y, 0xFF7FE08F);
        } else if (status == BlueprintScanner.Status.FAILED) {
            drawWrapped(g, BlueprintScanner.message(), y, COLOR_ERROR);
        }
    }

    private void drawWrapped(GuiGraphics g, String text, int y, int color) {
        int lines = 0;
        for (FormattedCharSequence line : this.font.split(Component.literal(text), leftW)) {
            if (lines++ >= 2) {
                break;
            }
            g.drawString(this.font, line, leftX, y, color, false);
            y += 10;
        }
    }

    private String newestName() {
        return blueprints.isEmpty() ? null : BlueprintStore.shared().list().get(0).name();
    }

    /** What the selected blueprint is made of and whether this game has all of it. */
    private void renderDetails(GuiGraphics g) {
        PaletteBuilder.Prepared prepared = detailsOfSelected();
        if (prepared == null) {
            return;
        }
        Blueprint b = blueprints.get(selected);
        int y = listBottom + 4;
        g.drawString(this.font, ellipsize("用到的模组：" + String.join("、", b.namespaces()), rightW), rightX, y, COLOR_MUTED, false);
        y += 10;
        if (prepared.missingBlocks().isEmpty()) {
            g.drawString(this.font, "这个存档里所有方块都有", rightX, y, 0xFF7FE08F, false);
        } else {
            String mods = prepared.missingMods().isEmpty() ? "" : "（缺少模组：" + String.join("、", prepared.missingMods()) + "）";
            g.drawString(this.font, ellipsize("缺少 " + prepared.missingBlocks().size() + " 种方块" + mods + "，会被跳过", rightW),
                    rightX, y, COLOR_ERROR, false);
        }
        if (prepared.degradedStates() > 0) {
            g.drawString(this.font, ellipsize(prepared.degradedStates() + " 种方块的部分属性这个版本不认识，按默认值放", rightW),
                    rightX, y + 10, COLOR_WARNING, false);
        }
    }
}
