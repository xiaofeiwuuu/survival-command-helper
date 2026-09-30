package com.xiaofeiwu.cmdhelper.client.locate;

import java.util.Map;

/**
 * Chinese names for vanilla structures and points of interest. The game's own language files carry
 * no display names for either (checked against 1.20.1: no structure keys at all; the only reusable
 * ones are the villager professions), so there is nothing to look up — the names have to ship here.
 * Biomes need no table: the game translates those itself.
 */
public final class VanillaLocateNames {

    private VanillaLocateNames() {
    }

    public static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("minecraft:ancient_city", "远古城市"),
            Map.entry("minecraft:bastion_remnant", "堡垒遗迹"),
            Map.entry("minecraft:buried_treasure", "埋藏的宝藏"),
            Map.entry("minecraft:desert_pyramid", "沙漠神殿"),
            Map.entry("minecraft:end_city", "末地城"),
            Map.entry("minecraft:fortress", "下界要塞"),
            Map.entry("minecraft:igloo", "雪屋"),
            Map.entry("minecraft:jungle_pyramid", "丛林神庙"),
            Map.entry("minecraft:mansion", "林地府邸"),
            Map.entry("minecraft:mineshaft", "废弃矿井"),
            Map.entry("minecraft:mineshaft_mesa", "恶地废弃矿井"),
            Map.entry("minecraft:monument", "海底神殿"),
            Map.entry("minecraft:nether_fossil", "下界化石"),
            Map.entry("minecraft:ocean_ruin_cold", "冷水海洋遗迹"),
            Map.entry("minecraft:ocean_ruin_warm", "暖水海洋遗迹"),
            Map.entry("minecraft:pillager_outpost", "掠夺者前哨站"),
            Map.entry("minecraft:ruined_portal", "废弃传送门"),
            Map.entry("minecraft:ruined_portal_desert", "废弃传送门（沙漠）"),
            Map.entry("minecraft:ruined_portal_jungle", "废弃传送门（丛林）"),
            Map.entry("minecraft:ruined_portal_mountain", "废弃传送门（山地）"),
            Map.entry("minecraft:ruined_portal_nether", "废弃传送门（下界）"),
            Map.entry("minecraft:ruined_portal_ocean", "废弃传送门（海洋）"),
            Map.entry("minecraft:ruined_portal_swamp", "废弃传送门（沼泽）"),
            Map.entry("minecraft:shipwreck", "沉船"),
            Map.entry("minecraft:shipwreck_beached", "搁浅的沉船"),
            Map.entry("minecraft:stronghold", "要塞"),
            Map.entry("minecraft:swamp_hut", "沼泽小屋"),
            Map.entry("minecraft:trail_ruins", "古迹废墟"),
            Map.entry("minecraft:village_desert", "沙漠村庄"),
            Map.entry("minecraft:village_plains", "平原村庄"),
            Map.entry("minecraft:village_savanna", "热带草原村庄"),
            Map.entry("minecraft:village_snowy", "雪原村庄"),
            Map.entry("minecraft:village_taiga", "针叶林村庄")
    );

    // A POI type is the block a villager (or a bee, a portal...) is attracted to; the label names
    // the thing it stands for and, where useful, the block.
    public static final Map<String, String> POI_TYPES = Map.ofEntries(
            Map.entry("minecraft:armorer", "盔甲匠（高炉）"),
            Map.entry("minecraft:butcher", "屠夫（烟熏炉）"),
            Map.entry("minecraft:cartographer", "制图师（制图台）"),
            Map.entry("minecraft:cleric", "牧师（炼药锅）"),
            Map.entry("minecraft:farmer", "农民（堆肥桶）"),
            Map.entry("minecraft:fisherman", "渔夫（桶）"),
            Map.entry("minecraft:fletcher", "制箭师（制箭台）"),
            Map.entry("minecraft:leatherworker", "皮匠（炼药锅）"),
            Map.entry("minecraft:librarian", "图书管理员（讲台）"),
            Map.entry("minecraft:mason", "石匠（切石机）"),
            Map.entry("minecraft:shepherd", "牧羊人（织布机）"),
            Map.entry("minecraft:toolsmith", "工具匠（砂轮）"),
            Map.entry("minecraft:weaponsmith", "武器匠（砂轮）"),
            Map.entry("minecraft:home", "床（村民的家）"),
            Map.entry("minecraft:meeting", "钟（村庄集合点）"),
            Map.entry("minecraft:nether_portal", "下界传送门"),
            Map.entry("minecraft:lodestone", "磁石"),
            Map.entry("minecraft:beehive", "蜂箱"),
            Map.entry("minecraft:bee_nest", "蜂巢"),
            Map.entry("minecraft:lightning_rod", "避雷针")
    );
}
