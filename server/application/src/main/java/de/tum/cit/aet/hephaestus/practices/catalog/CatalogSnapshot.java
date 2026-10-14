package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.jspecify.annotations.Nullable;

record CatalogSnapshot(
        String slug, String definitionDigest, @Nullable String baseDigest, int position, boolean excluded)
        implements ConfigAuditSnapshot {}
