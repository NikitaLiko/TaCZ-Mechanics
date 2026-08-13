package ru.liko.tacz_mechanics.client.hitbox;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import ru.liko.tacz_mechanics.TaczMechanics;

/**
 * {@code /taczmech hitbox} — toggles the skeleton overlay without touching the config or restarting.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class HitboxDebugCommand {

    private HitboxDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> hitbox = Commands.literal("hitbox")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    boolean on = HitboxDebugRenderer.toggle();
                    ctx.getSource().sendSuccess(() -> on ? enabledMessage() : disabledMessage(), false);
                    return 1;
                });
        event.getDispatcher().register(Commands.literal("taczmech").then(hitbox));
    }

    private static Component enabledMessage() {
        return Component.literal("Hitbox overlay ON  ")
                .append(Component.literal("head ").withStyle(ChatFormatting.RED))
                .append(Component.literal("torso ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("arms ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("legs").withStyle(ChatFormatting.YELLOW));
    }

    private static Component disabledMessage() {
        return Component.literal("Hitbox overlay OFF").withStyle(ChatFormatting.GRAY);
    }
}
