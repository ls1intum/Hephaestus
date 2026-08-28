package de.tum.cit.aet.hephaestus.agent.sandbox.spi;

import java.util.UUID;

/**
 * Immutable identity of an {@link AttachedSandbox}: the three keys that never change over the
 * lifetime of a session. Collapsing the former {@code sessionId()/userId()/workspaceId()}
 * accessors into one value object keeps the SPI surface focused (ISP) without losing any
 * information — callers reach the same fields via {@code identity().sessionId()} etc.
 *
 * @param sessionId   unique session identifier (also the container SESSION_ID label)
 * @param userId      owning user identifier; one live session per (userId, workspaceId)
 * @param workspaceId owning workspace identifier
 */
public record SandboxIdentity(UUID sessionId, String userId, String workspaceId) {}
