package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.CanonicalDigest;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One entry of the catalog this instance offers: the definition in force, where it came from, and —
 * when the two differ — the definition Hephaestus ships now, so an administrator can see what they
 * would be taking before they take it.
 *
 * @param <D> the definition shape, {@code PracticeDefinition} or {@code AreaDefinition}
 * @param effective what this instance offers, which is the administrator's definition when there is
 *     one and the shipped definition otherwise
 * @param shipped what this build ships, or null once it ships nothing under this slug
 * @param retired whether an administrator has stopped offering it
 * @param version the override row's version, or 0 when no administrator has touched the entry
 */
public record CatalogEntry<D extends CatalogDefinition>(
    String slug,
    D effective,
    @Nullable D shipped,
    @Nullable D overridden,
    @Nullable String basedOnDigest,
    boolean retired,
    long version,
    @Nullable Instant updatedAt
) {
    public CatalogEntry {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(effective, "effective");
    }

    /** An entry nobody has touched: what the build ships, offered as-is. */
    public static <D extends CatalogDefinition> CatalogEntry<D> shippedOnly(String slug, D shipped) {
        return new CatalogEntry<>(slug, shipped, shipped, null, null, false, 0, null);
    }

    public CatalogEntryState state() {
        if (overridden == null) {
            // A row that only retires an entry leaves the definition Hephaestus ships in force.
            return shipped == null ? CatalogEntryState.NO_LONGER_SHIPPED : CatalogEntryState.FROM_HEPHAESTUS;
        }
        if (shipped == null) {
            return basedOnDigest == null ? CatalogEntryState.YOURS : CatalogEntryState.NO_LONGER_SHIPPED;
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
        return shipped.detectionFingerprint(slug).equals(overridden.detectionFingerprint(slug))
            ? CatalogChangeKind.WORDING
            : CatalogChangeKind.DETECTION;
    }

    /** Whether a workspace created now would receive this entry. */
    public boolean offered() {
        return !retired;
    }

    /**
     * A tag for the entry as a whole, so a write can be conditioned on it whether or not an override
     * row exists yet. Two administrators editing the same entry is ordinary; the second is told.
     */
    public String etag() {
        return new CanonicalDigest()
            .add(slug)
            .addInt((int) version)
            .add(effective.digest(slug))
            .addInt(retired ? 1 : 0)
            .hex()
            .substring(0, 16);
    }
}
