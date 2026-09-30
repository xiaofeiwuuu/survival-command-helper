package com.xiaofeiwu.cmdhelper.client.screen;

import com.xiaofeiwu.cmdhelper.client.command.CommandDescriber;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryNames;
import com.xiaofeiwu.cmdhelper.client.widget.CoordinateFields;
import com.xiaofeiwu.cmdhelper.client.widget.DropdownWidget;
import com.xiaofeiwu.cmdhelper.client.widget.RegistryGridWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared chrome for every menu in this mod: a solid dark panel behind the content
 * (other mods on this client happily paint busy backgrounds right through un-boxed
 * vanilla widgets, and this pack's button sprites render nearly invisible against a
 * dark background), a header bar with the title, and — when there's a parent — a
 * back button. Also owns the single open dropdown, if any, so its popup always
 * paints above everything else and always eats the next click.
 */
public abstract class CmdHelperScreen extends Screen {

    protected static final int COLOR_PANEL_BG = 0xE8101014;
    protected static final int COLOR_HEADER_BG = 0xF01C1C24;
    protected static final int COLOR_BORDER = 0xFF3A3A46;
    protected static final int COLOR_TEXT = 0xFFEDEDED;
    protected static final int COLOR_MUTED = 0xFFA8A8B4;
    protected static final int COLOR_ACCENT = 0xFFFFD34D;

    protected static final int HEADER_HEIGHT = 30;

    protected static final int COLOR_ERROR = 0xFFFF6B6B;
    protected static final int COLOR_WARNING = 0xFFFFC857;

    /** What a screen would send right now: either a command (optionally with a warning about it)
     *  or the reason there isn't one yet. */
    protected record Preview(String command, String summary, String note) {
        public static Preview ok(String command) {
            return new Preview(command, null, null);
        }

        public static Preview warn(String command, String warning) {
            return new Preview(command, null, warning);
        }

        /** For actions that send several commands: a sentence describing them, shown without a leading "/". */
        public static Preview summary(String text, String warningOrNull) {
            return new Preview(null, text, warningOrNull);
        }

        public static Preview problem(String reason) {
            return new Preview(null, null, reason);
        }
    }

    protected final Screen parent;
    private final List<DropdownWidget<?>> dropdowns = new ArrayList<>();
    // Minecraft rebuilds every widget (calling init() again) on a window resize, and this mod
    // also rebuilds screens to switch modes — both used to wipe whatever was typed. Boxes
    // registered through remember() have their text kept here and put back on the next init.
    private final Map<String, EditBox> trackedBoxes = new LinkedHashMap<>();
    private final Map<String, String> savedInputs = new LinkedHashMap<>();

