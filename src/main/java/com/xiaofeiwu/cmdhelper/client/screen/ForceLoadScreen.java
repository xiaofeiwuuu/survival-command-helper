package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.forceload.ForceLoadService;
import com.xiaofeiwu.cmdhelper.client.forceload.ForceLoadedChunks.Chunk;
import com.xiaofeiwu.cmdhelper.client.teleport.SafeTeleport;
import com.xiaofeiwu.cmdhelper.client.widget.ChunkListWidget;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/** /forceload — keeps chunks ticking even when no player is nearby.
 *
 *  The default view lists the chunks that are force-loaded right now (in the current dimension),
 *  each with buttons to teleport there or stop force-loading it. The other modes are for adding /
 *  removing by coordinates. Positions typed here are block columns (X/Z only, no Y), matching
 *  vanilla's "column_pos" argument; the list itself is in chunk coordinates, as the server reports it. */
public class ForceLoadScreen extends CmdHelperScreen {

    private enum Mode {LIST, ADD, REMOVE, REMOVE_ALL, QUERY_POS}

    private static final Map<Mode, String> MODE_LABELS = Map.of(
            Mode.LIST, "已强制加载的区块（列表）",
            Mode.ADD, "添加强制加载区块",
            Mode.REMOVE, "移除指定区块",
            Mode.REMOVE_ALL, "移除当前维度全部",
            Mode.QUERY_POS, "查询某个坐标是否强制加载"
    );

    private static final long CONFIRM_WINDOW_MILLIS = 3000;

    private final Mode mode;

    private EditBox fromXBox;
    private EditBox fromZBox;
    private EditBox toXBox;
    private EditBox toZBox;
    private int coordLabelY = -1;
    private int toLabelY = -1;

    private String resultText = "";
    private EditBox resultBox;

    // LIST mode
    private ChunkListWidget chunkList;
    private int paginationY;
    private boolean listRequested;
    private Button removeAllButton;
    private long removeAllConfirmUntil;

    public ForceLoadScreen(Screen parent) {
        this(parent, Mode.LIST);
    }

