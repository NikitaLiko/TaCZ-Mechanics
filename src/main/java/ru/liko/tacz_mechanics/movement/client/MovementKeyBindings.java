package ru.liko.tacz_mechanics.movement.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import ru.liko.tacz_mechanics.TaczMechanics;

/**
 * Key bindings for movement mechanics.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MovementKeyBindings {
    
    public static final String CATEGORY = "key.categories.tacz_mechanics.movement";
    
    public static final KeyMapping SIT_KEY = new KeyMapping(
        "key.tacz_mechanics.sit",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        CATEGORY
    );
    
    public static final KeyMapping CRAWL_KEY = new KeyMapping(
        "key.tacz_mechanics.crawl",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        CATEGORY
    );
    
    public static final KeyMapping LEAN_LEFT_KEY = new KeyMapping(
        "key.tacz_mechanics.lean_left",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Q,
        CATEGORY
    );
    
    public static final KeyMapping LEAN_RIGHT_KEY = new KeyMapping(
        "key.tacz_mechanics.lean_right",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_E,
        CATEGORY
    );
    
    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(SIT_KEY);
        event.register(CRAWL_KEY);
        event.register(LEAN_LEFT_KEY);
        event.register(LEAN_RIGHT_KEY);
    }
}
