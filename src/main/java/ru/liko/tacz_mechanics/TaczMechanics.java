package ru.liko.tacz_mechanics;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.data.distant_fire.DistantFireRegistry;
import ru.liko.tacz_mechanics.data.manager.BulletInteractionsManager;
import ru.liko.tacz_mechanics.data.manager.BulletParticlesManager;
import ru.liko.tacz_mechanics.data.manager.BulletSoundsManager;
import ru.liko.tacz_mechanics.data.whizz.WhizzSoundManager;
import ru.liko.tacz_mechanics.particle.ModParticles;

@Mod(TaczMechanics.MODID)
public class TaczMechanics {
    public static final String MODID = "tacz_mechanics";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TaczMechanics(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

        ModParticles.TYPES.register(modEventBus);
        modEventBus.addListener(TaczMechanics::onCommonSetup);

        LOGGER.info("TaCZ Mechanics initialized");
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BulletSoundsManager.INSTANCE.bootstrap();
            BulletParticlesManager.INSTANCE.bootstrap();
            BulletInteractionsManager.INSTANCE.bootstrap();
            DistantFireRegistry.INSTANCE.bootstrap();
            WhizzSoundManager.INSTANCE.bootstrap();
        });
    }
}
