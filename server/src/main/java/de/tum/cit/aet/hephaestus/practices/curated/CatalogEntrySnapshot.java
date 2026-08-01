package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import org.jspecify.annotations.Nullable;

/**
 * What the audit trail records about a catalog entry.
 *
 * <p>Identity, state and the fingerprint of the definition in force — not the definition itself. The
 * criteria are a detection rubric that is deliberately not shown to learners, and the audit trail is
 * read by more people than the catalog editor is.
 */
record CatalogEntrySnapshot(
    String slug,
    CatalogEntryState state,
    boolean retired,
    String effectiveFingerprint,
    @Nullable String shippedFingerprint
) implements ConfigAuditSnapshot {
    static <D extends CatalogDefinition> CatalogEntrySnapshot of(CatalogEntry<D> entry) {
        return new CatalogEntrySnapshot(
            entry.slug(),
            entry.state(),
            entry.retired(),
            entry.effective().detectionFingerprint(entry.slug()),
            entry.shipped() == null ? null : entry.shipped().detectionFingerprint(entry.slug())
        );
    }
}
