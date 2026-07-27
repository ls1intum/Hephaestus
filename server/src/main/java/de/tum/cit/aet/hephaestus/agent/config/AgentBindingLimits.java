package de.tum.cit.aet.hephaestus.agent.config;

/**
 * The range a workspace administrator may set a binding's per-run timeout to.
 *
 * <p>Both ends are product limits, not arbitrary guards, and both are load-bearing elsewhere — which
 * is why they are constants a caller can reference rather than literals repeated per site.
 *
 * <ul>
 *   <li><b>Floor.</b> Below {@value #MIN_TIMEOUT_SECONDS}s a run cannot finish a single model call, so
 *       the binding would only ever produce timeouts.
 *   <li><b>Ceiling.</b> {@value #MAX_TIMEOUT_SECONDS}s is the longest a single agent run may last, and
 *       everything downstream that has to outlive a run is sized from it — notably
 *       {@code MentorInFlightReaper}, which treats a turn older than its window as abandoned and bills
 *       it. Without an enforced ceiling that sweep has no safe window to pick: any constant it chose
 *       could be exceeded by a binding configured past it, and the sweep would bill and close a
 *       conversation that was still running. Raising this value therefore means raising that window
 *       too; a test fails if the two stop agreeing.
 * </ul>
 */
public final class AgentBindingLimits {

    /** Shortest configurable per-run timeout, in seconds. */
    public static final int MIN_TIMEOUT_SECONDS = 30;

    /** Longest configurable per-run timeout, in seconds — one hour. */
    public static final int MAX_TIMEOUT_SECONDS = 3600;

    private AgentBindingLimits() {}
}
