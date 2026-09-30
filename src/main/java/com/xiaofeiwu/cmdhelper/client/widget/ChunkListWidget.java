package com.xiaofeiwu.cmdhelper.client.widget;

import com.xiaofeiwu.cmdhelper.client.forceload.ForceLoadedChunks.Chunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Consumer;

/**
 * A paginated list of chunks, each row with its own 传送 / 取消 buttons on the right. Drawn and
 * hit-tested by hand, the same screen-driven pattern as the other list widgets here (real Button
 * widgets would have to be rebuilt every time the page or the list changes).
 */
public class ChunkListWidget {

    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 38;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;

    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_MUTED = 0xFF8A8A9A;
    private static final int COLOR_BUTTON = 0xFF2E2E3C;
    private static final int COLOR_BUTTON_HOVER = 0xFF4A4A5E;
    private static final int COLOR_BUTTON_BORDER = 0xFF5A5A6C;
    private static final int COLOR_DANGER_HOVER = 0xFF6A2E2E;

    private final int x;
    private final int y;
    private final int width;
    private final int rows;
    private final Consumer<Chunk> onTeleport;
    private final Consumer<Chunk> onRemove;

    private List<Chunk> chunks = List.of();
    private int page = 0;
    private String emptyMessage = "";

    public ChunkListWidget(int x, int y, int width, int rows, Consumer<Chunk> onTeleport, Consumer<Chunk> onRemove) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.rows = Math.max(1, rows);
        this.onTeleport = onTeleport;
        this.onRemove = onRemove;
    }

    public int pixelHeight() {
        return rows * ROW_HEIGHT;
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
    }

    public void setChunks(List<Chunk> chunks) {
        this.chunks = chunks;
        if (page >= totalPages()) {
            page = Math.max(0, totalPages() - 1);
        }
    }

    public int totalPages() {
        return Math.max(1, (chunks.size() + rows - 1) / rows);
    }

    public int currentPage() {
        return page;
    }

    public void nextPage() {
        if (page < totalPages() - 1) {
            page++;
        }
    }

    public void prevPage() {
        if (page > 0) {
            page--;
        }
    }

    // ---- geometry ------------------------------------------------------------------------------

    private int removeButtonX() {
        return x + width - BUTTON_WIDTH - 4;
    }

    private int teleportButtonX() {
        return removeButtonX() - BUTTON_GAP - BUTTON_WIDTH;
    }

    private int buttonY(int rowY) {
        return rowY + (ROW_HEIGHT - BUTTON_HEIGHT) / 2;
    }

    private static boolean inside(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    // ---- drawing -------------------------------------------------------------------------------

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        if (chunks.isEmpty()) {
            if (!emptyMessage.isEmpty()) {
                g.drawCenteredString(font, emptyMessage, x + width / 2, y + pixelHeight() / 2 - 4, COLOR_MUTED);
            }
            return;
        }
        var player = Minecraft.getInstance().player;
        int start = page * rows;
        int end = Math.min(chunks.size(), start + rows);
        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * ROW_HEIGHT;
            Chunk chunk = chunks.get(i);
            boolean rowHovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            g.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 1,
                    rowHovered ? 0xFF2A2A36 : ((i - start) % 2 == 0 ? 0x501A1A22 : 0x50151519));

            g.drawString(font, "区块 (" + chunk.x() + ", " + chunk.z() + ")", x + 6, rowY + 3, COLOR_TEXT, false);
            String detail = "方块 X " + chunk.minBlockX() + "~" + (chunk.minBlockX() + 15)
                    + "  Z " + chunk.minBlockZ() + "~" + (chunk.minBlockZ() + 15);
            if (player != null) {
                double dx = chunk.centerBlockX() - player.getX();
                double dz = chunk.centerBlockZ() - player.getZ();
                detail += "  距你 " + (int) Math.round(Math.sqrt(dx * dx + dz * dz)) + " 格";
            }
            g.drawString(font, detail, x + 6, rowY + 12, COLOR_MUTED, false);

            int by = buttonY(rowY);
            drawButton(g, "传送", teleportButtonX(), by, mouseX, mouseY, COLOR_BUTTON_HOVER);
            drawButton(g, "取消", removeButtonX(), by, mouseX, mouseY, COLOR_DANGER_HOVER);
        }
    }

    private void drawButton(GuiGraphics g, String label, int bx, int by, int mouseX, int mouseY, int hoverColor) {
        var font = Minecraft.getInstance().font;
        boolean hovered = inside(mouseX, mouseY, bx, by, BUTTON_WIDTH, BUTTON_HEIGHT);
        g.fill(bx - 1, by - 1, bx + BUTTON_WIDTH + 1, by + BUTTON_HEIGHT + 1, COLOR_BUTTON_BORDER);
        g.fill(bx, by, bx + BUTTON_WIDTH, by + BUTTON_HEIGHT, hovered ? hoverColor : COLOR_BUTTON);
        g.drawCenteredString(font, label, bx + BUTTON_WIDTH / 2, by + (BUTTON_HEIGHT - 8) / 2, COLOR_TEXT);
    }

    // ---- input ---------------------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || chunks.isEmpty() || mouseX < x || mouseX >= x + width
                || mouseY < y || mouseY >= y + pixelHeight()) {
            return false;
        }
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        int index = page * rows + row;
        if (index >= chunks.size()) {
            return false;
        }
        int by = buttonY(y + row * ROW_HEIGHT);
        Chunk chunk = chunks.get(index);
        if (inside(mouseX, mouseY, teleportButtonX(), by, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onTeleport.accept(chunk);
            return true;
        }
        if (inside(mouseX, mouseY, removeButtonX(), by, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            onRemove.accept(chunk);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + pixelHeight()) {
            return false;
        }
        if (delta > 0) {
            prevPage();
        } else if (delta < 0) {
            nextPage();
        }
        return true;
    }
}
