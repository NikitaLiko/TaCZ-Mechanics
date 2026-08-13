package ru.liko.tacz_mechanics.compat;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Признак «игрок сейчас пилотирует FPV-дрон», добытый рефлексией у мода {@code fpvdrone}.
 *
 * <p>Пока пилот в очках, камерой владеет FPV: он ставит yaw/pitch/roll абсолютно на
 * NORMAL-приоритете. Наклон корпуса из {@code MovementClientHandler} висит на LOWEST и
 * прибавляется поверх — горизонт в очках заваливался, а {@code CameraOffsetMixin} ещё и
 * сдвигал камеру вбок. Прибить приоритетом нельзя: LOWEST там стоит осознанно, ради SBW.</p>
 *
 * <p>Связи в сборке нет специально — мод должен собираться и работать без fpvdrone.
 * Метод ищется один раз; любая осечка означает «мода нет», и наклон работает как раньше.</p>
 */
public final class FpvDroneCompat {

    private static final Method GET_ACTIVE_DRONE_ID = resolveGetActiveDroneId();

    private FpvDroneCompat() {
    }

    private static Method resolveGetActiveDroneId() {
        if (!ModList.get().isLoaded("fpvdrone")) {
            return null;
        }
        try {
            return Class.forName("com.fpvdrone.FPVMod").getMethod("getActiveDroneId", UUID.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * {@code true}, если картинку сейчас рисует FPV: игрок либо пилотирует дрон, либо
     * просто смотрит чужой борт в надетых очках — во втором случае активного дрона у него
     * нет, а камеру FPV всё равно забирает. Очки ловим по неймспейсу предмета, чтобы не
     * тащить вторую рефлексию: наклоняться в шлеме с видеолинком всё равно нечем.
     */
    public static boolean isPilotingDrone(Player player) {
        if (GET_ACTIVE_DRONE_ID == null || player == null) {
            return false;
        }
        ResourceLocation head = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(EquipmentSlot.HEAD).getItem());
        if (head != null && "fpvdrone".equals(head.getNamespace())) {
            return true;
        }

        try {
            return ((Optional<?>) GET_ACTIVE_DRONE_ID.invoke(null, player.getUUID())).isPresent();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            return false;
        }
    }
}
