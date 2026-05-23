package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

/**
 * Liveness frame. Independent from WebSocket-level ping/pong: the {@code draining} flag is what
 * the hub uses to drop the worker from dispatch rotation before SIGTERM finishes the drain wait.
 */
public record Heartbeat(boolean draining) implements WorkerControlFrame {}
