package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.core.proxy.ProxyStreamingUtils;
import de.tum.cit.aet.hephaestus.core.proxy.ProxyStreamingUtils.UpstreamResult;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transparent LLM proxy controller for agent containers.
 *
 * <p>Proxies LLM API calls from sandboxed agent containers to upstream providers, replacing the
 * job-scoped token with the real (live-resolved) API key. Zero format translation — the agent talks
 * its native protocol; this only swaps auth and forwards.
 *
 * <p><b>#1368 slice 5 — connection identified by the AUTHENTICATED token, not the URL.</b> There is
 * no {@code /internal/llm/<provider>} path segment: {@link JobTokenAuthenticationFilter} resolves the
 * caller's {@link ProxyRouting} from its bearer token (an {@code AgentJob}'s frozen
 * {@code ConfigSnapshot}, or a mentor session's registry entry), and THAT identifies WHICH connection
 * funds the call — only the wire protocol is the snapshot's FROZEN behaviour. The upstream base URL,
 * the auth header name/prefix/value, and the api key are all re-resolved TOGETHER, LIVE, from the same
 * connection row on every request via {@link LlmModelResolver#resolveProxyCredential} — so a rotated or
 * revoked key, or a connection repointed to a new host, takes effect immediately without waiting for the
 * job to finish, and without ever pairing a freshly-rotated key with a stale frozen host.
 * {@link EgressPolicy} is re-checked here too, against that same live base URL, so a connection disabled
 * or edited to an unsafe host mid-flight cannot be used to egress. A connection with no api key
 * configured (a deliberately keyless self-hosted gateway) is forwarded without an auth header rather
 * than refused.
 *
 * <p>Authentication is handled by {@link JobTokenAuthenticationFilter} which validates
 * the bearer token and sets a {@link JobTokenAuthentication} on the security context.
 *
 * <p><b>Residual window: token rotation does not kill an already-authenticated in-flight stream
 * (#1368 fix wave, finding #9 — documented, not closed).</b> {@code AgentJobRepository#requeueOrphan}
 * rotates a job's token when it is requeued (orphan recovery, worker drain, infra-retry), and that
 * rotation DOES stop any NEW request from authenticating with the old token — the row's
 * {@code job_token_hash} no longer matches it once the CAS commits. It does NOT stop a request that
 * was ALREADY authenticated (past {@link JobTokenAuthenticationFilter}) before the rotation: this
 * method streams the upstream response — an SSE call can run minutes — with no re-validation at chunk
 * boundaries, so a zombie sandbox mid-call when its job gets requeued keeps consuming its original
 * call to completion on the now-superseded token. Deliberately left open rather than partially fenced:
 * {@link LlmProxyWebClientConfig} runs with no read-idle timeout BY DESIGN (LLM SSE streams go silent
 * during model "thinking"), so a mid-stream liveness check needs its own design, not a quick bolt-on.
 * Bounded by {@code responseTimeout} (300s) regardless. See ADR 0025's fix-wave amendment for the full
 * reasoning.
 */
@RestController
@Hidden
@RequestMapping("/internal/llm")
@PreAuthorize("isAuthenticated()")
@ConditionalOnExpression(
    "${" + RuntimeRole.AGENT_ENABLED_PROPERTY + ":false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}"
)
class LlmProxyController {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyController.class);

    private static final String PROXY_PATH_PREFIX = "/internal/llm";

    /** Timeout for blocking on the upstream Mono — slightly above the WebClient responseTimeout. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(310);

    /** Maximum request body size (4 MB). LLM APIs typically accept large prompts with code context. */
    private static final int MAX_REQUEST_BODY_SIZE = 4 * 1024 * 1024;

    private static final String AZURE_PROTOCOL = "azure-openai-responses";
    private static final String OPENAI_COMPLETIONS_PROTOCOL = "openai-completions";

    /** Azure OpenAI rejects this param with 400 "Unknown parameter". Stripped when upstream is Azure. */
    private static final String AZURE_UNSUPPORTED_PARAM = "reasoningSummary";

    /** Azure OpenAI requires this rename: {@code max_tokens} -> {@code max_completion_tokens}. */
    private static final String AZURE_PARAM_FROM = "max_tokens";
    private static final String AZURE_PARAM_TO = "max_completion_tokens";

    private final WebClient llmProxyWebClient;
    private final LlmModelResolver llmModelResolver;
    private final EgressPolicy egressPolicy;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    LlmProxyController(
        WebClient llmProxyWebClient,
        LlmModelResolver llmModelResolver,
        EgressPolicy egressPolicy,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.llmProxyWebClient = llmProxyWebClient;
        this.llmModelResolver = llmModelResolver;
        this.egressPolicy = egressPolicy;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /** Catch-all proxy endpoint — the connection is resolved from the authenticated token, not the path. */
    @RequestMapping("/**")
    public ResponseEntity<?> proxy(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader HttpHeaders incomingHeaders,
        @RequestBody(required = false) byte[] body
    ) {
        ProxyRouting routing = getAuthenticatedRouting();

        MDC.put("proxy.principal", routing.principalDescription());
        MDC.put("proxy.apiProtocol", routing.apiProtocol());
        Timer.Sample timerSample = Timer.start();
        try {
            ResponseEntity<?> result = doProxy(routing, request, response, incomingHeaders, body);
            int status = result != null ? result.getStatusCode().value() : response.getStatus();
            log.info(
                "LLM proxy: principal={} apiProtocol={} method={} path={} status={}",
                routing.principalDescription(),
                routing.apiProtocol(),
                request.getMethod(),
                request.getRequestURI(),
                status
            );
            return result;
        } finally {
            timerSample.stop(
                Timer.builder("llm.proxy.duration")
                    .description("LLM proxy request duration")
                    .tag("apiProtocol", routing.apiProtocol())
                    .register(meterRegistry)
            );
            MDC.remove("proxy.principal");
            MDC.remove("proxy.apiProtocol");
        }
    }

    private ResponseEntity<?> doProxy(
        ProxyRouting routing,
        HttpServletRequest request,
        HttpServletResponse response,
        HttpHeaders incomingHeaders,
        byte[] body
    ) {
        // Cheap, purely-syntactic checks first — no DB round-trip / decryption for a malformed
        // request. Reject oversized request bodies (defense against memory pressure).
        if (body != null && body.length > MAX_REQUEST_BODY_SIZE) {
            log.warn(
                "Request body too large ({} bytes) from principal {}",
                body.length,
                routing.principalDescription()
            );
            return ResponseEntity.status(413).body("Request body too large");
        }

        // Build upstream URL: strip the fixed proxy prefix, forward the rest as-is.
        String fullPath = request.getRequestURI();
        if (!fullPath.startsWith(PROXY_PATH_PREFIX)) {
            return ResponseEntity.badRequest().body("Invalid path");
        }
        String subPath = fullPath.substring(PROXY_PATH_PREFIX.length());

        // Reject path traversal: literal, single-encoded, double-encoded, and backslash forms
        String subPathLower = subPath.toLowerCase(Locale.ROOT);
        if (
            subPath.contains("..") ||
            subPath.contains("\\") ||
            subPathLower.contains("%2e") ||
            subPathLower.contains("%252e")
        ) {
            return ResponseEntity.badRequest().body("Invalid path");
        }

        // Routing (base URL) and credential are resolved TOGETHER from the live connection row — never
        // split between the job's frozen ConfigSnapshot and a live credential lookup, or a connection
        // repointed to a new host after dispatch would send its rotated key to the stale old host.
        LlmModelResolver.ProxyCredential credential = llmModelResolver.resolveProxyCredential(
            new LlmModelResolver.ConnectionRef(routing.connectionScope(), routing.connectionId()),
            routing.legacyConfigId(),
            routing.apiProtocol()
        );
        if (credential == null) {
            log.warn("No live connection resolvable for principal {}", routing.principalDescription());
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("No credential configured for this connection");
        }
        // A null/blank api key is a deliberately keyless connection (self-hosted vLLM/Ollama gateways
        // commonly run without auth) — buildUpstreamHeaders forwards without injecting an auth header
        // rather than refusing.
        try {
            egressPolicy.validate(credential.baseUrl());
        } catch (IllegalArgumentException e) {
            log.warn(
                "Egress policy rejected base URL for principal {}: {}",
                routing.principalDescription(),
                e.getMessage()
            );
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream target not permitted");
        }

        boolean azure = AZURE_PROTOCOL.equals(routing.apiProtocol());
        String incomingQuery = request.getQueryString();
        // Azure requires ?api-version=... on every call. The sandbox's own outbound request may not
        // set it (the custom "hephaestus" provider registration carries no api-version knob), so the
        // proxy appends the connection's LIVE-resolved azureApiVersion when the caller didn't already
        // supply one.
        if (
            azure &&
            credential.azureApiVersion() != null &&
            !credential.azureApiVersion().isBlank() &&
            (incomingQuery == null || !incomingQuery.toLowerCase(Locale.ROOT).contains("api-version="))
        ) {
            String versionParam = "api-version=" + credential.azureApiVersion();
            incomingQuery =
                incomingQuery == null || incomingQuery.isBlank() ? versionParam : incomingQuery + "&" + versionParam;
        }
        String upstreamUrl = buildUpstreamUrl(credential.baseUrl(), subPath, incomingQuery);

        // SSRF defense: verify constructed URL still points at expected upstream host and scheme
        URI upstreamUri;
        URI expectedBase;
        try {
            upstreamUri = URI.create(upstreamUrl);
            expectedBase = URI.create(credential.baseUrl());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed upstream URL for principal {}: {}", routing.principalDescription(), e.getMessage());
            return ResponseEntity.badRequest().body("Invalid request path");
        }
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

        HttpHeaders outHeaders = buildUpstreamHeaders(incomingHeaders, credential);

        byte[] outBody = azure ? sanitizeBodyForAzure(body) : body;
        if (OPENAI_COMPLETIONS_PROTOCOL.equals(routing.apiProtocol())) {
            outBody = injectStreamUsageOption(outBody);
        }

        // Host-only — never log the full upstream URL (path/query can carry provider-specific,
        // operator-sensitive routing detail; the credential itself is already excluded from headers).
        log.debug(
            "Proxying {} to host={} for principal {}",
            request.getMethod(),
            upstreamUri.getHost(),
            routing.principalDescription()
        );

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
            WebClient.RequestHeadersSpec<?> readySpec = (outBody != null && outBody.length > 0)
                ? baseSpec.bodyValue(outBody)
                : baseSpec;

            upstream = readySpec.exchangeToMono(ProxyStreamingUtils::consumeResponse).block(BLOCK_TIMEOUT);
        } catch (WebClientRequestException e) {
            // Never log e.getMessage() here — WebClientRequestException embeds the full request URI
            // (including path/query) in its message.
            log.warn(
                "Upstream unreachable for principal {}: reason={}",
                routing.principalDescription(),
                e.getClass().getSimpleName()
            );
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream provider unreachable");
        } catch (Exception e) {
            log.warn(
                "Upstream request failed for principal {}: reason={}",
                routing.principalDescription(),
                e.getClass().getSimpleName()
            );
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream request failed");
        }

        if (upstream == null) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream provider unavailable");
        }

        log.debug(
            "Upstream responded {} (SSE={}) for principal {}",
            upstream.status(),
            upstream.sseBody() != null,
            routing.principalDescription()
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
    HttpHeaders buildUpstreamHeaders(HttpHeaders incomingHeaders, LlmModelResolver.ProxyCredential credential) {
        HttpHeaders out = ProxyStreamingUtils.filterHopByHopHeaders(incomingHeaders);
        out.remove(HttpHeaders.HOST);
        out.set(HttpHeaders.ACCEPT_ENCODING, "identity");

        // Remove all incoming auth headers (they contain the proxy-scoped token, never the real key)
        out.remove("x-api-key");
        out.remove("api-key");
        out.remove(HttpHeaders.AUTHORIZATION);

        // Inject the real, live-resolved API key in the connection's configured header shape — unless
        // the connection is deliberately keyless (self-hosted vLLM/Ollama gateways), in which case the
        // request is forwarded with the auth headers stripped and nothing injected in their place.
        if (credential.apiKey() != null && !credential.apiKey().isBlank()) {
            String prefix = credential.authValuePrefix() != null ? credential.authValuePrefix() : "";
            out.set(credential.authHeaderName(), prefix + credential.apiKey());
        }

        return out;
    }

    private void incrementErrors(String apiProtocol) {
        meterRegistry.counter("llm.proxy.errors", "apiProtocol", apiProtocol).increment();
    }

    private ProxyRouting getAuthenticatedRouting() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JobTokenAuthentication jta) {
            return jta.getPrincipal();
        }
        throw new IllegalStateException("Expected JobTokenAuthentication on security context");
    }

    /**
     * Strip / rename parameters that Azure OpenAI rejects/requires-renamed but standard OpenAI does
     * not, gated purely on the connection's {@code apiProtocol} (#1368 slice 5 — never on a path
     * segment or upstream-URL string match).
     */
    byte[] sanitizeBodyForAzure(byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (!tree.isObject()) {
                return body;
            }
            ObjectNode obj = (ObjectNode) tree;
            boolean modified = false;

            if (obj.has(AZURE_UNSUPPORTED_PARAM)) {
                obj.remove(AZURE_UNSUPPORTED_PARAM);
                modified = true;
            }
            JsonNode value = obj.remove(AZURE_PARAM_FROM);
            if (value != null) {
                obj.set(AZURE_PARAM_TO, value);
                modified = true;
            }

            return modified ? objectMapper.writeValueAsBytes(obj) : body;
        } catch (Exception e) {
            log.debug("Failed to parse body for Azure sanitization: {}", e.getMessage());
            return body;
        }
    }

    /**
     * For {@code openai-completions} requests with {@code "stream": true}, ensures
     * {@code stream_options.include_usage=true} so the SSE stream's final chunk carries a usage
     * block — otherwise a streaming chat/completions call never reports token usage at all.
     */
    byte[] injectStreamUsageOption(byte[] body) {
        if (body == null || body.length == 0) {
            return body;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (!tree.isObject() || !tree.path("stream").asBoolean(false)) {
                return body;
            }
            ObjectNode obj = (ObjectNode) tree;
            JsonNode existing = obj.get("stream_options");
            ObjectNode streamOptions =
                existing != null && existing.isObject() ? (ObjectNode) existing : obj.putObject("stream_options");
            if (streamOptions.path("include_usage").asBoolean(false)) {
                return body; // already set — avoid a needless re-serialize
            }
            streamOptions.put("include_usage", true);
            obj.set("stream_options", streamOptions);
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.debug("Failed to inject stream_options.include_usage: {}", e.getMessage());
            return body;
        }
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
