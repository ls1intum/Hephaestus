package de.tum.cit.aet.hephaestus.evidence;

/**
 * Whether, and by what means, a source can establish that the captured copy is current.
 *
 * <p>No age-based mode: the mirror records when a row was last <em>written</em>, not when it was last
 * <em>verified</em>, so an age derived from it reports a correctly mirrored quiet record as stale.
 * Adding one requires the sync layer to record a verified-as-of watermark first.
 */
public enum FreshnessMode {
    /** The capture is anchored to an immutable upstream identity and therefore cannot drift. */
    PINNED_IDENTITY,
    /**
     * No available signal can establish currentness for this source. This records that currentness
     * cannot be demonstrated, not that it is irrelevant, and authoring validation rejects a
     * {@code CURRENT} requirement on such a source.
     */
    NOT_APPLICABLE,
}
