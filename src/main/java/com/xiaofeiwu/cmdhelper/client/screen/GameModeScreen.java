package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** /gamemode and /difficulty share this screen — /difficulty has no target of its own and
 *  isn't enough content to justify a separate page. */
public class GameModeScreen extends CmdHelperScreen {

    private static final List<String> TARGETS = List.of("@s", "@p", "@a", "@r");
    private static final List<String> TARGET_LABELS = List.of("自己 (@s)", "最近的玩家 (@p)", "所有玩家 (@a)", "随机玩家 (@r)");

    private static final List<String> MODES = List.of("survival", "creative", "adventure", "spectator");
    private static final List<String> MODE_LABELS = List.of(
            "生存模式 (survival)", "创造模式 (creative)", "冒险模式 (adventure)", "旁观模式 (spectator)");

    private static final List<String> DIFFICULTIES = List.of("peaceful", "easy", "normal", "hard");
    private static final List<String> DIFFICULTY_LABELS = List.of(
            "和平 (peaceful)", "简单 (easy)", "普通 (normal)", "困难 (hard)");

    private String target = "@s";
    private String mode = "survival";
    private String difficulty = "normal";

    // The gamemode block ends with its buttons at y=92..110, so the difficulty block starts
    // below that: separator, then a caption, then the dropdown (which used to sit at y=100 and
    // cover the buttons above it).
    private static final int GAMEMODE_BUTTONS_Y = 92;
    private static final int DIFFICULTY_Y = 142;

    public GameModeScreen(Screen parent) {
        super(Component.translatable("screen.cmdhelper.gamemode.title"), parent);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        // --- 游戏模式 ---
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                MODES, mode, m -> MODE_LABELS.get(MODES.indexOf(m)),
                value -> this.mode = value)));

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 66, 300, 20,
                TARGETS, target, t -> TARGET_LABELS.get(TARGETS.indexOf(t)),
                value -> this.target = value)));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b ->
                CommandExecutor.execute(gameModeCommand())
        ).bounds(centerX + 10, GAMEMODE_BUTTONS_Y, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b ->
                CommandExecutor.copyToClipboard(gameModeCommand())
        ).bounds(centerX + 105, GAMEMODE_BUTTONS_Y, 90, 18).build());

        // --- 难度 ---
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, DIFFICULTY_Y, 300, 20,
                DIFFICULTIES, difficulty, d -> DIFFICULTY_LABELS.get(DIFFICULTIES.indexOf(d)),
                value -> this.difficulty = value)));

        int difficultyButtonsY = DIFFICULTY_Y + 26;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b ->
                CommandExecutor.execute(difficultyCommand())
        ).bounds(centerX + 10, difficultyButtonsY, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b ->
                CommandExecutor.copyToClipboard(difficultyCommand())
        ).bounds(centerX + 105, difficultyButtonsY, 90, 18).build());
    }

    private String gameModeCommand() {
        return CommandBuilders.gameMode(mode, target);
    }

    private String difficultyCommand() {
        return CommandBuilders.difficulty(difficulty);
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        guiGraphics.hLine(centerX - 150, centerX + 150, DIFFICULTY_Y - 24, COLOR_BORDER);
        guiGraphics.drawString(this.font, "难度（影响整个世界，不是只有自己）", centerX - 150, DIFFICULTY_Y - 14, COLOR_MUTED, false);
    }
}
