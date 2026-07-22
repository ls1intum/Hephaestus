package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.catalog.EgressPolicy;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
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
    private final MeterRegistry meterRegistry;

    LlmProxyController(
        WebClient llmProxyWebClient,
        LlmModelResolver llmModelResolver,
        EgressPolicy egressPolicy,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.webClient = llmProxyWebClient;
        this.resolver = llmModelResolver;
        this.egressPolicy = egressPolicy;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
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
        Timer.Sample timer = Timer.start();
        try {
            return forward(routing, response, incomingHeaders, body);
        } finally {
            timer.stop(
                Timer.builder("llm.proxy.duration")
                    .description("LLM proxy request duration")
                    .tag("apiProtocol", routing.apiProtocol())
                    .register(meterRegistry)
            );
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
        LlmModelResolver.ProxyCredential credential = resolver.resolveProxyCredential(
            new LlmModelResolver.ConnectionRef(
                routing.connectionScope(),
                routing.connectionId(),
                routing.modelId(),
                routing.workspaceId()
            ),
            routing.legacyConfigId(),
            routing.apiProtocol()
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

        byte[] outgoingBody = prepareBody(
            body,
            credential.upstreamModelId(),
            COMPLETIONS_PROTOCOL.equals(routing.apiProtocol())
        );
        if (outgoingBody == null) return ResponseEntity.badRequest().body("Request body must be a JSON object");

        URI upstreamUri;
        try {
            upstreamUri = buildUpstreamUri(credential.baseUrl(), routing.apiProtocol());
        } catch (IllegalArgumentException e) {
            incrementErrors(routing.apiProtocol());
            return ResponseEntity.status(502).body("Invalid upstream configuration");
        }

        UpstreamResult upstream;
        try {
            upstream = webClient
                .method(HttpMethod.POST)
                .uri(upstreamUri)
                .headers(headers -> {
                    headers.clear();
                    headers.addAll(buildUpstreamHeaders(incomingHeaders, credential));
                })
                .bodyValue(outgoingBody)
                .exchangeToMono(ProxyStreamingUtils::consumeResponse)
                .block(BLOCK_TIMEOUT);
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
        if (upstream.sseBody() != null) {
            ProxyStreamingUtils.streamSseToResponse(
                upstream.sseBody(),
                upstream.headers(),
                response,
                upstream.status()
            );
            return null;
        }
        return ResponseEntity.status(upstream.status()).headers(upstream.headers()).body(upstream.body());
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

    byte[] prepareBody(byte[] body, String upstreamModelId, boolean includeStreamingUsage) {
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
            if (includeStreamingUsage && object.path("stream").asBoolean(false)) {
                JsonNode existing = object.get("stream_options");
                ObjectNode options =
                    existing != null && existing.isObject()
                        ? (ObjectNode) existing
                        : object.putObject("stream_options");
                options.put("include_usage", true);
            }
            return objectMapper.writeValueAsBytes(object);
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

    private void incrementErrors(String apiProtocol) {
        meterRegistry.counter("llm.proxy.errors", "apiProtocol", apiProtocol).increment();
    }
}
