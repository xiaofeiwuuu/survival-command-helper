package com.xiaofeiwu.cmdhelper.client.hostile;

import com.xiaofeiwu.cmdhelper.client.command.CommandExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks at what the client can currently see and clears the hostile mobs from the player's list —
 * shared by the main menu's one-click button and the kill screen's live preview, so both always
 * agree on what "nearby hostile mobs" means. The counting itself is {@link HostileScan}.
 */
public final class HostileClear {

    private HostileClear() {
    }

    /** The hostile list resolved against this game's registries: the entries a mod no longer provides drop out. */
    public record Resolved(Map<EntityType<?>, String> idByType, List<String> ids, Map<String, String> names) {

        public String nameOf(String id) {
            return names.getOrDefault(id, id);
        }
    }

    public static Resolved resolve() {
        Map<EntityType<?>, String> idByType = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (String id : HostileTypes.shared().ids()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location != null && ForgeRegistries.ENTITY_TYPES.containsKey(location)) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(location);
                idByType.put(type, id);
                names.put(id, type.getDescription().getString());
            }
        }
        return new Resolved(idByType, new ArrayList<>(idByType.values()), names);
    }

    /** Hostile-list mobs within {@code range} blocks of the player that the client has loaded. */
    public static List<HostileScan.Found> scan(Resolved hostile, int range) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || hostile.idByType().isEmpty()) {
            return List.of();
        }
        List<HostileScan.Sighting> sightings = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            String id = hostile.idByType().get(entity.getType());
            if (id != null && entity.isAlive()) {
                sightings.add(new HostileScan.Sighting(id, entity.distanceToSqr(mc.player)));
            }
        }
        return HostileScan.count(sightings, hostile.ids(), range);
    }

    /** "僵尸×5、骷髅×2" for the types that will actually be killed (i.e. not skipped). */
    public static String describe(Resolved hostile, List<HostileScan.Found> found, Set<String> skipped) {
        List<String> parts = new ArrayList<>();
        for (HostileScan.Found f : found) {
            if (!skipped.contains(f.typeId())) {
                parts.add(hostile.nameOf(f.typeId()) + "×" + f.count());
            }
        }
        return String.join("、", parts);
    }

    /**
     * The main menu's one-click action: scan with the saved range, and send one kill per hostile
     * type that is actually nearby. Reports on the action bar when there's nothing to do.
     */
    public static void clearNow() {
        Resolved hostile = resolve();
        int range = HostileTypes.shared().range();
        if (hostile.ids().isEmpty()) {
            tell("敌对名单是空的：到「杀死生物」→「当前范围内敌对生物」里添加", ChatFormatting.RED);
            return;
        }
        List<HostileScan.Found> found = scan(hostile, range);
        List<String> commands = HostileScan.commands(found, Set.of(), range);
        if (commands.isEmpty()) {
            tell("范围 " + range + " 格内没有名单里的敌对生物", ChatFormatting.GRAY);
            return;
        }
        CommandExecutor.executeBatch(commands, "清除敌对生物：" + describe(hostile, found, Set.of()));
    }

    private static void tell(String text, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[指令助手] " + text).withStyle(color), true);
        }
    }
}
