package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBuildersTest {

    @Test
    void give_formatsBasicCommand() {
        assertEquals("give @s minecraft:diamond 1",
                CommandBuilders.give("@s", "minecraft:diamond", 1, 1, 99));
    }

    @Test
    void give_clampsCountAboveMax() {
        // the server told us 99 is the real ceiling for /give's count argument, not our own guess of 64
        assertEquals("give @s minecraft:diamond 99",
                CommandBuilders.give("@s", "minecraft:diamond", 500, 1, 99));
    }

    @Test
    void give_clampsCountBelowMin() {
        assertEquals("give @s minecraft:diamond 1",
                CommandBuilders.give("@s", "minecraft:diamond", 0, 1, 99));
    }

    @Test
    void killRangeAll_excludesPlayers() {
        // @e includes players by default; the executing player is always within their own
        // 0-block radius, so a naive "kill everything nearby" would kill them too.
        assertEquals("kill @e[type=!minecraft:player,distance=..10]", CommandBuilders.killRangeAll(10));
    }

    @Test
    void killRangeType_includesTypeAndDistance() {
        assertEquals("kill @e[type=xxxmod:royal_creeper,distance=..20]",
                CommandBuilders.killRangeType("xxxmod:royal_creeper", 20));
    }

    @Test
    void killNearest_withoutType_excludesPlayers() {
        // Otherwise "nearest entity" with no type filter always resolves to the player
        // themselves — distance 0 to yourself always wins a nearest-sort.
        assertEquals("kill @e[distance=..10,sort=nearest,limit=1,type=!minecraft:player]",
                CommandBuilders.killNearest(null, 10));
    }

    @Test
    void killNearest_withType_appendsTypeFilter() {
        assertEquals("kill @e[distance=..10,sort=nearest,limit=1,type=minecraft:zombie]",
                CommandBuilders.killNearest("minecraft:zombie", 10));
    }

    @Test
    void killCustom_passesSelectorThrough() {
        assertEquals("kill @e[tag=boss]", CommandBuilders.killCustom("@e[tag=boss]"));
    }

    @Test
    void fill_withoutMode_omitsTrailingSpace() {
        assertEquals("fill 0 0 0 10 10 10 minecraft:stone",
                CommandBuilders.fill("0 0 0", "10 10 10", "minecraft:stone", null));
    }

    @Test
    void fill_withMode_appendsIt() {
        assertEquals("fill 0 0 0 10 10 10 minecraft:stone keep",
                CommandBuilders.fill("0 0 0", "10 10 10", "minecraft:stone", "keep"));
    }

    @Test
    void setBlock_withMode_appendsIt() {
        assertEquals("setblock 1 2 3 minecraft:stone destroy",
                CommandBuilders.setBlock("1 2 3", "minecraft:stone", "destroy"));
    }

    @Test
    void teleportSelfToCoords_targetsSelf() {
        assertEquals("teleport @s 1 2 3", CommandBuilders.teleportSelfToCoords("1 2 3"));
    }

    @Test
    void teleportPlayerToPlayer_usesBothNames() {
        assertEquals("teleport Steve Alex", CommandBuilders.teleportPlayerToPlayer("Steve", "Alex"));
    }

    @Test
    void summon_withoutRotation_omitsNbt() {
        assertEquals("summon minecraft:zombie 1 2 3",
                CommandBuilders.summon("minecraft:zombie", "1 2 3", "", ""));
    }

    @Test
    void summon_withRotation_appendsNbtRotationTag() {
        assertEquals("summon minecraft:zombie 1 2 3 {Rotation:[90f,0f]}",
                CommandBuilders.summon("minecraft:zombie", "1 2 3", "90", "0"));
    }

    @Test
    void weather_withoutDuration_omitsTrailingSpace() {
        assertEquals("weather clear", CommandBuilders.weather("clear", ""));
    }

    @Test
    void weather_withDuration_appendsSeconds() {
        assertEquals("weather thunder 600", CommandBuilders.weather("thunder", "600"));
    }

    @Test
    void timeSet_passesPresetOrNumberThrough() {
        assertEquals("time set day", CommandBuilders.timeSet("day"));
        assertEquals("time set 6000", CommandBuilders.timeSet("6000"));
    }

    @Test
    void timeAdd_formatsAmount() {
        assertEquals("time add 1000", CommandBuilders.timeAdd("1000"));
    }

    @Test
    void timeQuery_formatsType() {
        assertEquals("time query daytime", CommandBuilders.timeQuery("daytime"));
    }

    @Test
    void teleportToSurface_letsTheServerFindTheGround() {
        assertEquals("execute positioned 40 0 -8 positioned over motion_blocking_no_leaves run tp @s ~ ~ ~",
                CommandBuilders.teleportToSurface(40, -8));
    }

    @Test
    void teleportToSurface_fitsInTheCommandPacketLimit() {
        // A 1.20.1 command packet is capped at 256 characters.
        assertTrue(CommandBuilders.teleportToSurface(-30000000, -30000000).length() < 256);
    }

    @Test
    void teleportToHeight_isAPlainTeleport() {
        assertEquals("teleport @s -736 320 -720", CommandBuilders.teleportToHeight(-736, 320, -720));
    }

    @Test
    void teleportKeepingHeight_usesRelativeY() {
        assertEquals("teleport @s 40 ~ -8", CommandBuilders.teleportKeepingHeight(40, -8));
    }

    @Test
    void selectorProblem_acceptsWellFormedSelectors() {
        assertNull(CommandBuilders.selectorProblem("@e[tag=boss]"));
        assertNull(CommandBuilders.selectorProblem("@a"));
        assertNull(CommandBuilders.selectorProblem("Steve"));
    }

    @Test
    void selectorProblem_rejectsEmpty() {
        assertNotNull(CommandBuilders.selectorProblem(""));
        assertNotNull(CommandBuilders.selectorProblem("   "));
        assertNotNull(CommandBuilders.selectorProblem(null));
    }

    @Test
    void selectorProblem_rejectsTheUnfinishedDefaultValue() {
        // The kill screen pre-fills "@e[" — sending that as-is is always a server error.
        assertNotNull(CommandBuilders.selectorProblem("@e["));
    }

    @Test
    void selectorProblem_rejectsStrayClosingBracket() {
        assertNotNull(CommandBuilders.selectorProblem("@e]tag=x["));
    }
}
