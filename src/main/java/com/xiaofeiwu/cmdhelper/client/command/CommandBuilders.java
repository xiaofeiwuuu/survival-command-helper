package com.xiaofeiwu.cmdhelper.client.command;

/**
 * Pure string formatting for every command this mod can generate. Kept free of any
 * Minecraft/Forge/widget class so it can be unit tested without booting the game;
 * screens only decide *whether* enough input exists, this decides *what the command
 * text looks like*.
 */
public final class CommandBuilders {

    private CommandBuilders() {
    }

    public static String give(String target, String itemId, int count, int min, int max) {
        int clamped = Math.max(min, Math.min(max, count));
        return "give " + target + " " + itemId + " " + clamped;
    }

    // @e matches every entity by default, players included — without an explicit type,
    // "kill everything nearby" and "kill the nearest thing" would otherwise kill the very
    // player who ran the command (they're always within their own 0-block radius, and
    // always the "nearest" entity to themselves).
    private static final String EXCLUDE_PLAYERS = "type=!minecraft:player";

    public static String killRangeAll(int range) {
        return "kill @e[" + EXCLUDE_PLAYERS + ",distance=.." + range + "]";
    }

    public static String killRangeType(String entityId, int range) {
        return "kill @e[type=" + entityId + ",distance=.." + range + "]";
    }

    public static String killNearest(String entityIdOrNull, int range) {
        String typePart = (entityIdOrNull == null || entityIdOrNull.isEmpty()) ? "," + EXCLUDE_PLAYERS : ",type=" + entityIdOrNull;
        return "kill @e[distance=.." + range + ",sort=nearest,limit=1" + typePart + "]";
    }

    /** Null when the selector looks well-formed; otherwise what's wrong, in words for the player.
     *  Only checks what's cheap and certain (empty, unbalanced brackets) — the server has the
     *  final say on everything else. */
    public static String selectorProblem(String rawSelector) {
        String raw = rawSelector == null ? "" : rawSelector.trim();
        if (raw.isEmpty()) {
            return "请输入目标选择器，例如 @e[tag=boss]";
        }
        int depth = 0;
        for (char c : raw.toCharArray()) {
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth < 0) {
                    return "选择器里多了一个 ]";
                }
            }
        }
        return depth == 0 ? null : "选择器的中括号没有闭合，例如 @e[tag=boss]";
    }

    public static String killCustom(String rawSelector) {
        return "kill " + rawSelector;
    }

    public static String fill(String fromCoords, String toCoords, String blockId, String modeOrNull) {
        String modePart = (modeOrNull == null || modeOrNull.isEmpty()) ? "" : " " + modeOrNull;
        return "fill " + fromCoords + " " + toCoords + " " + blockId + modePart;
    }

    /** @param optionsOrNull the trailing "[replace|masked] [force|move]" text, already worded, or null */
    public static String clone(String begin, String end, String destination, String optionsOrNull) {
        String tail = (optionsOrNull == null || optionsOrNull.isEmpty()) ? "" : " " + optionsOrNull;
        return "clone " + begin + " " + end + " " + destination + tail;
    }

    public static String setBlock(String posCoords, String blockId, String modeOrNull) {
        String modePart = (modeOrNull == null || modeOrNull.isEmpty()) ? "" : " " + modeOrNull;
        return "setblock " + posCoords + " " + blockId + modePart;
    }

    public static String teleportSelfToCoords(String coords) {
        return "teleport @s " + coords;
    }

    /**
     * Teleports to the surface at a block column. A chunk has no height, so the server works it out:
     * "positioned over" sets Y to the terrain height there (never inside a hill, never in mid-air).
     * The chunk has to be loaded on the server — a force-loaded one always is. Doesn't work under a
     * bedrock ceiling (the Nether), where the "surface" is the roof: use {@link #teleportKeepingHeight}.
     */
    public static String teleportToSurface(int blockX, int blockZ) {
        return "execute positioned " + blockX + " 0 " + blockZ
                + " positioned over motion_blocking_no_leaves run tp @s ~ ~ ~";
    }

    /** Plain teleport to an exact spot. */
    public static String teleportToHeight(int blockX, int blockY, int blockZ) {
        return "teleport @s " + blockX + " " + blockY + " " + blockZ;
    }

    /** Moves sideways only; Y stays whatever it is now (for dimensions with no reachable surface). */
    public static String teleportKeepingHeight(int blockX, int blockZ) {
        return "teleport @s " + blockX + " ~ " + blockZ;
    }

    public static String teleportSelfToPlayer(String destinationPlayer) {
        return "teleport @s " + destinationPlayer;
    }

    public static String teleportPlayerToPlayer(String targetPlayer, String destinationPlayer) {
        return "teleport " + targetPlayer + " " + destinationPlayer;
    }

    public static String teleportPlayerToCoords(String targetPlayer, String coords) {
        return "teleport " + targetPlayer + " " + coords;
    }

    public static String summon(String entityId, String coords, String yawOrNull, String pitchOrNull) {
        StringBuilder cmd = new StringBuilder("summon ").append(entityId).append(' ').append(coords);
        if (yawOrNull != null && !yawOrNull.isEmpty() && pitchOrNull != null && !pitchOrNull.isEmpty()) {
            cmd.append(" {Rotation:[").append(yawOrNull).append("f,").append(pitchOrNull).append("f]}");
        }
        return cmd.toString();
    }

    public static String weather(String type, String durationSecondsOrNull) {
        String durPart = (durationSecondsOrNull == null || durationSecondsOrNull.isEmpty()) ? "" : " " + durationSecondsOrNull;
        return "weather " + type + durPart;
    }

    public static String timeSet(String dayPresetOrNumber) {
        return "time set " + dayPresetOrNumber;
    }

    public static String timeAdd(String amountTicks) {
        return "time add " + amountTicks;
    }

    public static String timeQuery(String type) {
        return "time query " + type;
    }

    public static String gameMode(String mode, String target) {
        return "gamemode " + mode + " " + target;
    }

    public static String difficulty(String level) {
        return "difficulty " + level;
    }

    public static String locate(String type, String id) {
        return "locate " + type + " " + id;
    }

    public static String forceLoadAdd(String from, String toOrNull) {
        return "forceload add " + from + (toOrNull == null || toOrNull.isEmpty() ? "" : " " + toOrNull);
    }

    public static String forceLoadRemove(String from, String toOrNull) {
        return "forceload remove " + from + (toOrNull == null || toOrNull.isEmpty() ? "" : " " + toOrNull);
    }

    public static String forceLoadRemoveAll() {
        return "forceload remove all";
    }

    public static String forceLoadQueryAll() {
        return "forceload query";
    }

    public static String forceLoadQueryPos(String pos) {
        return "forceload query " + pos;
    }
}
