package de.tum.cit.aet.hephaestus.agent.context;

/**
 * The only bound a collector may place on how much of a source it captures — a memory bound, not an
 * editorial one.
 *
 * <p>A capture is assembled as byte arrays in the server's heap before it reaches the sandbox, so the
 * ceiling exists to keep a pathological artifact from exhausting the worker, not to spare the reviewing
 * agent evidence. It sits far above anything real, and a capture that reaches it is reported
 * {@code PARTIAL} rather than passed off as whole.
 */
public final class EvidenceLimits {

    /** Items of one kind a single capture may hold. Reaching it makes the capture partial. */
    public static final int MAX_ITEMS_PER_SOURCE = 10_000;

    private EvidenceLimits() {}
}
