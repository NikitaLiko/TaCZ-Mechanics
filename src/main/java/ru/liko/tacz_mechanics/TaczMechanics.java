package ru.liko.tacz_mechanics;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TaczMechanics.MODID)
public class TaczMechanics {
    public static final String MODID = "tacz_mechanics";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TaczMechanics(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

        LOGGER.info("TaCZ Mechanics initialized");
    }
}
