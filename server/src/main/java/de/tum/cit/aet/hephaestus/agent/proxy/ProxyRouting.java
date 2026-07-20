package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
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
 */
public record ProxyRouting(
    String principalDescription,
    String apiProtocol,
    String baseUrl,
    @Nullable FundingSource connectionScope,
    @Nullable Long connectionId,
    @Nullable Long legacyConfigId
) {}
