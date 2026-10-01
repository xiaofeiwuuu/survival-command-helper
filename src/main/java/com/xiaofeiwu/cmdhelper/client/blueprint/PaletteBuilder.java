package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.PaletteEntry;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPlacer.Pass;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out, for this game and a given rotation/mirror, what each blueprint palette entry will be:
 * which exist here (a block from a mod that isn't installed doesn't), what they look like once turned,
 * and in which pass they should be placed. Also keeps what the "did it come out right?" check needs.
 */
public final class PaletteBuilder {

    private PaletteBuilder() {
    }

    /**
     * @param entries         one per palette entry, in the blueprint's order
     * @param blockIds        the registry id of each entry's block, or -1 if it isn't available here
     * @param missingBlocks   block ids that don't exist in this game
     * @param missingMods     the namespaces of those that belong to a mod that isn't loaded
     * @param degradedStates  how many entries had a property this game's version of the block doesn't know
     */
    public record Prepared(List<PaletteEntry> entries, int[] blockIds, Set<String> missingBlocks,
                           Set<String> missingMods, int degradedStates) {
    }

    public static Prepared prepare(Blueprint blueprint, Transform transform) {
        List<PaletteEntry> entries = new ArrayList<>();
        int[] blockIds = new int[blueprint.palette().size()];
        Set<String> missingBlocks = new LinkedHashSet<>();
        Set<String> missingMods = new LinkedHashSet<>();
        int degraded = 0;
        for (int i = 0; i < blueprint.palette().size(); i++) {
            String text = blueprint.palette().get(i);
            StateStrings.Parsed parsed = StateStrings.parse(text);
            if (!parsed.exists()) {
                entries.add(new PaletteEntry(text, false, Pass.SOLID));
                blockIds[i] = -1;
                String id = Blueprint.blockId(text);
                missingBlocks.add(id);
                int colon = id.indexOf(':');
                String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
                if (!ModList.get().isLoaded(namespace)) {
                    missingMods.add(namespace);
                }
                continue;
            }
            if (parsed.degraded()) {
                degraded++;
            }
            BlockState state = StateStrings.transform(parsed.state(), transform);
            Pass pass = state.isAir() ? Pass.CLEAR : (state.canOcclude() ? Pass.SOLID : Pass.ATTACHED);
            entries.add(new PaletteEntry(StateStrings.minimal(state), true, pass));
            blockIds[i] = BuiltInRegistries.BLOCK.getId(state.getBlock());
        }
        return new Prepared(entries, blockIds, missingBlocks, missingMods, degraded);
    }
}
