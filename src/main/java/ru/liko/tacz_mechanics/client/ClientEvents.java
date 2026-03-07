package ru.liko.tacz_mechanics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;
import ru.liko.tacz_mechanics.client.sound.SoundFilterRegistry;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;

@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SoundFilterRegistry.tick();
        FreeAimHandler.getInstance().tick();
        SuppressionHandler.tick();
    }
}
