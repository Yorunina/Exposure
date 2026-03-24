package io.github.mortuusars.exposure.data;

import com.mojang.logging.LogUtils;
import io.github.mortuusars.exposure.Exposure;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class ColorPalettes {
    public static final ResourceKey<ColorPalette> DEFAULT = createKey(Exposure.resource("map_colors_plus"));
    public static final ResourceKey<ColorPalette> MAP_COLORS = createKey(Exposure.resource("map_colors"));

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceKey<ColorPalette> createKey(ResourceLocation location) {
        return ResourceKey.create(Exposure.Registries.COLOR_PALETTE, location);
    }

    public static Holder<ColorPalette> get(RegistryAccess registryAccess, ResourceKey<ColorPalette> key) {
        Registry<ColorPalette> registry = registryAccess.registryOrThrow(Exposure.Registries.COLOR_PALETTE);
        return registry.getHolder(key)
              .or(() -> {
                  LOGGER.error("Unknown color palette: {}. Default will be used.", key.location());
                  return registry.getHolder(DEFAULT);
              })
              .orElseThrow();
    }

    public static Holder<ColorPalette> get(RegistryAccess registryAccess, ResourceLocation key) {
        return get(registryAccess, createKey(key));
    }

    public static Holder<ColorPalette> getDefault(RegistryAccess registryAccess) {
        return get(registryAccess, DEFAULT);
    }
}
