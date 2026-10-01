package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.command.RegionBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a box of blocks out of the world the client has loaded and saves it as a blueprint.
 *
 * The client can only see chunks it has loaded, so the whole area has to be within the render distance;
 * that's checked up front rather than silently scanning loaded air where a building should be. The
 * reading is spread over several ticks so a big area doesn't freeze the game.
 */
public final class BlueprintScanner {

    public enum Status {IDLE, RUNNING, DONE, FAILED}

    public static final int MAX_CELLS = 1_000_000;
    private static final int CELLS_PER_TICK = 150_000;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static Job job;
    private static Status status = Status.IDLE;
    private static String message = "";
    private static float progress;

    private BlueprintScanner() {
    }

    public static Status status() {
        return status;
    }

    public static String message() {
        return message;
    }

    public static float progress() {
        return progress;
    }

    /** The player has seen the last result: forget it, so reopening the screen doesn't show an old message. */
    public static void acknowledge() {
        if (status != Status.RUNNING) {
            status = Status.IDLE;
            message = "";
        }
    }

    private static final class Job {
        final ClientLevel level;
        final String name;
        final RegionBounds region;
        final int sx, sy, sz;
        final int[] cells;
        final Map<BlockState, Integer> index = new HashMap<>();
        final List<String> palette = new ArrayList<>();
        int cursor;

        Job(ClientLevel level, String name, RegionBounds region) {
            this.level = level;
            this.name = name;
            this.region = region;
            this.sx = region.sizeX();
            this.sy = region.sizeY();
            this.sz = region.sizeZ();
            this.cells = new int[sx * sy * sz];
        }

        /** @return true when every cell has been read */
        boolean step() {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int end = Math.min(cells.length, cursor + CELLS_PER_TICK);
            for (; cursor < end; cursor++) {
                int x = cursor % sx;
                int rest = cursor / sx;
                int z = rest % sz;
                int y = rest / sz;
                pos.set(region.minX() + x, region.minY() + y, region.minZ() + z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    state = AIR; // cave air and void air are just "nothing" here
                }
                Integer known = index.get(state);
                if (known == null) {
                    known = palette.size();
                    index.put(state, known);
                    palette.add(StateStrings.minimal(state));
                }
                cells[cursor] = known;
            }
            progress = cells.length == 0 ? 1f : (float) cursor / cells.length;
            return cursor >= cells.length;
        }
    }

    /**
     * Starts a scan.
     * @return why it can't start (shown to the player), or null if it has started
     */
    public static String start(String name, RegionBounds region) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return "不在世界里";
        }
        if (status == Status.RUNNING) {
            return "上一次扫描还没结束";
        }
        if (region.volume() > MAX_CELLS) {
            return "区域有 " + region.volume() + " 格，超过一次能扫描的 " + MAX_CELLS + " 格，请分成几块";
        }
        int notLoaded = 0;
        for (int cx = Math.floorDiv(region.minX(), 16); cx <= Math.floorDiv(region.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(region.minZ(), 16); cz <= Math.floorDiv(region.maxZ(), 16); cz++) {
                if (level.getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                    notLoaded++;
                }
            }
        }
        if (notLoaded > 0) {
            return "区域里有 " + notLoaded + " 个区块客户端还没加载，请走近一些再扫描（或缩小范围）";
        }
        job = new Job(level, name, region);
        status = Status.RUNNING;
        progress = 0f;
        message = "扫描中…";
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.level != job.level) { // left the world / changed dimension mid-scan
            job = null;
            status = Status.FAILED;
            message = "扫描被中断（离开了这个世界）";
            return;
        }
        try {
            if (!job.step()) {
                return;
            }
            Job finished = job;
            job = null;
            List<Cuboid> cuboids = VoxelMerger.merge(finished.cells, finished.sx, finished.sy, finished.sz);
            Blueprint saved = BlueprintStore.shared().save(new Blueprint(finished.name, finished.sx, finished.sy,
                    finished.sz, finished.palette, cuboids, System.currentTimeMillis()));
            status = Status.DONE;
            message = "已保存「" + saved.name() + "」：" + saved.sizeX() + "×" + saved.sizeY() + "×" + saved.sizeZ()
                    + "，" + saved.volume() + " 格，" + cuboids.size() + " 个方框，用到 " + saved.namespaces().size() + " 个模组";
        } catch (IOException e) {
            job = null;
            status = Status.FAILED;
            message = "保存失败：" + e.getMessage();
        } catch (RuntimeException e) {
            job = null;
            status = Status.FAILED;
            message = "扫描出错：" + e;
        }
    }
}
