package com.xiaofeiwu.cmdhelper.client.command;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains, in Chinese, a command this mod generated ("kill @e[type=minecraft:zombie,distance=..10]"
 * -> "清除 10 格内的 僵尸"). It reads the command text back rather than being told what each screen
 * meant: every command comes from {@link CommandBuilders}, so the shapes are fixed and one place can
 * cover all screens — and also commands saved in history before this existed, and the current
 * language when names are looked up.
 *
 * Pure (no Minecraft classes): registry names come in through {@link Names}. Returns null for any
 * command it doesn't recognise (e.g. a hand-written custom selector's exact intent), so callers show
 * the raw command alone instead of a wrong guess.
 */
public final class CommandDescriber {

    /** Display-name lookups; each falls back to the id itself when the name is unknown. */
    public interface Names {
        String entity(String id);

        String item(String id);

        String block(String id);

        String biome(String id);
    }

    private static final Map<String, String> TARGETS = Map.of(
            "@s", "自己", "@p", "最近的玩家", "@a", "所有玩家", "@r", "随机玩家");

    private static final Map<String, String> DIRECTIONS = Map.of(
            "north", "北", "south", "南", "east", "东", "west", "西", "up", "上", "down", "下");

    private static final Map<String, String> WEATHER = Map.of("clear", "晴天", "rain", "下雨", "thunder", "雷暴");

    private static final Map<String, String> TIME_SET = Map.of(
            "day", "白天 (1000)", "noon", "正午 (6000)", "night", "夜晚 (13000)", "midnight", "午夜 (18000)");

    private static final Map<String, String> TIME_QUERY = Map.of(
            "daytime", "白天时间 (0-24000 循环)", "gametime", "世界运行总刻数", "day", "第几天");

    private static final Map<String, String> GAME_MODES = Map.of(
            "survival", "生存模式", "creative", "创造模式", "adventure", "冒险模式", "spectator", "旁观模式");

    private static final Map<String, String> DIFFICULTIES = Map.of(
            "peaceful", "和平", "easy", "简单", "normal", "普通", "hard", "困难");

    private static final Map<String, String> LOCATE_KINDS = Map.of(
            "structure", "结构", "biome", "生物群系", "poi", "兴趣点");

    private static final Pattern KILL_ALL = Pattern.compile("kill @e\\[type=!minecraft:player,distance=\\.\\.(\\d+)]");
    private static final Pattern KILL_TYPE = Pattern.compile("kill @e\\[type=([^,\\]]+),distance=\\.\\.(\\d+)]");
    private static final Pattern KILL_NEAREST_ANY =
            Pattern.compile("kill @e\\[distance=\\.\\.(\\d+),sort=nearest,limit=1,type=!minecraft:player]");
    private static final Pattern KILL_NEAREST_TYPE =
            Pattern.compile("kill @e\\[distance=\\.\\.(\\d+),sort=nearest,limit=1,type=([^,\\]]+)]");
    private static final Pattern TELEPORT_TO_SURFACE = Pattern.compile(
            "execute positioned (-?\\d+) 0 (-?\\d+) positioned over [a-z_]+ run tp @s ~ ~ ~");
    private static final Pattern SUMMON_ROTATION = Pattern.compile("\\{Rotation:\\[([-\\d.eE]+)f,([-\\d.eE]+)f]}");

    private CommandDescriber() {
    }

