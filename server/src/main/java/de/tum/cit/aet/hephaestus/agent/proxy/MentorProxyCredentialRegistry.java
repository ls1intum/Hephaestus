package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Mints and validates proxy-scoped bearer tokens for the mentor's long-lived interactive sandbox
 * (#1368 slice 5). {@code AgentJob} rows carry their own DB-backed job token; the mentor sandbox is
 * NOT an {@code AgentJob} (it is a reused, developer-attached session, not a one-shot NATS-dispatched
 * job), so it needs an equivalent credential minted outside that table.
 *
 * <p>In-memory, process-local — mirrors the fact that the interactive sandbox registry itself
 * (mentor sessions are keyed by {@code (developerId, workspaceId)} and attached per worker process)
 * is already process-local. A token grants exactly what an {@code AgentJob} token grants: the caller
 * can ask the LLM proxy to resolve ONE connection's credential, nothing else.
 *
 * <h2>Revoke-on-teardown</h2>
 *
 * <p>Unlike an {@code AgentJob} token — whose TTL is the job timeout and which is revoked the moment
 * the job transitions terminal — a mentor token has no natural terminal event of its own, so
 * {@link #mint} is also keyed by the sandbox's {@code sessionId}:
 * {@code agent.sandbox.docker.interactive.DockerInteractiveSandboxAdapter}
 * calls {@link #revoke(UUID)} from its dispose path (any close reason — manual, idle-reap, error, or
 * app-server shutdown) the moment the underlying container is gone. {@link #TTL} remains a backstop for
 * the case a sandbox never reaches that callback (e.g. a hard process crash).
 */
@Component
public class MentorProxyCredentialRegistry {

    private static final Duration TTL = Duration.ofHours(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, Entry> byTokenHash = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokenHashBySession = new ConcurrentHashMap<>();

    /** Non-secret catalog route granted to one mentor sandbox. */
    public record Route(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId,
        @Nullable Long legacyConfigId
    ) {}

    /** Routing + expiry for a minted mentor proxy token. */
    private record Entry(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long modelId,
        @Nullable Long workspaceId,
        @Nullable Long legacyConfigId,
        Instant expiresAt
    ) {}

    /**
     * Mint a fresh token for a mentor sandbox build. Never returns the same token twice.
     *
     * @param sessionId the sandbox's {@code InteractiveSandboxSpec#sessionId} — the correlation key
     *     {@link #revoke(UUID)} uses to find this token again at sandbox teardown
     */
    public String mint(UUID sessionId, Route route) {
        byte[] bytes = new byte[32]; // 256 bits — same shape as AgentJob's job token
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = AgentJob.computeTokenHash(token);
        byTokenHash.put(
            hash,
            new Entry(
                route.apiProtocol(),
                route.baseUrl(),
                route.connectionScope(),
                route.connectionId(),
                route.modelId(),
                route.workspaceId(),
                route.legacyConfigId(),
                Instant.now().plus(TTL)
            )
        );
        tokenHashBySession.put(sessionId, hash);
        return token;
    }

    public String mint(
        UUID sessionId,
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long legacyConfigId
    ) {
        return mint(
            sessionId,
            new Route(apiProtocol, baseUrl, connectionScope, connectionId, null, null, legacyConfigId)
        );
    }

    /** Validate a bearer token, evicting it if expired. Empty when unknown or expired. */
    public Optional<ProxyRouting> validate(String token) {
        String hash = AgentJob.computeTokenHash(token);
        Entry entry = byTokenHash.get(hash);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            byTokenHash.remove(hash);
            return Optional.empty();
        }
        return Optional.of(
            new ProxyRouting(
                "mentor-session",
                entry.apiProtocol(),
                entry.baseUrl(),
                entry.connectionScope(),
                entry.connectionId(),
                entry.modelId(),
                entry.workspaceId(),
                entry.legacyConfigId(),
                null // mentor meters per turn at completion, not per proxy call
            )
        );
    }

    /**
     * Revoke the token minted for a sandbox session, if any. Idempotent — a second call (or a call for
     * a session that never minted a token, e.g. it lost the concurrent-attach race) is a harmless no-op.
     */
    public void revoke(UUID sessionId) {
        String hash = tokenHashBySession.remove(sessionId);
        if (hash != null) {
            byTokenHash.remove(hash);
        }
    }
}
