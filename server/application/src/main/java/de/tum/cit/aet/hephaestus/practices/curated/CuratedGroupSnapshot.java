package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import org.jspecify.annotations.Nullable;

record CuratedGroupSnapshot(
        String slug,
        CatalogEntryState state,
        boolean offered,
        int position,
        String name,
        @Nullable String description,
        @Nullable String icon,
        @Nullable String color,
        @Nullable String shippedDigest)
        implements ConfigAuditSnapshot {
    static CuratedGroupSnapshot of(CatalogEntry<GroupDefinition> entry) {
        GroupDefinition definition = entry.effective();
        return new CuratedGroupSnapshot(
                entry.slug(),
                entry.state(),
                entry.offered(),
                entry.position(),
                definition.name(),
                definition.description(),
                definition.icon(),
                definition.color(),
                entry.shipped() == null ? null : entry.shipped().digest(entry.slug()));
    }
}
