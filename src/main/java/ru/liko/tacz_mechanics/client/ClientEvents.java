package ru.liko.tacz_mechanics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimClientCache;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;
import ru.liko.tacz_mechanics.client.freeaim.RecoilSource;
import ru.liko.tacz_mechanics.client.sound.SoundFilterRegistry;
import ru.liko.tacz_mechanics.client.sound.SoundSourceTracker;
import ru.liko.tacz_mechanics.client.deafen.TinnitusHandler;
import ru.liko.tacz_mechanics.client.recoil.RecoilDrift;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;

@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SoundFilterRegistry.tick();
        FreeAimHandler.getInstance().tick();
        SuppressionHandler.tick();
        TinnitusHandler.tick();
        RecoilDrift.tick();
    }

    @SubscribeEvent
    public static void onLocalConfigLoaded(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == Config.SERVER_SPEC) {
            ClientTweakSettings.applyFromLocalConfig();
            ClientDistantFireSettings.applyFromLocalConfig();
        }
    }

    @SubscribeEvent
    public static void onDisconnectFromServer(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTweakSettings.applyFromLocalConfig();
        ClientDistantFireSettings.applyFromLocalConfig();
        SoundFilterRegistry.cleanup();
        SoundSourceTracker.cleanup();
        FreeAimClientCache.clear();
        RecoilSource.clearCache();
        TinnitusHandler.reset();
        RecoilDrift.reset();
    }
}
