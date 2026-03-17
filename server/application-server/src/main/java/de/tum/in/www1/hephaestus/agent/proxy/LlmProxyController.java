package de.tum.in.www1.hephaestus.agent.proxy;

import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.core.proxy.ProxyStreamingUtils;
import de.tum.in.www1.hephaestus.core.proxy.ProxyStreamingUtils.UpstreamResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Transparent LLM proxy controller for agent containers.
 *
 * <p>Proxies LLM API calls from sandboxed agent containers to upstream providers,
 * replacing the job token with the real API key. Each provider format is a dumb pipe
 * with zero format translation — the agent talks its native protocol.
 *
 * <p><b>Endpoint mapping:</b>
 * <pre>
 * /internal/llm/anthropic/**  → api.anthropic.com/**   (injects x-api-key)
 * /internal/llm/openai/**     → api.openai.com/**      (injects Authorization: Bearer)
 * </pre>
 *
 * <p>Authentication is handled by {@link JobTokenAuthenticationFilter} which validates
 * the job token and sets a {@link JobTokenAuthentication} on the security context.
 */
@RestController
@Hidden
@RequestMapping("/internal/llm")
@PreAuthorize("isAuthenticated()")
class LlmProxyController {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyController.class);

    private static final String PROXY_PATH_PREFIX = "/internal/llm/";

    /** Timeout for blocking on the upstream Mono — slightly above the WebClient responseTimeout. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(310);

    /** Maximum request body size (4 MB). LLM APIs typically accept large prompts with code context. */
    private static final int MAX_REQUEST_BODY_SIZE = 4 * 1024 * 1024;

    private final WebClient llmProxyWebClient;
    private final Map<LlmProvider, ProviderProxyConfig> providerConfigs;
    private final MeterRegistry meterRegistry;

    LlmProxyController(WebClient llmProxyWebClient, LlmProxyProperties properties, MeterRegistry meterRegistry) {
        this.llmProxyWebClient = llmProxyWebClient;
        this.providerConfigs = Map.of(
            LlmProvider.ANTHROPIC,
            ProviderProxyConfig.forProvider(LlmProvider.ANTHROPIC, properties),
            LlmProvider.OPENAI,
            ProviderProxyConfig.forProvider(LlmProvider.OPENAI, properties)
        );
        this.meterRegistry = meterRegistry;
    }

    /**
     * Catch-all proxy endpoint. The {@code provider} path variable determines which
     * upstream to forward to. Regex constraint ensures only known providers match.
     */
    @RequestMapping("/{provider:anthropic|openai}/**")
    public ResponseEntity<?> proxy(
        @PathVariable String provider,
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader HttpHeaders incomingHeaders,
        @RequestBody(required = false) byte[] body
    ) {
        LlmProvider llmProvider = LlmProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        ProviderProxyConfig config = providerConfigs.get(llmProvider);
        AgentJob job = getAuthenticatedJob();

        MDC.put("proxy.jobId", job.getId().toString());
        MDC.put("proxy.provider", provider);
        Timer.Sample timerSample = Timer.start();
        try {
            ResponseEntity<?> result = doProxy(provider, config, job, request, response, incomingHeaders, body);
            int status = result != null ? result.getStatusCode().value() : response.getStatus();
            log.info(
                "LLM proxy: job={} provider={} method={} path={} status={}",
                job.getId(),
                provider,
                request.getMethod(),
                request.getRequestURI(),
                status
            );
            return result;
        } finally {
            timerSample.stop(
                Timer.builder("llm.proxy.duration")
                    .description("LLM proxy request duration")
                    .tag("provider", provider)
                    .register(meterRegistry)
            );
            MDC.remove("proxy.jobId");
            MDC.remove("proxy.provider");
        }
    }

    private ResponseEntity<?> doProxy(
        String provider,
        ProviderProxyConfig config,
        AgentJob job,
        HttpServletRequest request,
        HttpServletResponse response,
        HttpHeaders incomingHeaders,
        byte[] body
    ) {
        String apiKey = job.getLlmApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Job {} has no LLM API key configured", job.getId());
            incrementErrors(provider);
            return ResponseEntity.status(502).body("No API key configured for this job");
        }

        // Reject oversized request bodies (defense against memory pressure)
        if (body != null && body.length > MAX_REQUEST_BODY_SIZE) {
            log.warn("Request body too large ({} bytes) from job {}", body.length, job.getId());
            return ResponseEntity.status(413).body("Request body too large");
        }

        // Build upstream URL: strip proxy prefix, forward rest as-is.
        String fullPath = request.getRequestURI();
        String providerPrefix = PROXY_PATH_PREFIX + provider;
        String subPath = fullPath.substring(fullPath.indexOf(providerPrefix) + providerPrefix.length());

        // Reject path traversal: check decoded form and normalize to catch double-encoding variants
        if (subPath.contains("..")) {
            return ResponseEntity.badRequest().body("Invalid path");
        }

        String incomingQuery = request.getQueryString();
        String upstreamUrl = buildUpstreamUrl(config.upstreamBaseUrl(), subPath, incomingQuery);

        // SSRF defense: verify constructed URL still points at expected upstream host and scheme
        URI upstreamUri;
        try {
            upstreamUri = URI.create(upstreamUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Malformed upstream URL for provider {}: {}", provider, e.getMessage());
            return ResponseEntity.badRequest().body("Invalid request path");
        }
        URI expectedBase = URI.create(config.upstreamBaseUrl());
        if (
            upstreamUri.getUserInfo() != null ||
            !upstreamUri.getHost().equals(expectedBase.getHost()) ||
            !upstreamUri.getScheme().equals(expectedBase.getScheme())
        ) {
            log.warn(
                "SSRF attempt: constructed '{}://{}' != expected '{}://{}'",
                upstreamUri.getScheme(),
                upstreamUri.getHost(),
                expectedBase.getScheme(),
                expectedBase.getHost()
            );
            return ResponseEntity.badRequest().body("Invalid upstream target");
        }

        HttpHeaders outHeaders = buildUpstreamHeaders(incomingHeaders, config, apiKey);

        log.debug("Proxying {} {} for job {}", request.getMethod(), upstreamUrl, job.getId());

        UpstreamResult upstream;
        try {
            var baseSpec = llmProxyWebClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(upstreamUri)
                .headers(h -> {
                    h.clear();
                    h.addAll(outHeaders);
                });

            // Only attach body for methods that typically carry one (avoids Content-Length: 0 on GET)
            WebClient.RequestHeadersSpec<?> readySpec = (body != null && body.length > 0)
                ? baseSpec.bodyValue(body)
                : baseSpec;

            upstream = readySpec.exchangeToMono(ProxyStreamingUtils::consumeResponse).block(BLOCK_TIMEOUT);
        } catch (WebClientRequestException e) {
            log.warn("Upstream unreachable for provider {}: {}", provider, e.getMessage());
            incrementErrors(provider);
            return ResponseEntity.status(502).body("Upstream provider unreachable");
        } catch (Exception e) {
            log.warn("Upstream request failed for provider {}: {}", provider, e.getMessage());
            incrementErrors(provider);
            return ResponseEntity.status(502).body("Upstream request failed");
        }

        if (upstream == null) {
            incrementErrors(provider);
            return ResponseEntity.status(502).body("Upstream provider unavailable");
        }

        log.debug(
            "Upstream responded {} (SSE={}) for job {}",
            upstream.status(),
            upstream.sseBody() != null,
            job.getId()
        );

        if (upstream.sseBody() != null) {
            ProxyStreamingUtils.streamSseToResponse(
                upstream.sseBody(),
                upstream.headers(),
                response,
                upstream.status()
            );
            return null; // Response already committed
        } else {
            return ResponseEntity.status(upstream.status()).headers(upstream.headers()).body(upstream.body());
        }
    }

    // Package-private for testability — credential isolation is a critical security invariant.
    HttpHeaders buildUpstreamHeaders(HttpHeaders incomingHeaders, ProviderProxyConfig config, String apiKey) {
        HttpHeaders out = ProxyStreamingUtils.filterHopByHopHeaders(incomingHeaders);
        out.remove(HttpHeaders.HOST);
        out.set(HttpHeaders.ACCEPT_ENCODING, "");

        // Remove all incoming auth headers (they contain the job token)
        out.remove("x-api-key");
        out.remove("api-key");
        out.remove(HttpHeaders.AUTHORIZATION);

        // Inject the real API key in the provider's expected format
        out.set(config.authHeaderName(), config.formatAuthValue(apiKey));

        return out;
    }

    private void incrementErrors(String provider) {
        meterRegistry.counter("llm.proxy.errors", "provider", provider).increment();
    }

    private AgentJob getAuthenticatedJob() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JobTokenAuthentication jta) {
            return jta.getPrincipal();
        }
        throw new IllegalStateException("Expected JobTokenAuthentication on security context");
    }

    /**
     * Build the full upstream URL, merging any query params already in the base URL
     * (e.g. Azure's {@code ?api-version=...}) with the incoming request's query string.
     */
    static String buildUpstreamUrl(String baseUrl, String subPath, String incomingQuery) {
        String basePath;
        String baseQuery;
        int q = baseUrl.indexOf('?');
        if (q >= 0) {
            basePath = baseUrl.substring(0, q);
            baseQuery = baseUrl.substring(q + 1);
        } else {
            basePath = baseUrl;
            baseQuery = null;
        }

        var url = new StringBuilder(basePath).append(subPath);
        if (baseQuery != null || incomingQuery != null) {
            url.append('?');
            if (baseQuery != null) {
                url.append(baseQuery);
                if (incomingQuery != null) url.append('&');
            }
            if (incomingQuery != null) url.append(incomingQuery);
        }
        return url.toString();
    }
}
