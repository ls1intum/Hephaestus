package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogChangeKind;
import de.tum.cit.aet.hephaestus.practices.curated.CatalogEntryState;
import org.jspecify.annotations.Nullable;

public record CatalogOffer<D>(
        String slug,
        @Nullable ItemKind kind,
        D effective,
        @Nullable D shipped,
        @Nullable D base,
        int position,
        boolean excluded,
        CatalogEntryState state,
        CatalogChangeKind changeKind,
        String etag,
        boolean offered) {}
