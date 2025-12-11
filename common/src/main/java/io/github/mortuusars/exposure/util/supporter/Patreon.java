package io.github.mortuusars.exposure.util.supporter;

import io.github.mortuusars.exposure.Exposure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;

public class Patreon {
    private long lastQueryTime = -1L;
    private @Nullable Map<Tier, List<Supporter>> patrons = null;

    public boolean canQuery() {
        return System.currentTimeMillis() - lastQueryTime > 60000; // 1 min
    }

    public @NotNull Map<Tier, List<Supporter>> getOrQuery() {
        return Collections.emptyMap();
    }

    public @NotNull Map<Tier, List<Supporter>> query() {
        lastQueryTime = System.currentTimeMillis();
        if (patrons == null) {
            return Collections.emptyMap();
        }

        return patrons;
    }

    // --

    public boolean hasAccessToGoldenSkin(UUID uuid) {
        Map<Tier, List<Supporter>> patrons = getOrQuery();
        return patrons.getOrDefault(Tier.GOLD, Collections.emptyList()).stream().anyMatch(s -> s.matches(uuid))
                || patrons.getOrDefault(Tier.DIAMOND, Collections.emptyList()).stream().anyMatch(s -> s.matches(uuid));
    }

    public enum Tier {
        COPPER,
        IRON,
        GOLD,
        DIAMOND;

        public URI getUuidsUri() {
            return URI.create("https://raw.githubusercontent.com/mortuusars/resources/refs/heads/main/supporters/patreon/uuids/"
                    + name().toLowerCase() + ".json");
        }
    }
}