    protected CmdHelperScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        captureInputs();
        dropdowns.clear();
        if (parent != null) {
            this.addRenderableWidget(Button.builder(Component.literal("< 返回"), b -> this.minecraft.setScreen(parent))
                    .bounds(8, 6, 60, 16).build());
        }
    }

    /** Registers a text box so its text survives this screen being rebuilt, and restores any
     *  text saved for the same key. Call it before attaching a responder, so restoring doesn't
     *  fire the responder while the screen is still half-built. */
    protected <B extends EditBox> B remember(String key, B box) {
        String saved = savedInputs.get(key);
        if (saved != null) {
            box.setValue(saved);
        }
        trackedBoxes.put(key, box);
        return box;
    }

    protected void rememberCoords(String prefix, CoordinateFields fields) {
        remember(prefix + ".x", fields.xBox);
        remember(prefix + ".y", fields.yBox);
        remember(prefix + ".z", fields.zBox);
    }

    /** For non-text state that should also survive a rebuild (e.g. the mod-filter dropdown). */
    protected void saveState(String key, String value) {
        savedInputs.put(key, value);
    }

    protected String savedState(String key, String fallback) {
        return savedInputs.getOrDefault(key, fallback);
    }

    private void captureInputs() {
        trackedBoxes.forEach((key, box) -> savedInputs.put(key, box.getValue()));
        trackedBoxes.clear();
    }

    /** Carries everything typed so far into a freshly built replacement screen. */
    protected void passInputsTo(CmdHelperScreen next) {
        captureInputs();
        next.savedInputs.putAll(this.savedInputs);
    }

    /**
     * Hover text for a widget, shown after a short pause; "\n" starts a new line. Every text box says
     * what it wants here, since a bare box gives no hint (why is there a long one after the three
     * coordinate boxes?).
     */
    protected <W extends AbstractWidget> W tip(W widget, String text) {
        widget.setTooltip(Tooltip.create(Component.literal(text)));
        widget.setTooltipDelay(150);
        return widget;
    }

    /** The title bar across the top. The main menu turns it off: it's a launcher, not a page. */
    protected boolean showHeader() {
        return true;
    }

    /** Screens override this to feed the command-preview line above the buttons. */
    protected Preview preview() {
        return null;
    }

    /** Every DropdownWidget a screen creates must be registered here so its popup can be
     *  drawn on top of everything else and get first claim on the click that closes it. */
    protected <T> DropdownWidget<T> trackDropdown(DropdownWidget<T> dropdown) {
        this.dropdowns.add(dropdown);
        return dropdown;
    }

    private DropdownWidget<?> openDropdown() {
        for (DropdownWidget<?> d : dropdowns) {
            if (d.isOpen()) {
                return d;
            }
        }
        return null;
    }

    /** Item tooltips (unlike this screen's own dropdown popups) render at a Z-depth Minecraft
     *  reserves for always-on-top content — higher even than a dropdown's own +300 popup boost
     *  (see DropdownWidget.renderOverlay). So a RegistryGridWidget tooltip for a cell hidden
     *  under an open dropdown's popup would otherwise paint right through it. Screens should
     *  skip their grid's renderTooltip() call while this is true. */
    protected boolean isAnyDropdownOpen() {
        return openDropdown() != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While a dropdown's popup is open, it must get the very next click — whether that's
        // picking an option or clicking away to dismiss it — before any widget underneath
        // the popup (the item list is usually right there) gets a chance to react to it.
        DropdownWidget<?> open = openDropdown();
        if (open != null) {
            return open.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Nothing in the normal widget list claimed it — give a screen's own non-widget
        // component (the item/block/entity grid, which isn't a real GuiEventListener) a shot.
        return onExtraMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        DropdownWidget<?> open = openDropdown();
        if (open != null) {
            return open.mouseScrolled(mouseX, mouseY, delta);
        }
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return onExtraMouseScrolled(mouseX, mouseY, delta);
    }

    protected boolean onExtraMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onExtraMouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /** "◀ / ▶" buttons for a RegistryGridWidget; pair with drawPaginationLabel in renderExtra. */
    protected void addPaginationButtons(RegistryGridWidget grid, int y) {
        int centerX = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> grid.prevPage())
                .bounds(centerX - 60, y, 20, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> grid.nextPage())
                .bounds(centerX + 40, y, 20, 16).build());
    }

    protected void drawPaginationLabel(GuiGraphics g, RegistryGridWidget grid, int y) {
        String label = "第 " + (grid.currentPage() + 1) + " / " + grid.totalPages() + " 页";
        g.drawCenteredString(this.font, label, this.width / 2, y + 4, COLOR_MUTED);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        DropdownWidget<?> open = openDropdown();
        if (open != null) {
            return open.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DropdownWidget<?> open = openDropdown();
        if (open != null) {
            return open.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, COLOR_PANEL_BG);
        if (showHeader()) {
            g.fill(0, 0, this.width, HEADER_HEIGHT, COLOR_HEADER_BG);
            g.hLine(0, this.width - 1, HEADER_HEIGHT, COLOR_BORDER);
        }

        drawWidgetChrome(g);
        super.render(g, mouseX, mouseY, partialTick);

        if (showHeader()) {
            g.drawCenteredString(this.font, this.title, this.width / 2, 10, COLOR_TEXT);
        }

        renderExtra(g, mouseX, mouseY, partialTick);
        renderPreview(g);

        DropdownWidget<?> open = openDropdown();
        if (open != null) {
            open.renderOverlay(g, mouseX, mouseY);
        }
    }

    /** Bottom-of-screen line just above the Execute/Copy buttons: the exact command, or why
     *  there isn't one. A warning (something will be ignored / rejected) sits under the command. */
    private void renderPreview(GuiGraphics g) {
        Preview preview = preview();
        if (preview == null) {
            return;
        }
        int maxWidth = this.width - 40;
        int centerX = this.width / 2;
        int commandY = this.height - 47;
        if (preview.command() != null || preview.summary() != null) {
            String line = preview.command() != null ? "/" + preview.command() : preview.summary();
            g.drawCenteredString(this.font, ellipsize(line, maxWidth), centerX, commandY, COLOR_ACCENT);
            if (preview.note() != null) {
                g.drawCenteredString(this.font, ellipsize("⚠ " + preview.note(), maxWidth), centerX, commandY + 10, COLOR_WARNING);
            } else if (preview.command() != null) {
                // A warning wins the second line; otherwise say in Chinese what the command does.
                String explanation = explain(preview.command());
                if (explanation != null) {
                    g.drawCenteredString(this.font, ellipsize(explanation, maxWidth), centerX, commandY + 10, COLOR_MUTED);
                }
            }
        } else if (preview.note() != null) {
            g.drawCenteredString(this.font, ellipsize("✗ " + preview.note(), maxWidth), centerX, commandY + 5, COLOR_ERROR);
        }
    }

    // preview() is drawn every frame but a command's explanation never changes: remember the last one.
    private String explainedCommand;
    private String explanation;

    private String explain(String command) {
        if (!command.equals(explainedCommand)) {
            explainedCommand = command;
            explanation = CommandDescriber.describe(command, RegistryNames.INSTANCE);
        }
        return explanation;
    }

    protected String ellipsize(String text, int maxWidth) {
        return this.font.width(text) <= maxWidth ? text
                : this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    /** Screens draw their own field labels / command preview here instead of overriding render(). */
    protected void renderExtra(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** Solid background + border behind every plain text field, so it's visible no matter what
     *  other mods do to this client's default widget skinning. */
    private void drawWidgetChrome(GuiGraphics g) {
        for (var renderable : this.renderables) {
            if (renderable instanceof EditBox box) {
                int x = box.getX();
                int y = box.getY();
                int w = box.getWidth();
                int h = box.getHeight();
                g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5A5A6C);
                g.fill(x, y, x + w, y + h, 0xFF17171C);
            }
        }
    }
}
