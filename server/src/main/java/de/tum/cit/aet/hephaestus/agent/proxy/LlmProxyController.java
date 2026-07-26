package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.proxy.ProxyStreamingUtils;
import de.tum.cit.aet.hephaestus.core.proxy.ProxyStreamingUtils.UpstreamResult;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
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
 * Credential-injecting proxy for the two OpenAI-compatible API surfaces used by agent sandboxes.
 * The authenticated token chooses a catalog model; callers cannot choose an upstream host, path,
 * protocol, credential header, or model id.
 */
@RestController
@Hidden
@RequestMapping("/internal/llm")
@PreAuthorize("isAuthenticated()")
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
class LlmProxyController {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyController.class);
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(310);
    private static final int MAX_REQUEST_BODY_SIZE = 4 * 1024 * 1024;
    private static final String COMPLETIONS_PROTOCOL = "openai-completions";
    private static final String RESPONSES_PROTOCOL = "openai-responses";
    private static final String COMPLETIONS_PROXY_PATH = "/internal/llm/chat/completions";
    private static final String RESPONSES_PROXY_PATH = "/internal/llm/responses";

    private final WebClient webClient;
    private final LlmModelResolver resolver;
    private final EgressPolicy egressPolicy;
    private final ObjectMapper objectMapper;
    private final ProxyAccounting accounting;

    LlmProxyController(
        WebClient llmProxyWebClient,
        LlmModelResolver llmModelResolver,
        EgressPolicy egressPolicy,
        ObjectMapper objectMapper,
        ProxyAccounting accounting
    ) {
        this.webClient = llmProxyWebClient;
        this.resolver = llmModelResolver;
        this.egressPolicy = egressPolicy;
        this.objectMapper = objectMapper;
        this.accounting = accounting;
    }

    @PostMapping({ "/chat/completions", "/responses" })
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains the workspace route")
    public ResponseEntity<?> proxy(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestHeader HttpHeaders incomingHeaders,
        @RequestBody(required = false) byte[] body
    ) {
        ProxyRouting routing = authenticatedRouting();
        ResponseEntity<String> rejected = validateSafeSurface(request, routing, body);
        if (rejected != null) return rejected;

        MDC.put("proxy.principal", routing.principalDescription());
        MDC.put("proxy.apiProtocol", routing.apiProtocol());
        Timer.Sample timer = accounting.startTimer();
        try {
            return forward(routing, response, incomingHeaders, body);
        } finally {
            accounting.stopTimer(timer, routing.apiProtocol());
            MDC.remove("proxy.principal");
            MDC.remove("proxy.apiProtocol");
        }
    }

    private ResponseEntity<?> forward(
        ProxyRouting routing,
        HttpServletResponse response,
        HttpHeaders incomingHeaders,
        byte[] body
    ) {
        // In-flight budget backstop: once a workspace has crossed its monthly cap — counting what THIS
        // attempt has already consumed but not yet had recorded — refuse NEW upstream calls before
        // resolving any credential or hitting the network. Never interrupts a call already streaming.
        // Reads a short-TTL cached ledger verdict so this is not a per-call month-window SUM.
        if (accounting.refuseForBudget(routing)) {
            return ResponseEntity.status(429).body(budgetReachedMessage(routing.connectionScope()));
        }

        LlmModelResolver.ProxyCredential credential = resolver.resolveProxyCredential(
            new LlmModelResolver.ConnectionRef(
                routing.connectionScope(),
                routing.connectionId(),
                routing.modelId(),
                routing.workspaceId()
            )
        );
        if (credential == null) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("The configured model is not available");
        }
        if (!routing.apiProtocol().equals(credential.apiProtocol())) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("The configured model protocol changed");
        }

        try {
            egressPolicy.validate(credential.baseUrl());
        } catch (IllegalArgumentException e) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream target not permitted");
        }

        boolean responsesProtocol = RESPONSES_PROTOCOL.equals(routing.apiProtocol());
        PreparedBody prepared = prepareBody(body, credential.upstreamModelId(), !responsesProtocol);
        if (prepared == null) return ResponseEntity.badRequest().body("Request body must be a JSON object");

        URI upstreamUri;
        try {
            upstreamUri = buildUpstreamUri(credential.baseUrl(), routing.apiProtocol());
        } catch (IllegalArgumentException e) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Invalid upstream configuration");
        }

        HttpHeaders upstreamHeaders = buildUpstreamHeaders(incomingHeaders, credential);
        UpstreamResult upstream;
        try {
            upstream = callUpstream(upstreamUri, upstreamHeaders, prepared.body());
            if (rejectedOurUsageRequest(upstream, prepared)) {
                // Degrade, don't fail: asking for usage is OUR addition to the caller's payload, so a
                // provider that refuses the flag must cost the caller nothing more than our blindness
                // to that call's tokens. Retried once, with the body the caller actually sent.
                log.info(
                    "Upstream rejected stream_options.include_usage for principal {}; retrying without it — " +
                        "this call's tokens will not be metered",
                    routing.principalDescription()
                );
                accounting.recordStreamUsageUnsupported(routing.apiProtocol());
                upstream = callUpstream(upstreamUri, upstreamHeaders, prepared.withoutUsageRequestOrBody());
            }
        } catch (WebClientRequestException e) {
            log.warn(
                "LLM upstream unreachable for principal {}: reason={}",
                routing.principalDescription(),
                e.getClass().getSimpleName()
            );
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Upstream provider unreachable");
        } catch (Exception e) {
            log.warn(
                "LLM upstream request failed for principal {}: reason={}",
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
        boolean served = upstream.status() >= 200 && upstream.status() < 300;
        if (upstream.sseBody() != null) {
            // Streamed calls are metered by teeing the bytes as they pass, NOT by buffering the
            // response: the client sees the same frames, at the same time, flushed the same way. The
            // usage frame is the last one, so a stream that dies before it bills nothing — what it
            // observed — rather than a guess from the deltas.
            ProxyStreamUsageTap tap =
                served && routing.attempt() != null ? new ProxyStreamUsageTap(objectMapper, responsesProtocol) : null;
            ProxyStreamingUtils.streamSseToResponse(
                upstream.sseBody(),
                upstream.headers(),
                response,
                upstream.status(),
                tap
            );
            if (tap != null) {
                // After the stream ends for ANY reason — natural end, client disconnect, upstream
                // timeout — so whatever the tap managed to observe is still recorded.
                accounting.recordUsage(routing.attempt(), tap.observed());
            }
            return null;
        }
        // Crash-safe accounting: attribute this non-streaming call's tokens to the execution now, so a
        // job or turn that dies before its terminal write still bills the calls it actually made.
        // Best-effort — never affects the returned response.
        if (routing.attempt() != null && upstream.body() != null && served) {
            accounting.recordUsage(routing.attempt(), upstream.body(), responsesProtocol);
        }
        return ResponseEntity.status(upstream.status()).headers(upstream.headers()).body(upstream.body());
    }

    private @Nullable UpstreamResult callUpstream(URI uri, HttpHeaders upstreamHeaders, byte[] outgoingBody) {
        return webClient
            .method(HttpMethod.POST)
            .uri(uri)
            .headers(headers -> {
                headers.clear();
                headers.addAll(upstreamHeaders);
            })
            .bodyValue(outgoingBody)
            .exchangeToMono(ProxyStreamingUtils::consumeResponse)
            .block(BLOCK_TIMEOUT);
    }

    /**
     * Whether this rejection is one WE caused by asking for streamed usage. Deliberately narrow: only
     * when we actually added the flag, only on the status range a provider uses to reject an unknown
     * request field, and only when it names the field. A blanket retry on 4xx would double every
     * genuinely bad request the runner makes.
     */
    private static boolean rejectedOurUsageRequest(@Nullable UpstreamResult upstream, PreparedBody prepared) {
        if (upstream == null || prepared.withoutUsageRequest() == null) return false;
        if (upstream.status() != 400 && upstream.status() != 422) return false;
        byte[] body = upstream.body();
        return body != null && new String(body, StandardCharsets.UTF_8).contains("stream_options");
    }

    private ResponseEntity<String> validateSafeSurface(HttpServletRequest request, ProxyRouting routing, byte[] body) {
        if (!"POST".equals(request.getMethod())) return ResponseEntity.status(405).body("Method not allowed");
        if (request.getQueryString() != null) return ResponseEntity.badRequest().body(
            "Query parameters are not allowed"
        );

        String expectedPath = switch (routing.apiProtocol()) {
            case COMPLETIONS_PROTOCOL -> COMPLETIONS_PROXY_PATH;
            case RESPONSES_PROTOCOL -> RESPONSES_PROXY_PATH;
            default -> null;
        };
        if (expectedPath == null || !expectedPath.equals(request.getRequestURI())) {
            return ResponseEntity.status(404).body("Not found");
        }
        if (body == null || body.length == 0) return ResponseEntity.badRequest().body("Request body is required");
        if (body.length > MAX_REQUEST_BODY_SIZE) return ResponseEntity.status(413).body("Request body too large");
        try {
            if (!objectMapper.readTree(body).isObject()) {
                return ResponseEntity.badRequest().body("Request body must be a JSON object");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Request body must be valid JSON");
        }
        return null;
    }

    /**
     * The body to send upstream, and — when we added the streamed-usage request to it — the same body
     * without that addition, so a provider that rejects the flag can be retried on what the caller
     * actually sent.
     *
     * @param withoutUsageRequest {@code null} when we added nothing, i.e. there is nothing to fall
     *     back to and a 400 from upstream is the caller's own
     */
    record PreparedBody(byte[] body, byte@Nullable [] withoutUsageRequest) {
        byte[] withoutUsageRequestOrBody() {
            return withoutUsageRequest != null ? withoutUsageRequest : body;
        }
    }

    /**
     * Lock the model, strip what the sandbox may not ask for, and — on a streaming chat-completions
     * request — ask the provider to report usage.
     *
     * <p>That last step is the only alteration the caller can observe, and it is deliberate: an
     * OpenAI-compatible stream reports no usage at all unless the request carries
     * {@code stream_options.include_usage}, so without it every streamed call is unbillable. The
     * responses API needs no equivalent flag — it puts usage on its terminal {@code response.completed}
     * event by default — which is why this is asked for on one protocol and not the other.
     *
     * @param includeStreamingUsage true for chat-completions (see above); the flag is only actually
     *     added when the request is streaming
     * @return {@code null} when the body is not a JSON object or asks for a capability the proxy
     *     refuses to forward
     */
    @Nullable
    PreparedBody prepareBody(byte[] body, String upstreamModelId, boolean includeStreamingUsage) {
        if (body == null || body.length == 0) return null;
        try {
            JsonNode tree = objectMapper.readTree(body);
            if (!tree.isObject()) return null;
            ObjectNode object = (ObjectNode) tree;
            if (
                usesProviderHostedTool(object.get("tools")) ||
                object.has("web_search_options") ||
                object.has("audio") ||
                !isTextOnlyModality(object.get("modalities"))
            ) return null;
            object.put("model", upstreamModelId);
            object.remove("service_tier");
            if (!includeStreamingUsage || !object.path("stream").asBoolean(false)) {
                return new PreparedBody(objectMapper.writeValueAsBytes(object), null);
            }
            byte[] asSent = objectMapper.writeValueAsBytes(object);
            JsonNode existing = object.get("stream_options");
            ObjectNode options =
                existing != null && existing.isObject() ? (ObjectNode) existing : object.putObject("stream_options");
            options.put("include_usage", true);
            byte[] withUsage = objectMapper.writeValueAsBytes(object);
            // Nothing was added when the caller already asked for usage itself — then a rejection is
            // theirs to own and there is no degraded form to retry.
            return new PreparedBody(withUsage, Arrays.equals(asSent, withUsage) ? null : asSent);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean usesProviderHostedTool(JsonNode tools) {
        if (tools == null) return false;
        if (!tools.isArray()) return true;
        for (JsonNode tool : tools) {
            String type = tool.path("type").asString("");
            if (!"function".equals(type) && !"custom".equals(type)) return true;
        }
        return false;
    }

    private static boolean isTextOnlyModality(JsonNode modalities) {
        return (
            modalities == null ||
            (modalities.isArray() && modalities.size() == 1 && "text".equals(modalities.get(0).asString()))
        );
    }

    HttpHeaders buildUpstreamHeaders(HttpHeaders incomingHeaders, LlmModelResolver.ProxyCredential credential) {
        HttpHeaders outgoing = new HttpHeaders();
        outgoing.setContentType(MediaType.APPLICATION_JSON);
        if (incomingHeaders.getFirst(HttpHeaders.ACCEPT) != null) {
            outgoing.set(HttpHeaders.ACCEPT, incomingHeaders.getFirst(HttpHeaders.ACCEPT));
        }
        outgoing.set(HttpHeaders.ACCEPT_ENCODING, "identity");

        if (credential.apiKey() != null && !credential.apiKey().isBlank()) {
            if (credential.authMode() == LlmAuthMode.API_KEY) {
                outgoing.set("api-key", credential.apiKey());
            } else {
                outgoing.setBearerAuth(credential.apiKey());
            }
        }
        return outgoing;
    }

    static URI buildUpstreamUri(String baseUrl, String apiProtocol) {
        String suffix = switch (apiProtocol) {
            case COMPLETIONS_PROTOCOL -> "/chat/completions";
            case RESPONSES_PROTOCOL -> "/responses";
            default -> throw new IllegalArgumentException("Unsupported API protocol");
        };
        return URI.create(baseUrl.strip().replaceAll("/+$", "") + suffix);
    }

    private ProxyRouting authenticatedRouting() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JobTokenAuthentication tokenAuthentication) {
            return tokenAuthentication.getPrincipal();
        }
        throw new IllegalStateException("Expected JobTokenAuthentication on security context");
    }

    /**
     * The 429 body names WHICH purse stopped the call, because the two have different remedies: the
     * workspace can raise its own cap itself, while a shared-model cap is the host's to raise.
     */
    private static String budgetReachedMessage(@Nullable FundingSource fundingSource) {
        return fundingSource == FundingSource.WORKSPACE
            ? "Own-provider budget reached. Paused until an admin raises the cap or the month rolls over."
            : "Shared-model budget reached. Paused until an admin raises the budget or the month rolls over.";
    }

    private void incrementErrors(String apiProtocol) {
        accounting.recordError(apiProtocol);
    }
}
