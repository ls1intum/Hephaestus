package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
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
 * <h2>Residual risk (documented per #1368 slice 5)</h2>
 *
 * <p>Unlike an {@code AgentJob} token — whose TTL is the job timeout and which is revoked the moment
 * the job transitions terminal — a mentor token's only lifetime bound today is the fixed {@link #TTL}
 * below; there is no explicit revoke-on-sandbox-teardown hook wired yet (the interactive sandbox
 * lifecycle lives in {@code agent.sandbox.docker.interactive}, outside this slice). A stale token
 * therefore remains valid for up to {@link #TTL} after its sandbox is torn down. Tracked as a
 * follow-up: wire {@link #revoke} into the interactive sandbox's dispose path.
 */
@Component
public class MentorProxyCredentialRegistry {

    private static final Duration TTL = Duration.ofHours(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, Entry> byTokenHash = new ConcurrentHashMap<>();

    /** Routing + expiry for a minted mentor proxy token. */
    private record Entry(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long legacyConfigId,
        Instant expiresAt
    ) {}

    /** Mint a fresh token for a mentor sandbox build. Never returns the same token twice. */
    public String mint(
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long legacyConfigId
    ) {
        byte[] bytes = new byte[32]; // 256 bits — same shape as AgentJob's job token
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        byTokenHash.put(
            AgentJob.computeTokenHash(token),
            new Entry(apiProtocol, baseUrl, connectionScope, connectionId, legacyConfigId, Instant.now().plus(TTL))
        );
        return token;
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
                entry.legacyConfigId()
            )
        );
    }
}
