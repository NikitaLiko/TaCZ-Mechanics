package ru.liko.tacz_mechanics.data.manager;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireSoundManager;
import ru.liko.tacz_mechanics.data.whizz.WhizzSoundManager;

@EventBusSubscriber(modid = TaczMechanics.MODID)
public class DataManagerRegistry {
    
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(BulletSoundsManager.INSTANCE);
        event.addListener(BulletParticlesManager.INSTANCE);
        event.addListener(BulletInteractionsManager.INSTANCE);
        event.addListener(DistantFireManager.INSTANCE);
        event.addListener(DistantFireSoundManager.INSTANCE);
        event.addListener(WhizzSoundManager.INSTANCE);
    }
    
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        for (var level : server.getAllLevels()) {
            BulletParticlesManager.INSTANCE.onLevelTick(level);
            DistantFireManager.INSTANCE.onServerTick(level);
        }
    }
}