    private ForceLoadScreen(Screen parent, Mode mode) {
        super(Component.literal("强制加载区块 /forceload"), parent);
        this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                List.of(Mode.values()), mode, MODE_LABELS::get,
                value -> this.minecraft.setScreen(new ForceLoadScreen(parent, value)))));

        if (mode == Mode.LIST) {
            initListMode(centerX);
            return;
        }

        int y = 66;
        if (mode == Mode.ADD || mode == Mode.REMOVE) {
            coordLabelY = y;
            initColumnRow(centerX, y, true);
            y += 26;
            toLabelY = y;
            initColumnRow(centerX, y, false);
            y += 18;
        } else if (mode == Mode.QUERY_POS) {
            coordLabelY = y;
            initColumnRow(centerX, y, true);
            y += 18;
        }

        int buttonsY = y + 16;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> runCommand())
                .bounds(centerX + 10, buttonsY, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX + 105, buttonsY, 90, 18).build());

        int resultY = buttonsY + 26;
        this.resultBox = new EditBox(this.font, centerX - 150, resultY, 300, 18, Component.literal("结果"));
        tip(this.resultBox, "服务器返回的原文（只读）\n可点「复制结果」复制。");
        this.resultBox.setValue(resultText);
        this.resultBox.setEditable(false);
        this.addRenderableWidget(this.resultBox);

        this.addRenderableWidget(Button.builder(Component.literal("复制结果"), b -> {
            if (!resultText.isEmpty()) {
                CommandExecutor.copyRaw(resultText);
            }
        }).bounds(centerX - 150, resultY + 24, 90, 18).build());
    }

    // ---- LIST mode -----------------------------------------------------------------------------

    private void initListMode(int centerX) {
        // Ask once per screen instance, not on every init(): a window resize re-runs init() and
        // shouldn't fire another query at the server.
        if (!listRequested) {
            listRequested = true;
            ForceLoadService.refresh();
        }

        int left = centerX - 150;
        this.addRenderableWidget(Button.builder(Component.literal("刷新"), b -> ForceLoadService.refresh())
                .bounds(left, 64, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("加载脚下的区块"), b -> forceLoadHere())
                .bounds(left + 66, 64, 110, 18).build());
        this.removeAllButton = this.addRenderableWidget(Button.builder(Component.literal("取消全部"), b -> removeAll())
                .bounds(left + 182, 64, 118, 18).build());

        int listTop = 90;
        int rows = Math.max(1, ((this.height - 72) - listTop) / 22);
        this.chunkList = new ChunkListWidget(left, listTop, 300, rows, this::teleportTo, this::stopForceLoading);
        this.chunkList.setChunks(ForceLoadService.chunks());
        paginationY = listTop + chunkList.pixelHeight() + 6;
        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> chunkList.prevPage())
                .bounds(centerX - 60, paginationY, 20, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> chunkList.nextPage())
                .bounds(centerX + 40, paginationY, 20, 16).build());
    }

    private void teleportTo(Chunk chunk) {
        // Force-loaded chunks are always loaded on the server, so the surface teleport normally works
        // on the first try; SafeTeleport also handles the Nether (no ground to look up) and retries.
        SafeTeleport.toColumn(chunk.centerBlockX(), chunk.centerBlockZ());
        this.minecraft.setScreen(null);
    }

    private void stopForceLoading(Chunk chunk) {
        CommandExecutor.execute(CommandBuilders.forceLoadRemove(chunk.centerBlockX() + " " + chunk.centerBlockZ(), null));
        // Commands from one player are handled in order, so this query sees the removal.
        ForceLoadService.refresh();
    }

    private void forceLoadHere() {
        var player = this.minecraft.player;
        if (player == null) {
            return;
        }
        String column = (int) Math.floor(player.getX()) + " " + (int) Math.floor(player.getZ());
        CommandExecutor.execute(CommandBuilders.forceLoadAdd(column, null));
        ForceLoadService.refresh();
    }

    /** Two clicks within a few seconds: this can't be undone, and it sits right next to 刷新. */
    private void removeAll() {
        long now = System.currentTimeMillis();
        if (now > removeAllConfirmUntil) {
            removeAllConfirmUntil = now + CONFIRM_WINDOW_MILLIS;
            return;
        }
        removeAllConfirmUntil = 0;
        CommandExecutor.execute(CommandBuilders.forceLoadRemoveAll());
        ForceLoadService.refresh();
    }

    // ---- coordinate modes ----------------------------------------------------------------------

    private void initColumnRow(int centerX, int y, boolean from) {
        int x0 = centerX - 150 + 30;
        EditBox xBox = new EditBox(this.font, x0, y, 60, 18, Component.literal("X"));
        EditBox zBox = new EditBox(this.font, x0 + 66, y, 60, 18, Component.literal("Z"));
        xBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,6}"));
        zBox.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,6}"));
        String what = from ? (mode == Mode.QUERY_POS ? "要查询的位置" : "起点") : "终点（可选，留空则只处理起点所在的那一个区块）";
        tip(xBox, what + " X\n填方块坐标（不是区块坐标），会自动换算成它所在的区块。\n东西方向：东为 +");
        tip(zBox, what + " Z\n填方块坐标（不是区块坐标），会自动换算成它所在的区块。\n南北方向：南为 +");
        remember((from ? "from" : "to") + ".x", xBox);
        remember((from ? "from" : "to") + ".z", zBox);
        this.addRenderableWidget(xBox);
        this.addRenderableWidget(zBox);
        if (from) {
            this.fromXBox = xBox;
            this.fromZBox = zBox;
        } else {
            this.toXBox = xBox;
            this.toZBox = zBox;
        }

        this.addRenderableWidget(Button.builder(Component.literal("用当前位置"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                xBox.setValue(String.valueOf((int) Math.floor(p.getX())));
                zBox.setValue(String.valueOf((int) Math.floor(p.getZ())));
            }
        }).bounds(x0 + 140, y, 90, 18).tooltip(Tooltip.create(Component.literal("把你现在站的位置的 X、Z 填进左边的格子\n（小数会向下取整）"))).build());
    }

    private void runCommand() {
        String cmd = buildCommand();
        if (cmd == null) {
            return;
        }
        resultText = "等待结果...";
        resultBox.setValue(resultText);
        CommandExecutor.executeAwaitingResult(cmd, text -> {
            resultText = text;
            if (resultBox != null) {
                resultBox.setValue(text);
            }
        });
    }

    private String buildCommand() {
        return switch (mode) {
            case ADD -> {
                String from = columnString(fromXBox, fromZBox);
                yield from == null ? null : CommandBuilders.forceLoadAdd(from, columnString(toXBox, toZBox));
            }
            case REMOVE -> {
                String from = columnString(fromXBox, fromZBox);
                yield from == null ? null : CommandBuilders.forceLoadRemove(from, columnString(toXBox, toZBox));
            }
            case REMOVE_ALL -> CommandBuilders.forceLoadRemoveAll();
            case QUERY_POS -> {
                String pos = columnString(fromXBox, fromZBox);
                yield pos == null ? null : CommandBuilders.forceLoadQueryPos(pos);
            }
            case LIST -> null; // the list has its own per-row buttons
        };
    }

    private static String columnString(EditBox xBox, EditBox zBox) {
        if (xBox == null || zBox == null) {
            return null;
        }
        String x = xBox.getValue().trim();
        String z = zBox.getValue().trim();
        if (x.isEmpty() || x.equals("-") || z.isEmpty() || z.equals("-")) {
            return null;
        }
        return x + " " + z;
    }

    // ---- input & drawing -----------------------------------------------------------------------

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        return chunkList != null && chunkList.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return chunkList != null && chunkList.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        if (mode == Mode.LIST) {
            renderList(guiGraphics, mouseX, mouseY, centerX);
            return;
        }
        int labelX = centerX - 150;
        if (coordLabelY >= 0) {
            guiGraphics.drawString(this.font, mode == Mode.QUERY_POS ? "坐标" : "起点", labelX, coordLabelY + 5, COLOR_MUTED, false);
        }
        if (toLabelY >= 0) {
            guiGraphics.drawString(this.font, "终点（可选）", labelX, toLabelY + 5, COLOR_MUTED, false);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, int centerX) {
        // The list is fed live from the service, so a reply that arrives while the screen is open
        // shows up without any further action.
        chunkList.setChunks(ForceLoadService.chunks());
        var status = ForceLoadService.status();
        chunkList.setEmptyMessage(switch (status) {
            case READY -> "当前维度没有强制加载的区块";
            case LOADING, NEVER_ASKED -> "正在向服务器查询…";
            case TIMED_OUT -> "";
        });
        chunkList.render(g, mouseX, mouseY);

        String pageLabel = "第 " + (chunkList.currentPage() + 1) + " / " + chunkList.totalPages() + " 页";
        g.drawCenteredString(this.font, pageLabel, centerX, paginationY + 4, COLOR_MUTED);

        if (removeAllButton != null) {
            boolean confirming = System.currentTimeMillis() < removeAllConfirmUntil;
            removeAllButton.setMessage(Component.literal(confirming ? "再点一次确认清空" : "取消全部"));
        }

        switch (status) {
            case READY -> g.drawCenteredString(this.font,
                    "当前维度共 " + ForceLoadService.chunks().size() + " 个强制加载区块", centerX, this.height - 47, COLOR_MUTED);
            case LOADING, NEVER_ASKED -> g.drawCenteredString(this.font, "正在查询…", centerX, this.height - 47, COLOR_MUTED);
            case TIMED_OUT -> g.drawCenteredString(this.font,
                    "服务器没有回复：可能没有 OP 权限，或服务器没响应。点「刷新」重试", centerX, this.height - 47, COLOR_ERROR);
        }
        var level = this.minecraft.level;
        if (level != null && level.dimensionType().hasCeiling()) {
            g.drawCenteredString(this.font, "当前维度有基岩顶，「传送」只平移、保持你现在的高度",
                    centerX, this.height - 37, COLOR_MUTED);
        }
    }
}
