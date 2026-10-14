package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Objects;

public record ListedItem(ItemRegistration item, ItemVersion version) {
    public ListedItem {
        Objects.requireNonNull(item);
        Objects.requireNonNull(version);
    }
}
