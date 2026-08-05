package de.tum.cit.aet.hephaestus.evidence;

/**
 * How — and whether — a source can demonstrate that the captured copy is current.
 *
 * <p>There is deliberately no age-based mode. An age needs a watermark saying when the copy was last
 * <em>verified</em> against upstream; the mirror records only when a row was last <em>written</em>,
 * and rows are written only when something changed. Age computed from that answers "how long since
 * this changed", which is close to the opposite: it reports a quiet, correctly-mirrored artifact as
 * stale and a just-edited one as fresh. Reintroducing an age mode needs the sync to record a
 * verified-as-of watermark first — a reconciliation pass that confirms no change is exactly an
 * HTTP 304, and should extend freshness rather than be ignored.
 */
public enum FreshnessMode {
    /** The capture is anchored to an immutable upstream identity, so it cannot drift. */
    PINNED_IDENTITY,
    /**
     * Nothing available can demonstrate currentness for this source. Not a claim that currentness is
     * meaningless here — a statement that we cannot prove it, so no practice may require it.
     */
    NOT_APPLICABLE,
}
