package io.github.mortuusars.exposure.data;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.component.FocalRange;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class Lenses {
    public static Optional<FocalRange> getFocalRange(RegistryAccess registryAccess, ItemStack stack) {
        if (!stack.is(Exposure.Tags.Items.LENSES)) {
            return Optional.empty();
        }

        return registryAccess.registryOrThrow(Exposure.Registries.LENS)
                .stream()
                .filter(lens -> lens.predicate().matches(stack))
                .findAny()
                .map(Lens::focalRange);
    }

    public static FocalRange getFocalRangeOrDefault(RegistryAccess registryAccess, ItemStack stack) {
        return getFocalRange(registryAccess, stack).orElse(FocalRange.getDefault());
    }
}