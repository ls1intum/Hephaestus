package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The routing shape a validated proxy credential (an {@code AgentJob} token or a mentor session
 * token) resolves to. Carries the FROZEN, non-secret behaviour (api protocol + upstream base URL —
 * see {@code ConfigSnapshot}) plus enough of a connection reference for
 * {@code LlmModelResolver#resolveProxyCredential} to re-resolve the LIVE credential + header
 * material. Never carries the credential itself (#1368 slice 5).
 *
 * @param principalDescription log/metrics-safe identifier of the caller (job id or mentor session
 *     description) — never the token.
 * @param sourceId the {@code agent_job} id this route bills to, so the proxy can attribute per-call
 *     token usage to the job for crash-safe accounting (#1368). {@code null} for the mentor route,
 *     which meters per turn at completion instead.
 */
public record ProxyRouting(
    String principalDescription,
    String apiProtocol,
    String baseUrl,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    @Nullable Long modelId,
    @Nullable Long workspaceId,
    @Nullable Long legacyConfigId,
    @Nullable UUID sourceId
) {
    public ProxyRouting(
        String principalDescription,
        String apiProtocol,
        String baseUrl,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        @Nullable Long legacyConfigId
    ) {
        this(
            principalDescription,
            apiProtocol,
            baseUrl,
            connectionScope,
            connectionId,
            null,
            null,
            legacyConfigId,
            null
        );
    }
}
