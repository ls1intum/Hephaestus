package de.tum.cit.aet.hephaestus.agent.context;

/**
 * The only bound a collector may place on how much of a source it captures.
 *
 * <p>Collectors do not decide how much evidence a reviewer can handle. An agent reads what it needs
 * and ignores the rest, so a cap chosen to spare it — two hundred comments, eight linked issues —
 * only removes evidence nobody asked to remove, and does so silently enough that the review reads as
 * complete.
 *
 * <p>What remains is a memory bound, not an editorial one. A capture is assembled as a map of byte
 * arrays in the server's heap before it reaches the sandbox, so a pathological artifact could exhaust
 * the worker. This ceiling sits far above anything real — no pull request has ten thousand comments —
 * and a capture that reaches it is reported {@code PARTIAL} rather than passed off as whole. It exists
 * to keep a runaway from taking the worker down, and it disappears the day captures stream from disk
 * instead of being materialised.
 */
public final class EvidenceLimits {

    /** Items of one kind a single capture may hold. Reaching it makes the capture partial. */
    public static final int MAX_ITEMS_PER_SOURCE = 10_000;

    private EvidenceLimits() {}
}
