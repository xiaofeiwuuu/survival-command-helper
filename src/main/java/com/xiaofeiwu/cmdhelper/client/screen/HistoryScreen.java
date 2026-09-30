package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandDescriber;
import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import com.xiaofeiwu.cmdhelper.client.history.CommandHistoryStore;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryNames;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.SimpleListWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class HistoryScreen extends CmdHelperScreen {

    private enum Tab {HISTORY, FAVORITES}

    // Room kept above the buttons for the selected command's full text (it's cut off in the row).
    private static final int DETAIL_HEIGHT = 40;

    private Tab tab;
    private SimpleListWidget list;
    private Button favoriteButton;

    public static HistoryScreen favorites(Screen parent) {
        return new HistoryScreen(parent, Tab.FAVORITES);
    }

    public HistoryScreen(Screen parent) {
        this(parent, Tab.HISTORY);
    }

    private HistoryScreen(Screen parent, Tab tab) {
        super(Component.literal(tab == Tab.HISTORY ? "历史记录" : "收藏指令"), parent);
        this.tab = tab;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        this.trackDropdown(this.addRenderableWidget(new DropdownWidget<>(centerX - 150, 40, 300, 20,
                List.of(Tab.HISTORY, Tab.FAVORITES), tab,
                t -> t == Tab.HISTORY ? "历史记录（最近 20 条）" : "收藏指令",
                value -> this.minecraft.setScreen(new HistoryScreen(parent, value)))));

        int listTop = 68;
        int rows = Math.max(1, ((this.height - 56 - DETAIL_HEIGHT) - listTop) / SimpleListWidget.TWO_LINE_ROW_HEIGHT);
        this.list = new SimpleListWidget(centerX - 150, listTop, 300, rows,
                SimpleListWidget.TWO_LINE_ROW_HEIGHT, s -> {
        });
        this.list.setDescriber(command -> CommandDescriber.describe(command, RegistryNames.INSTANCE));
        this.list.setEmptyMessage(tab == Tab.HISTORY ? "还没有执行或复制过指令" : "还没有收藏任何指令");
        this.list.setItems(tab == Tab.HISTORY ? CommandHistoryStore.history() : CommandHistoryStore.favorites());

        int pageY = listTop + list.pixelHeight() + 6;
        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> list.prevPage())
                .bounds(centerX - 60, pageY, 20, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> list.nextPage())
                .bounds(centerX + 40, pageY, 20, 16).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.execute"), b -> {
            String cmd = list.getSelected();
            if (cmd != null) {
                CommandExecutor.execute(cmd);
            }
        }).bounds(centerX - 150, this.height - 26, 90, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cmdhelper.copy"), b -> {
            String cmd = list.getSelected();
            if (cmd != null) {
                CommandExecutor.copyToClipboard(cmd);
            }
        }).bounds(centerX - 55, this.height - 26, 90, 18).build());

        this.favoriteButton = Button.builder(Component.literal(favoriteLabel()), b -> {
            String cmd = list.getSelected();
            if (cmd != null) {
                CommandHistoryStore.toggleFavorite(cmd);
                this.favoriteButton.setMessage(Component.literal(favoriteLabel()));
                if (tab == Tab.FAVORITES) {
                    list.setItems(CommandHistoryStore.favorites());
                }
            }
        }).bounds(centerX + 40, this.height - 26, 110, 18).build();
        this.addRenderableWidget(this.favoriteButton);
    }

    private String favoriteLabel() {
        String cmd = list == null ? null : list.getSelected();
        if (cmd == null) {
            return "★ 收藏/取消";
        }
        return CommandHistoryStore.isFavorite(cmd) ? "★ 取消收藏" : "☆ 收藏此指令";
    }

    @Override
    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = list.mouseClicked(mouseX, mouseY, button);
        if (handled && favoriteButton != null) {
            favoriteButton.setMessage(Component.literal(favoriteLabel()));
        }
        return handled;
    }

    @Override
    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }

    private String ellipsize(String text, int maxWidth) {
        return this.font.width(text) <= maxWidth ? text
                : this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    /** The row is one truncated line; this shows the selected command in full (wrapped), with its explanation. */
    private void renderSelectedDetail(GuiGraphics g, String command) {
        int left = this.width / 2 - 150;
        int lineY = this.height - 56 - DETAIL_HEIGHT + 30;
        String description = list.descriptionOf(command);
        if (description != null) {
            g.drawString(this.font, ellipsize(description, 300), left, lineY, COLOR_ACCENT, false);
            lineY += 10;
        }
        int linesLeft = description != null ? 2 : 3;
        for (FormattedCharSequence line : this.font.split(Component.literal("/" + command), 300)) {
            if (linesLeft-- <= 0) {
                break;
            }
            g.drawString(this.font, line, left, lineY, COLOR_MUTED, false);
            lineY += 10;
        }
    }

    @Override
    protected void renderExtra(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        list.render(guiGraphics, mouseX, mouseY);
        String label = "第 " + (list.currentPage() + 1) + " / " + list.totalPages() + " 页";
        int listTop = 68;
        guiGraphics.drawCenteredString(this.font, label, this.width / 2, listTop + list.pixelHeight() + 10, COLOR_MUTED);
        String selected = list.getSelected();
        if (selected == null) {
            guiGraphics.drawCenteredString(this.font, "点一条指令来选中它，再执行/复制/收藏", this.width / 2, this.height - 40, COLOR_MUTED);
        } else {
            renderSelectedDetail(guiGraphics, selected);
        }
    }
}
