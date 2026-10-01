package com.xiaofeiwu.cmdhelper;

import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintExecutor;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintPreview;
import com.xiaofeiwu.cmdhelper.client.blueprint.BlueprintScanner;
import com.xiaofeiwu.cmdhelper.client.command.ChatResultCapture;
import com.xiaofeiwu.cmdhelper.client.preview.FillPreview;
import com.xiaofeiwu.cmdhelper.client.registry.RegistryDataSource;
import com.xiaofeiwu.cmdhelper.client.screen.MainMenuScreen;
import com.xiaofeiwu.cmdhelper.client.teleport.SafeTeleport;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod(CmdHelperMod.MODID)
public class CmdHelperMod {

    public static final String MODID = "cmdhelper";

    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
            "key.cmdhelper.open_menu",
            GLFW.GLFW_KEY_K,
            "key.categories.cmdhelper"
    );

    public CmdHelperMod(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::registerKeyMappings);
        context.getModEventBus().addListener(this::registerReloadListeners);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ChatResultCapture.class);
        MinecraftForge.EVENT_BUS.register(FillPreview.class);
        MinecraftForge.EVENT_BUS.register(SafeTeleport.class);
        MinecraftForge.EVENT_BUS.register(BlueprintScanner.class);
        MinecraftForge.EVENT_BUS.register(BlueprintPreview.class);
        MinecraftForge.EVENT_BUS.register(BlueprintExecutor.class);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU_KEY);
        event.register(BlueprintPreview.ROTATE_KEY);
        event.register(BlueprintPreview.MIRROR_KEY);
        event.register(BlueprintPreview.UP_KEY);
        event.register(BlueprintPreview.DOWN_KEY);
    }

    // Item/block/entity display names are looked up once and cached; switching language or
    // reloading resource packs would otherwise leave the old language's names in the pickers.
    private void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> RegistryDataSource.invalidate());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_MENU_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MainMenuScreen());
            }
        }
    }
}
