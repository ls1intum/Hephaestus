package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import org.jspecify.annotations.Nullable;

record CuratedAreaSnapshot(
    String slug,
    CatalogEntryState state,
    boolean offered,
    int position,
    String name,
    @Nullable String description,
    @Nullable String icon,
    @Nullable String color,
    @Nullable String shippedDigest
) implements ConfigAuditSnapshot {
    static CuratedAreaSnapshot of(CatalogEntry<AreaDefinition> entry) {
        AreaDefinition definition = entry.effective();
        return new CuratedAreaSnapshot(
            entry.slug(),
            entry.state(),
            entry.offered(),
            entry.position(),
            definition.name(),
            definition.description(),
            definition.icon(),
            definition.color(),
            entry.shipped() == null ? null : entry.shipped().digest(entry.slug())
        );
    }
}