    /** @param command the command without its leading slash */
    public static String describe(String command, Names names) {
        if (command == null) {
            return null;
        }
        String trimmed = command.trim();
        String[] t = trimmed.split("\\s+");
        if (t.length == 0 || t[0].isEmpty()) {
            return null;
        }
        try {
            return switch (t[0]) {
                case "give" -> t.length == 4 ? give(t, names) : null;
                case "kill" -> kill(trimmed, t, names);
                case "fill" -> fill(t, names);
                case "clone" -> clone(t);
                case "setblock" -> setBlock(t, names);
                case "teleport", "tp" -> teleport(t);
                case "execute" -> execute(trimmed);
                case "summon" -> summon(t, names);
                case "weather" -> weather(t);
                case "time" -> time(t);
                case "gamemode" -> t.length == 3 && GAME_MODES.containsKey(t[1])
                        ? "把 " + target(t[2]) + " 的游戏模式设为 " + GAME_MODES.get(t[1]) : null;
                case "difficulty" -> t.length == 2 && DIFFICULTIES.containsKey(t[1])
                        ? "把世界难度设为 " + DIFFICULTIES.get(t[1]) : null;
                case "locate" -> locate(t, names);
                case "forceload" -> forceLoad(t);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- per-command ---------------------------------------------------------------------------

    private static String give(String[] t, Names names) {
        return "给予 " + target(t[1]) + " " + names.item(t[2]) + " ×" + Integer.parseInt(t[3]);
    }

    private static String kill(String whole, String[] t, Names names) {
        Matcher m = KILL_ALL.matcher(whole);
        if (m.matches()) {
            return "清除 " + m.group(1) + " 格内的所有实体（不含玩家）";
        }
        m = KILL_TYPE.matcher(whole);
        if (m.matches()) {
            return "清除 " + m.group(2) + " 格内的 " + names.entity(m.group(1));
        }
        m = KILL_NEAREST_ANY.matcher(whole);
        if (m.matches()) {
            return "清除 " + m.group(1) + " 格内最近的一个实体（不含玩家）";
        }
        m = KILL_NEAREST_TYPE.matcher(whole);
        if (m.matches()) {
            return "清除 " + m.group(1) + " 格内最近的一个 " + names.entity(m.group(2));
        }
        // A hand-written selector: we can only restate it, not know what it's for.
        return t.length == 2 ? "清除目标：" + t[1] : null;
    }

    private static String fill(String[] t, Names names) {
        if (t.length < 8) {
            return null;
        }
        int[] a = {Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])};
        int[] b = {Integer.parseInt(t[4]), Integer.parseInt(t[5]), Integer.parseInt(t[6])};
        long volume = RegionBounds.of(t[1] + " " + t[2] + " " + t[3], t[4] + " " + t[5] + " " + t[6]).volume();
        StringBuilder out = new StringBuilder("填充 ").append(coords(a)).append(" → ").append(coords(b))
                .append("（共 ").append(volume).append(" 格）为 ").append(blockWithState(t[7], names));
        if (t.length >= 9) {
            if (t[8].equals("replace") && t.length >= 10) {
                out.append("，只替换 ").append(blockWithState(t[9], names));
            } else {
                out.append("，模式：").append(FillModeLabels.labelFor(t[8]));
            }
        }
        return out.toString();
    }

    /** clone x1 y1 z1 x2 y2 z2 tx ty tz [replace|masked] [normal|force|move] */
    private static String clone(String[] t) {
        if (t.length < 10 || t.length > 12) {
            return null;
        }
        int[] n = new int[9];
        for (int i = 0; i < 9; i++) {
            n[i] = Integer.parseInt(t[i + 1]);
        }
        RegionBounds source = RegionBounds.of(n[0] + " " + n[1] + " " + n[2], n[3] + " " + n[4] + " " + n[5]);
        // The destination is the lowest corner of where the copy lands, with the source's size.
        RegionBounds copy = new RegionBounds(n[6], n[7], n[8],
                n[6] + source.sizeX() - 1, n[7] + source.sizeY() - 1, n[8] + source.sizeZ() - 1);
        StringBuilder out = new StringBuilder("复制 ")
                .append(coords(new int[]{source.minX(), source.minY(), source.minZ()})).append(" → ")
                .append(coords(new int[]{source.maxX(), source.maxY(), source.maxZ()}))
                .append("（").append(source.sizeX()).append("×").append(source.sizeY()).append("×").append(source.sizeZ())
                .append("，共 ").append(source.volume()).append(" 格）到起点 ")
                .append(coords(new int[]{n[6], n[7], n[8]}))
                .append("，复制后占 Y ").append(copy.minY()).append("~").append(copy.maxY());
        for (int i = 10; i < t.length; i++) {
            switch (t[i]) {
                case "replace" -> out.append("，替换目标方块");
                case "masked" -> out.append("，只复制非空气方块");
                case "normal" -> out.append("，普通模式");
                case "force" -> out.append("，强制（允许重叠）");
                case "move" -> out.append("，移动（源区域会被清除）");
                default -> {
                    return null;
                }
            }
        }
        return out.toString();
    }

    private static String setBlock(String[] t, Names names) {
        if (t.length < 5) {
            return null;
        }
        int[] p = {Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])};
        StringBuilder out = new StringBuilder("在 ").append(coords(p)).append(" 放置 ").append(blockWithState(t[4], names));
        if (t.length >= 6) {
            out.append("，模式：").append(FillModeLabels.labelFor(t[5]));
        }
        return out.toString();
    }

    private static String execute(String whole) {
        Matcher m = TELEPORT_TO_SURFACE.matcher(whole);
        if (m.matches()) {
            return "把 自己 传送到 (" + Integer.parseInt(m.group(1)) + ", " + Integer.parseInt(m.group(2)) + ") 处的地面";
        }
        return null;
    }

    private static String teleport(String[] t) {
        if (t.length == 5 && t[3].equals("~") && isInteger(t[2]) && isInteger(t[4])) {
            return "把 " + target(t[1]) + " 传送到 (" + Integer.parseInt(t[2]) + ", 当前高度, " + Integer.parseInt(t[4]) + ")";
        }
        if (t.length == 5 && isInteger(t[2]) && isInteger(t[3]) && isInteger(t[4])) {
            int[] p = {Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4])};
            return "把 " + target(t[1]) + " 传送到 " + coords(p);
        }
        if (t.length == 3) {
            return "把 " + target(t[1]) + " 传送到玩家 " + t[2] + " 身边";
        }
        return null;
    }

    private static String summon(String[] t, Names names) {
        if (t.length < 5) {
            return null;
        }
        int[] p = {Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4])};
        StringBuilder out = new StringBuilder("在 ").append(coords(p)).append(" 召唤 ").append(names.entity(t[1]));
        if (t.length >= 6) {
            Matcher m = SUMMON_ROTATION.matcher(t[5]);
            if (m.matches()) {
                out.append("，朝向 ").append(m.group(1)).append("°");
            }
        }
        return out.toString();
    }

    private static String weather(String[] t) {
        if (t.length < 2 || !WEATHER.containsKey(t[1])) {
            return null;
        }
        return "把天气设为 " + WEATHER.get(t[1]) + (t.length >= 3 ? "，持续 " + t[2] + " 秒" : "");
    }

    private static String time(String[] t) {
        if (t.length != 3) {
            return null;
        }
        return switch (t[1]) {
            case "set" -> "把时间设为 " + TIME_SET.getOrDefault(t[2], t[2] + " 刻");
            case "add" -> "时间增加 " + Integer.parseInt(t[2]) + " 刻";
            case "query" -> "查询时间：" + TIME_QUERY.getOrDefault(t[2], t[2]);
            default -> null;
        };
    }

    private static String locate(String[] t, Names names) {
        if (t.length != 3 || !LOCATE_KINDS.containsKey(t[1])) {
            return null;
        }
        String subject = t[1].equals("biome") ? names.biome(t[2]) : t[2];
        return "查找最近的" + LOCATE_KINDS.get(t[1]) + "：" + subject;
    }

    private static String forceLoad(String[] t) {
        if (t.length < 2) {
            return null;
        }
        switch (t[1]) {
            case "add", "remove" -> {
                String verb = t[1].equals("add") ? "强制加载" : "取消强制加载";
                if (t.length == 3 && t[2].equals("all")) {
                    return "取消当前维度全部强制加载";
                }
                // The coordinates are block positions; the command acts on the chunks containing them.
                if (t.length == 4) {
                    return verb + "方块 " + column(t[2], t[3]) + " 所在的区块";
                }
                if (t.length == 6) {
                    return verb + "方块 " + column(t[2], t[3]) + " 到 " + column(t[4], t[5]) + " 之间的区块";
                }
                return null;
            }
            case "query" -> {
                if (t.length == 2) {
                    return "查看已设置的强制加载区块";
                }
                return t.length == 4 ? "查询 " + column(t[2], t[3]) + " 是否被强制加载" : null;
            }
            default -> {
                return null;
            }
        }
    }

    // ---- pieces --------------------------------------------------------------------------------

    private static String target(String selector) {
        return TARGETS.getOrDefault(selector, selector);
    }

    private static String coords(int[] p) {
        return "(" + p[0] + ", " + p[1] + ", " + p[2] + ")";
    }

    private static String column(String x, String z) {
        return "(" + Integer.parseInt(x) + ", " + Integer.parseInt(z) + ")";
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** "minecraft:oak_stairs[facing=north,half=top]" -> "橡木楼梯（朝向 北，上半）". */
    private static String blockWithState(String blockAndState, Names names) {
        int bracket = blockAndState.indexOf('[');
        if (bracket < 0 || !blockAndState.endsWith("]")) {
            return names.block(blockAndState);
        }
        String name = names.block(blockAndState.substring(0, bracket));
        StringBuilder state = new StringBuilder();
        for (String pair : blockAndState.substring(bracket + 1, blockAndState.length() - 1).split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (state.length() > 0) {
                state.append("，");
            }
            state.append(stateText(kv[0], kv[1]));
        }
        return state.length() == 0 ? name : name + "（" + state + "）";
    }

    private static String stateText(String key, String value) {
        if (key.equals("facing")) {
            return "朝向 " + DIRECTIONS.getOrDefault(value, value);
        }
        if ((key.equals("half") || key.equals("type")) && (value.equals("top") || value.equals("upper"))) {
            return "上半";
        }
        if ((key.equals("half") || key.equals("type")) && (value.equals("bottom") || value.equals("lower"))) {
            return "下半";
        }
        return key + "=" + value;
    }
}
