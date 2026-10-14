package de.tum.cit.aet.hephaestus.practices.catalog;

import org.jspecify.annotations.Nullable;

public record CatalogOffer<D>(
        String slug,
        D effective,
        @Nullable D shipped,
        @Nullable D base,
        int position,
        boolean excluded,
        CatalogEntryState state,
        CatalogChangeKind changeKind,
        String etag,
        boolean offered) {}
