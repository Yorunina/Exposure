package io.github.mortuusars.exposure.util.supporter;

import io.github.mortuusars.exposure.Exposure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;

public class Gilded {
    private long lastQueryTime = -1L;
    private @Nullable List<Supporter> gildedSupporters = null;

    public @NotNull List<Supporter> getOrQuery() {
         return Collections.emptyList();
    }

    public @NotNull List<Supporter> query() {
        lastQueryTime = System.currentTimeMillis();

        if (gildedSupporters == null) {
            return Collections.emptyList();
        }

        return gildedSupporters;
    }

    protected URI getUuidsUri() {
        return URI.create("https://raw.githubusercontent.com/mortuusars/resources/refs/heads/main/supporters/uuids/gilded.json");
    }

    // --

    public boolean hasAccessToGoldenSkin(UUID uuid) {
        return getOrQuery().stream().anyMatch(s -> s.matches(uuid));
    }
}