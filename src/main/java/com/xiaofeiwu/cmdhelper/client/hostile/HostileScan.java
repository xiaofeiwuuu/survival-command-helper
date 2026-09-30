package com.xiaofeiwu.cmdhelper.client.hostile;

import com.xiaofeiwu.cmdhelper.client.command.CommandBuilders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "look around first" half of clearing hostile mobs: given what the client can currently see,
 * work out which of the player's hostile types are actually nearby, and build one kill command for
 * each of those — and only those. Pure data in, pure data out (no Minecraft classes), so the
 * counting and the range edge are unit tested.
 */
public final class HostileScan {

    private HostileScan() {
    }

    /** One entity the client knows about: its type and squared distance from the player. */
    public record Sighting(String typeId, double distanceSq) {
    }

    /** How many of one type are within range. */
    public record Found(String typeId, int count) {
    }

    /**
     * @param types the player's hostile list, in list order — the result keeps that order
     * @return only types with at least one entity within {@code range} blocks (inclusive, matching
     *         the server's {@code distance=..N})
     */
    public static List<Found> count(Iterable<Sighting> sightings, Collection<String> types, int range) {
        double limitSq = (double) range * range;
        Map<String, Integer> counts = new HashMap<>();
        for (Sighting s : sightings) {
            if (s.distanceSq() <= limitSq && types.contains(s.typeId())) {
                counts.merge(s.typeId(), 1, Integer::sum);
            }
        }
        List<Found> found = new ArrayList<>();
        for (String type : types) {
            Integer n = counts.get(type);
            if (n != null) {
                found.add(new Found(type, n));
            }
        }
        return found;
    }

    /** One {@code kill} per found type that the player hasn't skipped for this run. */
    public static List<String> commands(List<Found> found, Set<String> skipped, int range) {
        List<String> commands = new ArrayList<>();
        for (Found f : found) {
            if (!skipped.contains(f.typeId())) {
                commands.add(CommandBuilders.killRangeType(f.typeId(), range));
            }
        }
        return commands;
    }
}
