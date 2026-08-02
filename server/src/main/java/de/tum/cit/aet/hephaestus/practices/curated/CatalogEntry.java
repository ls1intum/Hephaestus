package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An effective catalog entry and the bundled definition it may override. */
public record CatalogEntry<D extends CatalogDefinition>(
    String slug,
    D effective,
    @Nullable D shipped,
    @Nullable D overridden,
    @Nullable String basedOnDigest,
    boolean retired,
    int position,
    @Nullable Instant updatedAt
) {
    public CatalogEntry {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(effective, "effective");
    }

    public static <D extends CatalogDefinition> CatalogEntry<D> shippedOnly(String slug, D shipped, int position) {
        return new CatalogEntry<>(slug, shipped, shipped, null, null, false, position, null);
    }

    public CatalogEntryState state() {
        if (overridden == null) {
            // A row that only retires an entry leaves the definition Hephaestus ships in force.
            return shipped == null ? CatalogEntryState.NO_LONGER_SHIPPED : CatalogEntryState.FROM_HEPHAESTUS;
        }
        if (shipped == null) {
            return basedOnDigest == null ? CatalogEntryState.YOURS : CatalogEntryState.NO_LONGER_SHIPPED;
        }
        if (shipped.digest(slug).equals(overridden.digest(slug))) {
            return CatalogEntryState.EDITED_HERE;
        }
        return shipped.digest(slug).equals(basedOnDigest)
            ? CatalogEntryState.EDITED_HERE
            : CatalogEntryState.UPDATE_WAITING;
    }

    /**
     * What taking the shipped definition would change. This is the difference between what is in
     * force and what Hephaestus ships, which is not the same as what Hephaestus changed — an
     * administrator's own edit to the criteria makes the difference a detection one even if the
     * newer build only reworded. Present it as a consequence, never as an attribution.
     */
    public CatalogChangeKind changeKind() {
        if (shipped == null || overridden == null) {
            return CatalogChangeKind.NONE;
        }
        if (shipped.digest(slug).equals(overridden.digest(slug))) {
            return CatalogChangeKind.NONE;
        }
        if (overridden instanceof AreaDefinition) {
            return CatalogChangeKind.PRESENTATION;
        }
        return shipped.detectionFingerprint(slug).equals(overridden.detectionFingerprint(slug))
            ? CatalogChangeKind.WORDING
            : CatalogChangeKind.DETECTION;
    }

    public boolean offered() {
        return !retired;
    }

    public String etag() {
        return new CanonicalDigest()
            .add(slug)
            .add(effective.digest(slug))
            .addNullable(shipped == null ? null : shipped.digest(slug))
            .addNullable(basedOnDigest)
            .add(state().name())
            .add(changeKind().name())
            .addInt(retired ? 1 : 0)
            .hex();
    }
}
