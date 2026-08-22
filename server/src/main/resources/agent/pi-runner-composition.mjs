// Coherence rules the composed-feedback envelope must satisfy for the server to deliver what it
// carries. Kept apart from pi-runner.mjs so they can be exercised without starting a review.

/**
 * Units the reader will discard. It resolves every SUPERSEDE against the envelope's own
 * `preparedThreadKeys`, so a unit naming a thread the envelope does not list is feedback that was
 * composed, accepted by the tool, and then dropped on the way out.
 *
 * The tool already refuses a SUPERSEDE whose key is not staged, so this can only be non-empty when
 * the envelope and the tool disagree about which threads were staged.
 */
export function undeliverableUnits(envelope) {
    const prepared = new Set(envelope?.preparedThreadKeys ?? []);
    return (envelope?.units ?? []).filter(
        (unit) => unit?.action === "SUPERSEDE" && !prepared.has(unit?.supersedesThreadKey),
    );
}
