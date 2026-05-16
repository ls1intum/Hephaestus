package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Where in the frame stream a new subscriber begins.
 *
 * <p>Two cursors, two distinct use cases that must not silently substitute for each other:
 *
 * <ul>
 *   <li>{@link #RING_REPLAY} — the snapshot of buffered frames is replayed before live frames.
 *       Correct for re-attach after a server restart, where a new {@code AttachedSandbox} handle
 *       has just been minted and the subscriber needs to see the existing session's recent
 *       history to rebuild its view.</li>
 *   <li>{@link #FROM_NOW} — skip the ring buffer entirely. Correct for multi-turn reuse of the
 *       same sandbox across mentor turns: turn N's {@code agent_end} sits in the ring after
 *       turn N completes, and replaying it on turn N+1's subscriber would terminate the new
 *       turn instantly with stale data.</li>
 * </ul>
 */
public enum Cursor {
    RING_REPLAY,
    FROM_NOW,
}
