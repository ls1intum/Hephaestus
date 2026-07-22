package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WebClientConnectors;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "Test &amp; fetch models" probe for LLM connections (#1368). Issues a short-timeout {@code GET
 * {baseUrl}/models} and reports only whether the provider answered and which model ids it listed.
 *
 * <p>Contract: the probe never throws on an upstream failure — a 4xx, 5xx or timeout is reported as an
 * advisory {@link LlmProbeResultDTO} with {@code reachable=false}. Only the egress guard (a client-side
 * misconfiguration) may reject the request before any network call. The upstream body is never echoed
 * back; only {@code data[].id} is extracted.
 */
@Service
@WorkspaceAgnostic("Instance LLM connection probe reads the global connection catalog, not tenant data")
public class LlmConnectionProbeService {

    private static final Logger log = LoggerFactory.getLogger(LlmConnectionProbeService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_MODELS = 1_000;
    private static final int MAX_MODEL_ID_LENGTH = 256;

    private final LlmConnectionRepository connectionRepository;
    private final EgressPolicy egressPolicy;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    LlmConnectionProbeService(
        LlmConnectionRepository connectionRepository,
        EgressPolicy egressPolicy,
        ObjectMapper objectMapper,
        @Value("${hephaestus.llm.egress.allow-loopback:false}") boolean allowLoopback
    ) {
        this.connectionRepository = connectionRepository;
        this.egressPolicy = egressPolicy;
        this.objectMapper = objectMapper;
        // Dedicated short-timeout client — deliberately independent of any job/proxy timeout.
        // Redirects are NEVER followed (Reactor Netty's default: no automatic redirect-following unless
        // explicitly enabled, reasserted below for clarity): only the initial URL is egress-validated,
        // and the custom auth header (x-api-key / api-key / Authorization) would otherwise survive a
        // cross-origin redirect and exfiltrate the credential to whatever host the 3xx points at. A
        // redirect response is reported as an ordinary unreachable/unsupported probe result (its status
        // is not 2xx), never followed and never treated as an error.
        //
        // Connect-time SSRF guard (#1368 fix wave): EgressPolicy.validate() above only checks the DNS
        // answer AT VALIDATION TIME — without a guarded resolver here too, a DNS-rebind target (public
        // answer during validation, private answer at connect time) would sail straight through. The
        // guarded resolver re-runs the same private-address check on the resolution actually used to
        // connect, closing that TOCTOU window; the loopback exemption mirrors EgressPolicy's own
        // literal-host dev/e2e allowance so the two layers agree.
        HttpClient httpClient = HttpClient.create()
            .resolver(WebClientConnectors.resolverGroup(allowLoopback))
            .followRedirect(false);
        ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(PROBE_TIMEOUT);
        factory.setReadTimeout(PROBE_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Probe a stored connection using its persisted credential. */
    @Transactional(readOnly = true)
    public LlmProbeResultDTO probeStored(Long connectionId) {
        LlmConnection connection = connectionRepository
            .findById(connectionId)
            .orElseThrow(() -> new EntityNotFoundException("LlmConnection", connectionId));
        egressPolicy.validate(connection.getBaseUrl());
        return probe(connection.getBaseUrl(), connection.getAuthMode(), connection.getApiKey());
    }

    /** Probe an unsaved draft using the supplied credential (never persisted). */
    public LlmProbeResultDTO probeDraft(ProbeLlmConnectionRequestDTO request) {
        egressPolicy.validate(request.baseUrl());
        LlmAuthMode authMode = request.authMode() != null ? request.authMode() : LlmAuthMode.BEARER;
        return probe(request.baseUrl(), authMode, request.apiKey());
    }

    /**
     * Low-level probe entry point for a caller that owns a different scope's connection (workspace BYO)
     * and has already egress-validated {@code baseUrl} itself — reused rather than duplicated so both
     * scopes share the exact same "test & fetch models" mechanics.
     */
    public LlmProbeResultDTO probeCredential(String baseUrl, LlmAuthMode authMode, String apiKey) {
        return probe(baseUrl, authMode, apiKey);
    }

    private LlmProbeResultDTO probe(String baseUrl, LlmAuthMode authMode, String apiKey) {
        String url = stripTrailingSlash(baseUrl) + "/models";
        try {
            return restClient
                .get()
                .uri(url)
                .headers(headers -> {
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    if (StringUtils.hasText(apiKey)) {
                        if (authMode == LlmAuthMode.API_KEY) {
                            headers.set("api-key", apiKey);
                        } else {
                            headers.setBearerAuth(apiKey);
                        }
                    }
                })
                .exchange((clientRequest, clientResponse) -> {
                    int status = clientResponse.getStatusCode().value();
                    if (!clientResponse.getStatusCode().is2xxSuccessful()) {
                        return LlmProbeResultDTO.unreachable(status, "Provider returned HTTP " + status);
                    }
                    byte[] response = clientResponse.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (response.length > MAX_RESPONSE_BYTES) {
                        return LlmProbeResultDTO.unreachable(status, "Provider response was too large");
                    }
                    JsonNode body = objectMapper.readTree(response);
                    return LlmProbeResultDTO.reachable(extractModelIds(body), status);
                });
        } catch (Exception e) {
            // Any transport-level failure (timeout, DNS, connection refused, malformed body) is advisory,
            // never fatal. The exception message may carry host detail, so keep it out of the response.
            log.info("LLM connection probe failed: reason={}", e.getClass().getSimpleName());
            return LlmProbeResultDTO.unreachable(null, "Could not reach the provider: " + e.getClass().getSimpleName());
        }
    }

    private static List<String> extractModelIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body == null) {
            return ids;
        }
        for (JsonNode entry : body.path("data")) {
            String id = entry.path("id").asString("");
            if (!id.isBlank() && id.length() <= MAX_MODEL_ID_LENGTH) {
                ids.add(id);
                if (ids.size() == MAX_MODELS) {
                    break;
                }
            }
        }
        return ids;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
