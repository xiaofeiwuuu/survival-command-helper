package com.xiaofeiwu.cmdhelper.client.registry;

import com.xiaofeiwu.cmdhelper.client.command.CommandDescriber;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/** The game's own display names, for {@link CommandDescriber}. */
public final class RegistryNames implements CommandDescriber.Names {

    public static final RegistryNames INSTANCE = new RegistryNames();

    private RegistryNames() {
    }

    @Override
    public String entity(String id) {
        return RegistryDataSource.nameOf(RegistryEntry.Kind.ENTITY_TYPE, id);
    }

    @Override
    public String item(String id) {
        return RegistryDataSource.nameOf(RegistryEntry.Kind.ITEM, id);
    }

    @Override
    public String block(String id) {
        return RegistryDataSource.nameOf(RegistryEntry.Kind.BLOCK, id);
    }

    @Override
    public String biome(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return id;
        }
        String key = "biome." + location.getNamespace() + "." + location.getPath();
        return I18n.exists(key) ? I18n.get(key) + " [" + id + "]" : id;
    }
}
