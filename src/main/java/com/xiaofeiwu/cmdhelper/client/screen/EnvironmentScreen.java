package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.command.CommandTreeReader;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /weather and /time share this screen — neither one has enough content to justify its own page. */
public class EnvironmentScreen extends CmdHelperScreen {

    private enum TimeMode {SET, ADD, QUERY}

    private static final List<String> WEATHER_TYPES = List.of("clear", "rain", "thunder");
    private static final Map<String, String> WEATHER_LABELS = Map.of(
            "clear", "晴天",
            "rain", "下雨",
            "thunder", "雷暴"
    );

    private static final Map<TimeMode, String> TIME_MODE_LABELS = Map.of(
            TimeMode.SET, "设置时间",
            TimeMode.ADD, "增加时间",
            TimeMode.QUERY, "查询时间"
    );

    private static final String CUSTOM_VALUE = "custom";
    private static final List<String> SET_VALUES = List.of("day", "noon", "night", "midnight", CUSTOM_VALUE);
    private static final Map<String, String> SET_LABELS = Map.of(
            "day", "白天 (1000)",
            "noon", "正午 (6000)",
            "night", "夜晚 (13000)",
            "midnight", "午夜 (18000)",
            CUSTOM_VALUE, "自定义数值"
    );

    private static final Map<String, String> QUERY_LABELS = Map.of(
            "daytime", "白天时间 (0-24000循环)",
            "gametime", "世界运行总刻数",
            "day", "第几天"
    );

    // Weather state
    private String weatherType;

    // Time state
    private TimeMode timeMode;
    private String setValue;
    private String customValue;
    private String addAmount;
    private String queryType;
    private EditBox customValueBox;
    private EditBox addAmountBox;

    private static final int TIME_Y = 100;

    public EnvironmentScreen(Screen parent) {
        this(parent, "clear", TimeMode.SET, "day", "", "1000", "daytime");
    }

    private EnvironmentScreen(Screen parent, String weatherType,
                               TimeMode timeMode, String setValue, String customValue, String addAmount, String queryType) {
        super(Component.literal("天气与时间"), parent);
        this.weatherType = weatherType;
        this.timeMode = timeMode;
        this.setValue = setValue;
        this.customValue = customValue;
        this.addAmount = addAmount;
        this.queryType = queryType;
    }

    private EnvironmentScreen rebuiltWithTimeMode(TimeMode newMode) {
        return new EnvironmentScreen(parent, weatherType,
                newMode, setValue, currentCustomValue(), currentAddAmount(), queryType);
    }

    private String currentCustomValue() {
        return customValueBox != null ? customValueBox.getValue() : customValue;
    }

    private String currentAddAmount() {
        return addAmountBox != null ? addAmountBox.getValue() : addAmount;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        // --- 天气 ---
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                WEATHER_TYPES, weatherType, WEATHER_LABELS::get, value -> this.weatherType = value)));

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b ->
                CommandExecutor.execute(weatherCommand())
        ).bounds(centerX + 10, 66, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b ->
                CommandExecutor.copyToClipboard(weatherCommand())
        ).bounds(centerX + 105, 66, 90, 18).build());

        // --- 时间 ---
        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, TIME_Y, 300, 20,
                List.of(TimeMode.values()), timeMode, TIME_MODE_LABELS::get,
                value -> this.minecraft.setScreen(rebuiltWithTimeMode(value)))));

        int timeContentY = TIME_Y + 26;
        switch (timeMode) {
            case SET -> {
                this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, timeContentY, 200, 18,
                        SET_VALUES, setValue, SET_LABELS::get, value -> this.setValue = value)));
                this.customValueBox = new EditBox(this.font, centerX + 58, timeContentY, 92, 18, Component.literal("数值"));
                this.customValueBox.setValue(customValue);
                this.customValueBox.setHint(Component.literal("0-24000"));
                this.customValueBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}"));
                this.addRenderableWidget(this.customValueBox);
            }
            case ADD -> {
                this.addAmountBox = new EditBox(this.font, centerX - 150, timeContentY, 190, 18, Component.literal("增加量"));
                this.addAmountBox.setValue(addAmount);
                this.addAmountBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,7}"));
                this.addRenderableWidget(this.addAmountBox);
            }
            case QUERY -> {
                List<String> types = new ArrayList<>();
                CommandTreeReader.findNode("time", "query").ifPresentOrElse(
                        node -> types.addAll(CommandTreeReader.literalChildNames(node)),
                        () -> types.addAll(List.of("daytime", "gametime", "day"))
                );
                this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, timeContentY, 300, 18,
                        types, queryType, t -> QUERY_LABELS.getOrDefault(t, t), value -> this.queryType = value)));
            }
        }

        int timeButtonsY = timeContentY + 30;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b ->
                CommandExecutor.execute(timeCommand())
        ).bounds(centerX + 10, timeButtonsY, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b ->
                CommandExecutor.copyToClipboard(timeCommand())
        ).bounds(centerX + 105, timeButtonsY, 90, 18).build());
    }

    private String weatherCommand() {
        return CommandBuilders.weather(weatherType, null);
    }

    private String timeCommand() {
        return switch (timeMode) {
            case SET -> {
                if (setValue.equals(CUSTOM_VALUE)) {
                    String v = customValueBox.getValue().trim();
                    yield CommandBuilders.timeSet(v.isEmpty() ? "0" : v);
                }
                yield CommandBuilders.timeSet(setValue);
            }
            case ADD -> {
                String v = addAmountBox.getValue().trim();
                yield CommandBuilders.timeAdd(v.isEmpty() ? "0" : v);
            }
            case QUERY -> CommandBuilders.timeQuery(queryType);
        };
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;

        guiGraphics.drawString(this.font, "天气", centerX - 190, 46, COLOR_TEXT, false);

        guiGraphics.hLine(centerX - 150, centerX + 150, TIME_Y - 8, COLOR_BORDER);

        int timeContentY = TIME_Y + 26;
        int timeButtonsY = timeContentY + 30;
        if (timeMode == TimeMode.QUERY) {
            guiGraphics.drawCenteredString(this.font, "查询结果会显示在游戏聊天框里", centerX, timeButtonsY + 26, COLOR_MUTED);
        }
    }
}
