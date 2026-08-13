package ru.liko.tacz_mechanics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimClientCache;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;
import ru.liko.tacz_mechanics.client.freeaim.RecoilSource;
import ru.liko.tacz_mechanics.client.deafen.TinnitusHandler;
import ru.liko.tacz_mechanics.client.recoil.RecoilDrift;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;

@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        FreeAimHandler.getInstance().tick();
        SuppressionHandler.tick();
        TinnitusHandler.tick();
        RecoilDrift.tick();
    }

    /**
     * Каждый новый звук проходит через контузию. Раньше это делал наш @Inject в
     * SoundEngine.play; платформенное событие даёт ту же точку без миксина, а значит
     * и без конфликтов с чужими модами, которые инжектятся туда же.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() != null) {
            TinnitusHandler.muffleNewSound(event.getSound());
        }
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
        FreeAimClientCache.clear();
        RecoilSource.clearCache();
        TinnitusHandler.reset();
        RecoilDrift.reset();
    }
}
