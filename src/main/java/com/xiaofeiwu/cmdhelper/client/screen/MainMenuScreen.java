package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.hostile.HostileClear;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;

public class MainMenuScreen extends CmdHelperScreen {

    private static final int COLUMNS = 3;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP_X = 6;
    private static final int GAP_Y = 6;

    // Vertical layout, top to bottom: three info lines, the copy-coords button, the button grid.
    private static final int COPY_BUTTON_OFFSET = 42;
    private static final int GRID_OFFSET = COPY_BUTTON_OFFSET + 18 + 8;

    private record Entry(String langKey, Runnable action) {
    }

    // Rows follow from how many buttons there are, so adding a feature is one line in entries().
    private int gridRows = 1;

    public MainMenuScreen() {
        super(Component.translatable("screen.cmdhelper.main.title"), null);
    }

    @Override
    protected boolean showHeader() {
        return false;
    }

    /** The whole block is centred vertically, since there's no title bar to hang it from. */
    private int contentTop() {
        int contentHeight = GRID_OFFSET + gridRows * (BUTTON_HEIGHT + GAP_Y) - GAP_Y;
        return Math.max(10, (this.height - contentHeight) / 2);
    }

    /** Every button in the grid, row by row, left to right. New features go just above the two history buttons. */
    private List<Entry> entries() {
        return List.of(
                new Entry("gui.cmdhelper.op.give", () -> minecraft.setScreen(new GiveScreen(this))),
                new Entry("gui.cmdhelper.op.kill", () -> minecraft.setScreen(new KillScreen(this))),
                // One click, no screen: scans around the player and kills the hostile mobs that are
                // actually there. What counts as hostile and how far to look is set inside "杀死生物".
                new Entry("gui.cmdhelper.op.kill_hostile", () -> {
                    HostileClear.clearNow();
                    minecraft.setScreen(null);
                }),

                new Entry("gui.cmdhelper.op.tp", () -> minecraft.setScreen(new TpScreen(this))),
                new Entry("gui.cmdhelper.op.fill", () -> minecraft.setScreen(new FillScreen(this))),
                new Entry("gui.cmdhelper.op.setblock", () -> minecraft.setScreen(new SetBlockScreen(this))),

                new Entry("gui.cmdhelper.op.summon", () -> minecraft.setScreen(new SummonScreen(this))),
                new Entry("gui.cmdhelper.op.environment", () -> minecraft.setScreen(new EnvironmentScreen(this))),
                new Entry("gui.cmdhelper.op.gamemode", () -> minecraft.setScreen(new GameModeScreen(this))),

                new Entry("gui.cmdhelper.op.locate", () -> minecraft.setScreen(new LocateScreen(this))),
                new Entry("gui.cmdhelper.op.forceload", () -> minecraft.setScreen(new ForceLoadScreen(this))),
                new Entry("gui.cmdhelper.op.clone", () -> minecraft.setScreen(new CloneScreen(this))),

                // The two "look back" buttons stay last: put new features above this line.
                new Entry("gui.cmdhelper.op.favorites", () -> minecraft.setScreen(HistoryScreen.favorites(this))),
                new Entry("gui.cmdhelper.op.history", () -> minecraft.setScreen(new HistoryScreen(this)))
        );
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        List<Entry> entries = entries();
        gridRows = (entries.size() + COLUMNS - 1) / COLUMNS;
        int top = contentTop();

        // Squeeze the buttons on a very narrow window instead of letting the grid run off-screen.
        int btnW = Math.min(BUTTON_WIDTH, (this.width - 16 - (COLUMNS - 1) * GAP_X) / COLUMNS);
        int gridWidth = COLUMNS * btnW + (COLUMNS - 1) * GAP_X;
        int gridLeft = centerX - gridWidth / 2;
        int gridTop = top + GRID_OFFSET;

        int coordBtnW = 100;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy_coords"), b -> {
            var p = this.minecraft.player;
            if (p != null) {
                // floor, not (int): a cast truncates toward zero, so x = -10.3 would copy as -10
                // although the block the player is standing in is -11.
                String text = (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ());
                CommandExecutor.copyRaw(text);
            }
        }).bounds(centerX - coordBtnW / 2, top + COPY_BUTTON_OFFSET, coordBtnW, 18).build());

        for (int i = 0; i < entries.size(); i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            addOp(gridLeft + col * (btnW + GAP_X), gridTop + row * (BUTTON_HEIGHT + GAP_Y),
                    btnW, BUTTON_HEIGHT, entries.get(i).langKey(), entries.get(i).action());
        }
    }

    private void addOp(int x, int y, int w, int h, String key, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
                .bounds(x, y, w, h).build());
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        var player = this.minecraft.player;
        if (player != null) {
            String dimension = player.level().dimension().location().toString();
            Direction facing = player.getDirection();
            String facingCn = cnFacing(facing);

            int infoTop = contentTop();
            String line1 = "玩家：" + player.getGameProfile().getName() + "    维度：" + dimension;
            String line2 = String.format("X: %.1f  Y: %.1f  Z: %.1f    朝向：%s", player.getX(), player.getY(), player.getZ(), facingCn);

            guiGraphics.drawCenteredString(this.font, line1, this.width / 2, infoTop, COLOR_TEXT);
            guiGraphics.drawCenteredString(this.font, line2, this.width / 2, infoTop + 12, COLOR_TEXT);

            if (!CommandExecutor.likelyHasPermission()) {
                guiGraphics.drawCenteredString(this.font, "⚠ 当前身份可能没有作弊权限，执行前请确认已开启作弊", this.width / 2, infoTop + 26, 0xFFFF5555);
            }
        }
    }

    private static String cnFacing(Direction d) {
        return switch (d) {
            case NORTH -> "北";
            case SOUTH -> "南";
            case EAST -> "东";
            case WEST -> "西";
            default -> "-";
        };
    }

}
