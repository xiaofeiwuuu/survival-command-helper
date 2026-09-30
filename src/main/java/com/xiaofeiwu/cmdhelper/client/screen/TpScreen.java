package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class TpScreen extends CmdHelperScreen {

    public enum Mode {
        SELF_TO_COORDS("传送自己到坐标"),
        SELF_TO_PLAYER("传送自己到玩家"),
        PLAYER_TO_PLAYER("传送玩家到玩家"),
        PLAYER_TO_COORDS("传送其他玩家到坐标");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private final Mode mode;

    private List<String> onlinePlayers = List.of();
    private String targetPlayer;
    private String destinationPlayer;
    private CoordinateFields coords;
    private int coordsLabelY = -1;

    public TpScreen(Screen parent) {
        this(parent, Mode.SELF_TO_COORDS);
    }

    public TpScreen(Screen parent, Mode mode) {
        super(Component.literal("传送 /teleport"), parent);
        this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int labelX = centerX - 190;
        int fieldX = centerX - 156;

        var connection = this.minecraft.getConnection();
        if (connection != null) {
            onlinePlayers = connection.getOnlinePlayers().stream()
                    .map(info -> info.getProfile().getName())
                    .sorted()
                    .toList();
        }
        if (onlinePlayers.isEmpty()) {
            onlinePlayers = List.of("(没有检测到其他在线玩家)");
        }
        // init() runs again on every window resize — keep the player already picked if still online.
        if (targetPlayer == null || !onlinePlayers.contains(targetPlayer)) {
            targetPlayer = onlinePlayers.get(0);
        }
        if (destinationPlayer == null || !onlinePlayers.contains(destinationPlayer)) {
            destinationPlayer = onlinePlayers.get(0);
        }

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                List.of(Mode.values()), mode, m -> m.label,
                value -> {
                    TpScreen next = new TpScreen(parent, value);
                    passInputsTo(next);
                    this.minecraft.setScreen(next);
                })));

        int y = 68;

        if (mode == Mode.PLAYER_TO_PLAYER || mode == Mode.PLAYER_TO_COORDS) {
            this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, y, 300, 20,
                    onlinePlayers, targetPlayer, s -> "被传送：" + s,
                    value -> this.targetPlayer = value)));
            y += 26;
        }

        if (mode == Mode.SELF_TO_PLAYER || mode == Mode.PLAYER_TO_PLAYER) {
            this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, y, 300, 20,
                    onlinePlayers, destinationPlayer, s -> "目的地：" + s,
                    value -> this.destinationPlayer = value)));
            y += 26;
        }

        if (mode == Mode.SELF_TO_COORDS || mode == Mode.PLAYER_TO_COORDS) {
            this.coordsLabelY = y;
            this.coords = CoordinateFields.create(this.font, fieldX, y, 55, 18);
            coords.withTooltips("目标位置");
            rememberCoords("coords", coords);
            this.addRenderableWidget(coords.xBox);
            this.addRenderableWidget(coords.yBox);
            this.addRenderableWidget(coords.zBox);
            this.addRenderableWidget(Button.builder(Component.literal("用当前坐标"), b -> {
                var p = this.minecraft.player;
                if (p != null) coords.fillFrom(p.getX(), p.getY(), p.getZ());
            }).bounds(centerX + 29, y, 90, 18).tooltip(Tooltip.create(Component.literal("把你现在站的位置填进左边的坐标格子\n（小数会向下取整）"))).build());
            this.addRenderableWidget(coords.createPasteBox(this.font, centerX + 123, y, 76, 18));
            y += 18;
        }

        int buttonsY = y + 16;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.execute(cmd);
            }
        }).bounds(centerX + 10, buttonsY, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            String cmd = buildCommand();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX + 105, buttonsY, 90, 18).build());
    }

    @Override
    protected Preview preview() {
        String command = buildCommand();
        if (command != null) {
            return Preview.ok(command);
        }
        boolean needsCoords = mode == Mode.SELF_TO_COORDS || mode == Mode.PLAYER_TO_COORDS;
        boolean needsTarget = mode == Mode.PLAYER_TO_PLAYER || mode == Mode.PLAYER_TO_COORDS;
        boolean needsDestination = mode == Mode.SELF_TO_PLAYER || mode == Mode.PLAYER_TO_PLAYER;
        if (needsTarget && !hasRealPlayer(targetPlayer)) {
            return Preview.problem("没有检测到可以传送的在线玩家");
        }
        if (needsDestination && !hasRealPlayer(destinationPlayer)) {
            return Preview.problem("没有检测到可以作为目的地的在线玩家");
        }
        if (needsCoords) {
            return Preview.problem("坐标还没填完整");
        }
        return Preview.problem("参数还不完整");
    }

    private String buildCommand() {
        return switch (mode) {
            case SELF_TO_COORDS -> coords != null && coords.isComplete()
                    ? CommandBuilders.teleportSelfToCoords(coords.coordString()) : null;
            case SELF_TO_PLAYER -> hasRealPlayer(destinationPlayer)
                    ? CommandBuilders.teleportSelfToPlayer(destinationPlayer) : null;
            case PLAYER_TO_PLAYER -> hasRealPlayer(targetPlayer) && hasRealPlayer(destinationPlayer)
                    ? CommandBuilders.teleportPlayerToPlayer(targetPlayer, destinationPlayer) : null;
            case PLAYER_TO_COORDS -> hasRealPlayer(targetPlayer) && coords != null && coords.isComplete()
                    ? CommandBuilders.teleportPlayerToCoords(targetPlayer, coords.coordString()) : null;
        };
    }

    private boolean hasRealPlayer(String name) {
        return name != null && !name.startsWith("(");
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (coords != null) {
            guiGraphics.drawString(this.font, "坐标", this.width / 2 - 190, coordsLabelY + 5, COLOR_MUTED, false);
        }
    }
}
