package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.catalog.ApiProtocolDefaults.AuthDefaults;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * "Test &amp; fetch models" probe for LLM connections (#1368). Issues a short-timeout {@code GET
 * {baseUrl}/models} and reports only whether the provider answered and which model ids it listed.
 *
 * <p>Contract: the probe never throws on an upstream failure — a 4xx, 5xx or timeout is reported as an
 * advisory {@link LlmProbeResult} with {@code reachable=false}. Only the egress guard (a client-side
 * misconfiguration) may reject the request before any network call. The upstream body is never echoed
 * back; only {@code data[].id} is extracted.
 */
@Service
@WorkspaceAgnostic("Instance LLM connection probe reads the global connection catalog, not tenant data")
public class LlmConnectionProbeService {

    private static final Logger log = LoggerFactory.getLogger(LlmConnectionProbeService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final LlmConnectionRepository connectionRepository;
    private final EgressPolicy egressPolicy;
    private final RestClient restClient;

    LlmConnectionProbeService(LlmConnectionRepository connectionRepository, EgressPolicy egressPolicy) {
        this.connectionRepository = connectionRepository;
        this.egressPolicy = egressPolicy;
        // Dedicated short-timeout client — deliberately independent of any job/proxy timeout.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PROBE_TIMEOUT);
        factory.setReadTimeout(PROBE_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Probe a stored connection using its persisted credential. */
    @Transactional(readOnly = true)
    public LlmProbeResult probeStored(Long connectionId) {
        LlmConnection connection = connectionRepository
            .findById(connectionId)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", connectionId));
        egressPolicy.validate(connection.getBaseUrl());
        return probe(
            connection.getBaseUrl(),
            connection.getAuthHeaderName(),
            connection.getAuthValuePrefix(),
            connection.getApiKey()
        );
    }

    /** Probe an unsaved draft using the supplied credential (never persisted). */
    public LlmProbeResult probeDraft(ProbeLlmConnectionRequest request) {
        egressPolicy.validate(request.baseUrl());
        AuthDefaults defaults = ApiProtocolDefaults.forProtocol(request.apiProtocol());
        String headerName = StringUtils.hasText(request.authHeaderName())
            ? request.authHeaderName()
            : defaults.headerName();
        String prefix = request.authValuePrefix() != null ? request.authValuePrefix() : defaults.valuePrefix();
        return probe(request.baseUrl(), headerName, prefix, request.apiKey());
    }

    /**
     * Low-level probe entry point for a caller that owns a different scope's connection (workspace BYO)
     * and has already egress-validated {@code baseUrl} itself — reused rather than duplicated so both
     * scopes share the exact same "test & fetch models" mechanics.
     */
    public LlmProbeResult probeCredential(
        String baseUrl,
        String authHeaderName,
        String authValuePrefix,
        String apiKey
    ) {
        return probe(baseUrl, authHeaderName, authValuePrefix, apiKey);
    }

    private LlmProbeResult probe(String baseUrl, String authHeaderName, String authValuePrefix, String apiKey) {
        String url = stripTrailingSlash(baseUrl) + "/models";
        try {
            return restClient
                .get()
                .uri(url)
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    if (StringUtils.hasText(apiKey)) {
                        String prefix = authValuePrefix != null ? authValuePrefix : "";
                        headers.add(authHeaderName, prefix + apiKey);
                    }
                })
                .exchange((clientRequest, clientResponse) -> {
                    int status = clientResponse.getStatusCode().value();
                    if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                        return LlmProbeResult.unreachable(status, "Provider returned HTTP " + status);
                    }
                    JsonNode body = clientResponse.bodyTo(JsonNode.class);
                    return LlmProbeResult.reachable(extractModelIds(body), status);
                });
        } catch (Exception e) {
            // Any transport-level failure (timeout, DNS, connection refused, malformed body) is advisory,
            // never fatal. The exception message may carry host detail, so keep it out of the response.
            log.info("LLM connection probe failed: reason={}", e.getClass().getSimpleName());
            return LlmProbeResult.unreachable(null, "Could not reach the provider: " + e.getClass().getSimpleName());
        }
    }

    private static List<String> extractModelIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body == null) {
            return ids;
        }
        for (JsonNode entry : body.path("data")) {
            String id = entry.path("id").asString("");
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
