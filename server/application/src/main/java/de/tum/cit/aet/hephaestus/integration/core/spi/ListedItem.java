package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ListedItem(
        ItemRegistration item,
        ItemVersion version,
        @Nullable ItemExclusionReason exclusion,
        long sizeBytes) {
    public ListedItem(ItemRegistration item, ItemVersion version) {
        this(item, version, null, 0);
    }

    public ListedItem {
        Objects.requireNonNull(item);
        Objects.requireNonNull(version);
    }
}
