package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Placed;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Plan;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a blueprint's /fill commands a few per tick (not all at once, which would be hundreds in a
 * single tick), shows progress, and can be cancelled with a left-click. Afterwards it looks at what
 * actually came out and sends only the fixes for what is wrong — a door that popped off, sand that
 * fell — up to two rounds.
 */
public final class BlueprintExecutor {

    private static final int COMMANDS_PER_TICK = 4;
    private static final int WAIT_BEFORE_CHECK_TICKS = 20;
    private static final int MAX_FIX_ROUNDS = 2;

    private enum Stage {SENDING, WAITING_TO_CHECK}

    private static final class Job {
        final Blueprint blueprint;
        final int[] min;
        final int sizeX, sizeY, sizeZ;
        final PaletteBuilder.Prepared prepared;
        final List<Cuboid> builtBoxes = new ArrayList<>();
        final boolean verify;
        final ArrayDeque<String> queue = new ArrayDeque<>();
        int total;
        int sent;
        int fixRounds;
        int waited;
        int ticks;
        Stage stage = Stage.SENDING;

        Job(Blueprint blueprint, Transform transform, int[] min, PaletteBuilder.Prepared prepared, Plan plan, boolean verify) {
            this.blueprint = blueprint;
            this.min = min;
            int[] size = transform.horizontalSize(blueprint.sizeX(), blueprint.sizeZ());
            this.sizeX = size[0];
            this.sizeY = blueprint.sizeY();
            this.sizeZ = size[1];
            this.prepared = prepared;
            this.verify = verify;
            for (Placed placed : plan.placed()) {
                if (!placed.skipped()) {
                    builtBoxes.add(placed.cuboid());
                }
            }
            queue.addAll(plan.commands());
            total = queue.size();
        }
    }

    private static Job job;

    private BlueprintExecutor() {
    }

    public static boolean isRunning() {
        return job != null;
    }

    public static void start(Blueprint blueprint, Transform transform, int[] min, PaletteBuilder.Prepared prepared,
                             Plan plan, boolean includeAir, boolean verify) {
        job = new Job(blueprint, transform, min, prepared, plan, verify);
        var stats = plan.stats();
        chat("开始放置蓝图「" + blueprint.name() + "」：" + job.total + " 条 fill"
                + (stats.missingBlocks() > 0 ? "，跳过缺少的方块 " + stats.missingBlocks() + " 格" : "")
                + "。左键可以取消", ChatFormatting.GRAY);
    }

    private static void chat(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), false);
        }
    }

    private static void bar(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text).withStyle(color), true);
        }
    }

    private static void finish(String text, ChatFormatting color) {
        job = null;
        chat(text, color);
    }

    // ---- per tick ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.connection == null) {
            job = null;
            return;
        }
        job.ticks++;
        if (job.stage == Stage.SENDING) {
            for (int i = 0; i < COMMANDS_PER_TICK && !job.queue.isEmpty(); i++) {
                mc.player.connection.sendCommand(job.queue.poll());
                job.sent++;
            }
            if (job.ticks % 5 == 0 || job.queue.isEmpty()) {
                bar("放置蓝图 " + job.sent + "/" + job.total + "（" + (job.total == 0 ? 100 : job.sent * 100 / job.total)
                        + "%）  左键取消", ChatFormatting.AQUA);
            }
            if (job.queue.isEmpty()) {
                if (job.verify) {
                    job.stage = Stage.WAITING_TO_CHECK;
                    job.waited = 0;
                } else {
                    finish("蓝图「" + job.blueprint.name() + "」放置完成（共 " + job.total + " 条命令，未校验）", ChatFormatting.GREEN);
                }
            }
        } else if (++job.waited >= WAIT_BEFORE_CHECK_TICKS) {
            check(mc.level);
        }
    }

    // ---- checking the result -----------------------------------------------------------------------

    private static void check(ClientLevel level) {
        Job j = job;
        for (int cx = Math.floorDiv(j.min[0], 16); cx <= Math.floorDiv(j.min[0] + j.sizeX - 1, 16); cx++) {
            for (int cz = Math.floorDiv(j.min[2], 16); cz <= Math.floorDiv(j.min[2] + j.sizeZ - 1, 16); cz++) {
                if (level.getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                    finish("蓝图「" + j.blueprint.name() + "」放置完成（目标区域不在渲染范围内，没有校验）", ChatFormatting.GREEN);
                    return;
                }
            }
        }
        int[] expected = BlueprintVerifier.expectedGrid(j.builtBoxes, j.min[0], j.min[1], j.min[2], j.sizeX, j.sizeY, j.sizeZ);
        int[] actual = new int[expected.length];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < j.sizeY; y++) {
            for (int z = 0; z < j.sizeZ; z++) {
                for (int x = 0; x < j.sizeX; x++) {
                    pos.set(j.min[0] + x, j.min[1] + y, j.min[2] + z);
                    actual[VoxelMerger.index(x, y, z, j.sizeX, j.sizeZ)] =
                            BuiltInRegistries.BLOCK.getId(level.getBlockState(pos).getBlock());
                }
            }
        }
        List<Cuboid> wrong = BlueprintVerifier.mismatches(expected, actual, j.prepared.blockIds(), j.sizeX, j.sizeY, j.sizeZ);
        if (wrong.isEmpty()) {
            finish("蓝图「" + j.blueprint.name() + "」放置完成，校验通过"
                    + (j.fixRounds > 0 ? "（补发了 " + j.fixRounds + " 轮）" : ""), ChatFormatting.GREEN);
            return;
        }
        long wrongBlocks = wrong.stream().mapToLong(Cuboid::volume).sum();
        if (j.fixRounds >= MAX_FIX_ROUNDS) {
            finish("蓝图「" + j.blueprint.name() + "」放置完成，但仍有 " + wrongBlocks + " 格和蓝图不一致（已补发 "
                    + MAX_FIX_ROUNDS + " 轮）。可能是服务器规则或方块之间的相互影响造成的", ChatFormatting.YELLOW);
            return;
        }
        List<Cuboid> world = new ArrayList<>();
        for (Cuboid c : wrong) {
            world.add(c.moved(j.min[0], j.min[1], j.min[2]));
        }
        Plan fix = BlueprintPlacer.planWorld(world, j.prepared.entries(), true);
        if (fix.commands().isEmpty()) {
            finish("蓝图「" + j.blueprint.name() + "」放置完成，有 " + wrongBlocks + " 格不一致但无法补发", ChatFormatting.YELLOW);
            return;
        }
        j.fixRounds++;
        j.queue.addAll(fix.commands());
        j.total += fix.commands().size();
        j.stage = Stage.SENDING;
        chat("校验发现 " + wrongBlocks + " 格不一致，补发 " + fix.commands().size() + " 条命令（第 " + j.fixRounds + " 轮）", ChatFormatting.GRAY);
    }

    // ---- cancel -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (job == null || !event.isAttack()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        String name = job.blueprint.name();
        int sent = job.sent;
        int total = job.total;
        job = null;
        chat("已取消放置蓝图「" + name + "」（已发送 " + sent + "/" + total + " 条，已放的不会撤回）", ChatFormatting.YELLOW);
    }
}
