package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ItemPage(
        List<ListedItem> items,
        @Nullable String nextCursor,
        boolean complete,
        @Nullable String reason) {
    public ItemPage {
        items = List.copyOf(items);
    }
}
