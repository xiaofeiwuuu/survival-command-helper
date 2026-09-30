package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDescriberTest {

    private static final Map<String, String> NAMES = Map.of(
            "minecraft:zombie", "僵尸",
            "minecraft:stone", "石头",
            "minecraft:diamond", "钻石",
            "minecraft:oak_stairs", "橡木楼梯",
            "minecraft:plains", "平原");

    private static final CommandDescriber.Names FAKE = new CommandDescriber.Names() {
        private String n(String id) {
            return NAMES.getOrDefault(id, id);
        }

        @Override
        public String entity(String id) {
            return n(id);
        }

        @Override
        public String item(String id) {
            return n(id);
        }

        @Override
        public String block(String id) {
            return n(id);
        }

        @Override
        public String biome(String id) {
            return n(id);
        }
    };

    private static String d(String command) {
        return CommandDescriber.describe(command, FAKE);
    }

    // ---- the important one: every command the builders can produce gets an explanation ----------

    @Test
    void everyCommandShapeTheBuildersProduce_isExplained() {
        List<String> commands = List.of(
                CommandBuilders.give("@s", "minecraft:diamond", 64, 1, 99),
                CommandBuilders.killRangeAll(10),
                CommandBuilders.killRangeType("minecraft:zombie", 10),
                CommandBuilders.killNearest(null, 10),
                CommandBuilders.killNearest("minecraft:zombie", 10),
                CommandBuilders.killCustom("@e[tag=boss]"),
                CommandBuilders.fill("0 64 0", "9 66 9", "minecraft:stone", null),
                CommandBuilders.fill("0 64 0", "9 66 9", "minecraft:stone", "replace"),
                CommandBuilders.fill("0 64 0", "9 66 9", "minecraft:stone", "replace minecraft:dirt"),
                CommandBuilders.fill("0 64 0", "9 66 9", "minecraft:stone", "hollow"),
                CommandBuilders.clone("0 0 0", "9 5 11", "20 0 0", null),
                CommandBuilders.clone("0 0 0", "9 5 11", "20 0 0", "masked"),
                CommandBuilders.clone("0 0 0", "9 5 11", "0 5 0", "replace force"),
                CommandBuilders.clone("0 0 0", "9 5 11", "20 0 0", "replace move"),
                CommandBuilders.setBlock("1 2 3", "minecraft:stone", null),
                CommandBuilders.setBlock("1 2 3", "minecraft:stone", "keep"),
                CommandBuilders.teleportSelfToCoords("1 2 3"),
                CommandBuilders.teleportSelfToPlayer("Steve"),
                CommandBuilders.teleportToSurface(136, -120),
                CommandBuilders.teleportKeepingHeight(136, -120),
                CommandBuilders.teleportToHeight(-736, 320, -720),
                CommandBuilders.teleportPlayerToPlayer("Steve", "Alex"),
                CommandBuilders.teleportPlayerToCoords("Steve", "1 2 3"),
                CommandBuilders.summon("minecraft:zombie", "1 2 3", "", ""),
                CommandBuilders.summon("minecraft:zombie", "1 2 3", "90.0", "0"),
                CommandBuilders.weather("rain", null),
                CommandBuilders.timeSet("day"),
                CommandBuilders.timeSet("5000"),
                CommandBuilders.timeAdd("1000"),
                CommandBuilders.timeQuery("daytime"),
                CommandBuilders.gameMode("creative", "@s"),
                CommandBuilders.difficulty("hard"),
                CommandBuilders.locate("structure", "minecraft:village_plains"),
                CommandBuilders.locate("biome", "minecraft:plains"),
                CommandBuilders.forceLoadAdd("0 0", null),
                CommandBuilders.forceLoadAdd("0 0", "16 16"),
                CommandBuilders.forceLoadRemove("0 0", null),
                CommandBuilders.forceLoadRemove("0 0", "16 16"),
                CommandBuilders.forceLoadRemoveAll(),
                CommandBuilders.forceLoadQueryAll(),
                CommandBuilders.forceLoadQueryPos("0 0"));
        for (String command : commands) {
            assertNotNull(d(command), "no explanation for: " + command);
        }
    }

    // ---- wording ---------------------------------------------------------------------------------

    @Test
    void give() {
        assertEquals("给予 自己 钻石 ×64", d("give @s minecraft:diamond 64"));
        assertEquals("给予 所有玩家 钻石 ×1", d("give @a minecraft:diamond 1"));
    }

    @Test
    void kill_variants() {
        assertEquals("清除 10 格内的所有实体（不含玩家）", d("kill @e[type=!minecraft:player,distance=..10]"));
        assertEquals("清除 20 格内的 僵尸", d("kill @e[type=minecraft:zombie,distance=..20]"));
        assertEquals("清除 8 格内最近的一个实体（不含玩家）",
                d("kill @e[distance=..8,sort=nearest,limit=1,type=!minecraft:player]"));
        assertEquals("清除 8 格内最近的一个 僵尸",
                d("kill @e[distance=..8,sort=nearest,limit=1,type=minecraft:zombie]"));
    }

    @Test
    void kill_customSelectorIsRestatedNotInterpreted() {
        assertEquals("清除目标：@e[tag=boss]", d("kill @e[tag=boss]"));
    }

    @Test
    void fill_showsRegionVolumeBlockAndMode() {
        assertEquals("填充 (0, 64, 0) → (9, 66, 9)（共 300 格）为 石头",
                d("fill 0 64 0 9 66 9 minecraft:stone"));
        assertEquals("填充 (0, 64, 0) → (9, 66, 9)（共 300 格）为 石头，只替换 minecraft:dirt",
                d("fill 0 64 0 9 66 9 minecraft:stone replace minecraft:dirt"));
        assertTrue(d("fill 0 64 0 9 66 9 minecraft:stone hollow").endsWith("模式：掏空（内部清空）"));
    }

    @Test
    void fill_volumeIsRightForNegativeAndReversedCorners() {
        assertTrue(d("fill 5 70 -3 -5 64 3 minecraft:stone").contains("（共 " + (11 * 7 * 7) + " 格）"));
    }

    @Test
    void blockStateIsTranslated() {
        assertEquals("在 (1, 2, 3) 放置 橡木楼梯（朝向 北，上半）",
                d("setblock 1 2 3 minecraft:oak_stairs[facing=north,half=top]"));
        assertTrue(d("setblock 1 2 3 minecraft:oak_stairs[half=bottom]").contains("下半"));
        assertTrue(d("setblock 1 2 3 minecraft:stone_slab[type=top]").contains("上半"));
    }

    @Test
    void unknownBlockFallsBackToItsId() {
        assertTrue(d("setblock 1 2 3 mymod:weird_block").contains("mymod:weird_block"));
    }

    @Test
    void teleport_variants() {
        assertEquals("把 自己 传送到 (1, 2, 3)", d("teleport @s 1 2 3"));
        assertEquals("把 自己 传送到玩家 Steve 身边", d("teleport @s Steve"));
        assertEquals("把 Steve 传送到玩家 Alex 身边", d("teleport Steve Alex"));
        assertEquals("把 Steve 传送到 (-4, 70, 12)", d("teleport Steve -4 70 12"));
    }

    @Test
    void clone_describesSizeTargetAndMode() {
        assertEquals("复制 (-1267, 73, -684) → (-1258, 78, -673)（10×6×12，共 720 格）到起点 (-1267, 78, -684)，"
                        + "复制后占 Y 78~83，替换目标方块，强制（允许重叠）",
                d("clone -1267 73 -684 -1258 78 -673 -1267 78 -684 replace force"));
    }

    @Test
    void clone_reversedCornersAreDescribedNormalised() {
        assertEquals(d("clone 0 0 0 9 5 11 20 0 0"), d("clone 9 5 11 0 0 0 20 0 0"));
    }

    @Test
    void clone_withoutOptions_hasNoModeText() {
        String text = d("clone 0 0 0 9 5 11 20 0 0");
        assertTrue(text.endsWith("复制后占 Y 0~5"), text);
    }

    @Test
    void clone_maskedAndMove() {
        assertTrue(d("clone 0 0 0 1 1 1 5 5 5 masked").contains("只复制非空气方块"));
        assertTrue(d("clone 0 0 0 1 1 1 5 5 5 replace move").contains("源区域会被清除"));
    }

    @Test
    void clone_malformedIsNotGuessedAt() {
        assertNull(d("clone 0 0 0 1 1 1 5 5"));
        assertNull(d("clone 0 0 0 1 1 1 5 5 5 nonsense"));
        assertNull(d("clone a b c 1 1 1 5 5 5"));
    }

    @Test
    void teleportToChunk_commands() {
        assertEquals("把 自己 传送到 (136, -120) 处的地面", d(CommandBuilders.teleportToSurface(136, -120)));
        assertEquals("把 自己 传送到 (136, 当前高度, -120)", d(CommandBuilders.teleportKeepingHeight(136, -120)));
    }

    @Test
    void summon() {
        assertEquals("在 (1, 2, 3) 召唤 僵尸", d("summon minecraft:zombie 1 2 3"));
        assertEquals("在 (1, 2, 3) 召唤 僵尸，朝向 90.0°",
                d("summon minecraft:zombie 1 2 3 {Rotation:[90.0f,0f]}"));
    }

    @Test
    void weatherTimeGameModeDifficulty() {
        assertEquals("把天气设为 下雨", d("weather rain"));
        assertEquals("把时间设为 白天 (1000)", d("time set day"));
        assertEquals("把时间设为 5000 刻", d("time set 5000"));
        assertEquals("时间增加 1000 刻", d("time add 1000"));
        assertEquals("查询时间：第几天", d("time query day"));
        assertEquals("把 自己 的游戏模式设为 创造模式", d("gamemode creative @s"));
        assertEquals("把世界难度设为 困难", d("difficulty hard"));
    }

    @Test
    void locate_biomeUsesItsDisplayName() {
        assertEquals("查找最近的生物群系：平原", d("locate biome minecraft:plains"));
        assertEquals("查找最近的结构：minecraft:village_plains", d("locate structure minecraft:village_plains"));
    }

    @Test
    void forceload() {
        assertEquals("强制加载方块 (0, 0) 所在的区块", d("forceload add 0 0"));
        assertEquals("取消强制加载方块 (8, 8) 所在的区块", d("forceload remove 8 8"));
        assertEquals("取消强制加载方块 (0, 0) 到 (16, 16) 之间的区块", d("forceload remove 0 0 16 16"));
        assertEquals("取消当前维度全部强制加载", d("forceload remove all"));
        assertEquals("查看已设置的强制加载区块", d("forceload query"));
        assertEquals("查询 (0, 0) 是否被强制加载", d("forceload query 0 0"));
    }

    // ---- never guess ------------------------------------------------------------------------------

    @Test
    void unrecognisedOrMalformedCommands_returnNullInsteadOfAWrongExplanation() {
        assertNull(d("say hello"));
        assertNull(d(""));
        assertNull(d(null));
        assertNull(d("fill 0 64 0 9 66 minecraft:stone"));      // too few coordinates
        assertNull(d("give @s minecraft:diamond many"));       // non-numeric count
        assertNull(d("weather blizzard"));
        assertNull(d("gamemode hardcore @s"));
    }

    @Test
    void leadingSlashIsNotExpected() {
        // History stores commands without the slash; a slash-prefixed string is not one of ours.
        assertNull(d("/kill @e[tag=x]"));
    }
}
