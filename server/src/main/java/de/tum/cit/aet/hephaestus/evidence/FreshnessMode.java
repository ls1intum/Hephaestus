package de.tum.cit.aet.hephaestus.evidence;

/**
 * Whether, and by what means, a source can establish that the captured copy is current.
 *
 * <p>There is deliberately no age-based mode. An age threshold requires a watermark recording when
 * the copy was last <em>verified</em> against upstream. The mirror records only when a row was last
 * <em>written</em>, and rows are written only when the upstream record changed, so an age derived
 * from it measures time since the last upstream modification. Applied as a freshness test, that
 * reports a correctly mirrored record that has not changed recently as stale, and a record modified
 * moments ago as current.
 *
 * <p>Adding an age-based mode therefore requires the synchronization layer to record a verified-as-of
 * watermark, advanced by a reconciliation pass that confirms a record is unchanged. Such a pass is
 * the equivalent of an HTTP {@code 304}, which
 * <a href="https://www.rfc-editor.org/rfc/rfc9111.html">RFC 9111 §4.3.4</a> defines as refreshing a
 * stored response without altering it.
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
