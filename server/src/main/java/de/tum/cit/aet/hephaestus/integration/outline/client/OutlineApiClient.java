package de.tum.cit.aet.hephaestus.integration.outline.client;

import de.tum.cit.aet.hephaestus.core.WebClientConnectors;
import de.tum.cit.aet.hephaestus.core.security.ServerUrlValidator;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineApiKeyListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineAuthInfoResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionDocumentsResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineCollectionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentInfoResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineDocumentListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineExportResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineWebhookSubscriptionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineWebhookSubscriptionResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Thin client for the Outline API. Outline is RPC-over-POST: every call is
 * {@code POST {serverUrl}/api/<resource>.<action>} with a JSON body and a bearer token.
 *
 * <p>The server URL is admin-supplied, so every request goes through the SSRF-guarded connector
 * ({@link WebClientConnectors#ssrfGuarded()}, which blocks private-range resolution and closes the
 * DNS-rebind bypass) and is validated up front with {@link ServerUrlValidator}.
 *
 * <p>Calls run through the {@code outlineRestApi} circuit breaker wrapped in the {@code outlineRestApiRetry}
 * decorator (retries 5xx/transport/429 with bounded backoff, honoring {@code Retry-After}); a 429 that
 * survives surfaces as {@link OutlineRateLimitedException} so the sync pauses and resumes next cycle.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineApiClient {

    private static final Logger log = LoggerFactory.getLogger(OutlineApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Outline caps list pages at 100 rows; the max keeps a full pass cheapest in calls. */
    private static final int PAGE_LIMIT = 100;

    /** Guards against a malformed {@code pagination} block looping forever. */
    private static final int MAX_PAGES = 1000;

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    @Autowired
    public OutlineApiClient(
        @Qualifier("outlineRestApiCircuitBreaker") CircuitBreaker circuitBreaker,
        @Qualifier("outlineRestApiRetry") Retry retry
    ) {
        this(circuitBreaker, retry, WebClient.builder().clientConnector(WebClientConnectors.ssrfGuarded()).build());
    }

    /** Test seam: inject a WebClient with a stubbed exchange function (the guarded connector needs a real host). */
    OutlineApiClient(CircuitBreaker circuitBreaker, Retry retry, WebClient webClient) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    /** Identity from Outline's {@code auth.info}. The team id is stable per instance and becomes the Connection's instance key. */
    public record OutlineIdentity(String teamId, String teamName, String userId) {}

    /**
     * Validates a token via {@code auth.info}, returning the resolved identity. Throws
     * {@link OutlineApiException} on an unreachable host, a rejected token, or a response without a team.
     */
    public OutlineIdentity validateToken(String serverUrl, String token) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineAuthInfoResponse response = post(
            resolvedUrl,
            token,
            "/api/auth.info",
            Map.of(),
            OutlineAuthInfoResponse.class
        );
        if (
            response == null ||
            response.data() == null ||
            response.data().team() == null ||
            response.data().team().id() == null
        ) {
            throw new OutlineApiException("Outline auth.info returned no team for this token");
        }
        OutlineAuthInfoResponse.Team team = response.data().team();
        OutlineAuthInfoResponse.User user = response.data().user();
        return new OutlineIdentity(team.id(), team.name(), user == null ? null : user.id());
    }

    public record OutlineTokenDescription(String name, String last4, Instant expiresAt, Instant lastActiveAt) {}

    /**
     * Describes the token via {@code apiKeys.list}, matched on the {@code last4} suffix. Empty when the key
     * is absent or {@code apiKeys.list} is out of scope — such a token still works for content sync, so
     * missing metadata must not surface as an error.
     */
    public Optional<OutlineTokenDescription> describeToken(String serverUrl, String token) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineApiKeyListResponse response;
        try {
            response = post(
                resolvedUrl,
                token,
                "/api/apiKeys.list",
                Map.of("limit", PAGE_LIMIT),
                OutlineApiKeyListResponse.class
            );
        } catch (OutlineApiException e) {
            if (isForbidden(e)) {
                log.debug("Outline apiKeys.list is not in this token's scope — token metadata unavailable");
                return Optional.empty();
            }
            throw e;
        }
        if (response == null || response.data() == null || token.length() < 4) {
            return Optional.empty();
        }
        String suffix = token.substring(token.length() - 4);
        return response
            .data()
            .stream()
            .filter(key -> suffix.equals(key.last4()))
            .findFirst()
            .map(key -> new OutlineTokenDescription(key.name(), key.last4(), key.expiresAt(), key.lastActiveAt()));
    }

    /** Lists the collections the token can see ({@code collections.list}); the catalog pass refreshes mirrored-collection metadata. */
    public List<OutlineCollectionListResponse.Collection> listCollections(String serverUrl, String token) {
        return listCollections(serverUrl, token, MAX_PAGES);
    }

    /**
     * {@link #listCollections(String, String)} under an explicit page cap — interactive admin paths pass a
     * small cap so a pathological instance cannot stall a request thread; a hit cap logs and returns partial.
     */
    public List<OutlineCollectionListResponse.Collection> listCollections(
        String serverUrl,
        String token,
        int maxPages
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        List<OutlineCollectionListResponse.Collection> all = new ArrayList<>();
        for (int page = 0, offset = 0; page < maxPages; page++, offset += PAGE_LIMIT) {
            OutlineCollectionListResponse body = post(
                resolvedUrl,
                token,
                "/api/collections.list",
                Map.of("offset", offset, "limit", PAGE_LIMIT),
                OutlineCollectionListResponse.class
            );
            List<OutlineCollectionListResponse.Collection> pageData = body == null ? null : body.data();
            if (pageData == null || pageData.isEmpty()) {
                return all;
            }
            all.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) {
                return all;
            }
        }
        log.info(
            "outline.client: collections.list stopped at the {}-page cap ({} collections) for {} — result may be truncated",
            maxPages,
            all.size(),
            resolvedUrl
        );
        return all;
    }

    /**
     * Fetches a collection's document tree ({@code collections.documents}); {@code children} carry the
     * nesting the sync flattens into parent relationships.
     */
    public List<OutlineCollectionDocumentsResponse.Node> listCollectionDocuments(
        String serverUrl,
        String token,
        String collectionId
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineCollectionDocumentsResponse body = post(
            resolvedUrl,
            token,
            "/api/collections.documents",
            Map.of("id", collectionId),
            OutlineCollectionDocumentsResponse.class
        );
        List<OutlineCollectionDocumentsResponse.Node> data = body == null ? null : body.data();
        return data == null ? List.of() : data;
    }

    /**
     * Lists per-document metadata ({@code documents.list}, newest-{@code updatedAt} first). Ordering matters:
     * the sync spends its bounded export budget front-to-back, and {@code updatedAt} is the incremental cursor.
     */
    public List<OutlineDocumentListResponse.Meta> listDocuments(String serverUrl, String token, String collectionId) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        List<OutlineDocumentListResponse.Meta> all = new ArrayList<>();
        for (int page = 0, offset = 0; page < MAX_PAGES; page++, offset += PAGE_LIMIT) {
            OutlineDocumentListResponse body = post(
                resolvedUrl,
                token,
                "/api/documents.list",
                Map.of(
                    "collectionId",
                    collectionId,
                    "offset",
                    offset,
                    "limit",
                    PAGE_LIMIT,
                    "sort",
                    "updatedAt",
                    "direction",
                    "DESC"
                ),
                OutlineDocumentListResponse.class
            );
            List<OutlineDocumentListResponse.Meta> pageData = requirePage(body, "documents.list", collectionId);
            if (pageData.isEmpty()) {
                return all;
            }
            all.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) {
                return all;
            }
        }
        throw pageCapExhausted("documents.list", collectionId, all.size());
    }

    /**
     * Fetches one document's metadata ({@code documents.info}); empty on HTTP 404 (the webhook refresh treats
     * that as a tombstone), rethrows every other failure.
     */
    public Optional<OutlineDocumentListResponse.Meta> getDocumentInfo(
        String serverUrl,
        String token,
        String documentId
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineDocumentInfoResponse body;
        try {
            body = post(
                resolvedUrl,
                token,
                "/api/documents.info",
                Map.of("id", documentId),
                OutlineDocumentInfoResponse.class
            );
        } catch (OutlineApiException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }
        OutlineDocumentInfoResponse.Data data = body == null ? null : body.data();
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(
            new OutlineDocumentListResponse.Meta(
                data.id(),
                data.url(),
                data.title(),
                data.createdAt(),
                data.updatedAt(),
                data.urlId(),
                data.parentDocumentId(),
                data.collectionId(),
                data.createdBy(),
                data.updatedBy(),
                data.collaboratorIds(),
                data.archivedAt()
            )
        );
    }

    /**
     * Lists a collection's ARCHIVED documents ({@code documents.list} with {@code statusFilter:["archived"]}).
     * Outline's default listing excludes archived documents, so without this call the tombstone-by-absence
     * sweep would wipe an archived (soft-deleted, recoverable) document as a permanent delete.
     */
    public List<OutlineDocumentListResponse.Meta> listArchivedDocuments(
        String serverUrl,
        String token,
        String collectionId
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        List<OutlineDocumentListResponse.Meta> all = new ArrayList<>();
        for (int page = 0, offset = 0; page < MAX_PAGES; page++, offset += PAGE_LIMIT) {
            OutlineDocumentListResponse body = post(
                resolvedUrl,
                token,
                "/api/documents.list",
                Map.of(
                    "collectionId",
                    collectionId,
                    "offset",
                    offset,
                    "limit",
                    PAGE_LIMIT,
                    "sort",
                    "updatedAt",
                    "direction",
                    "DESC",
                    "statusFilter",
                    List.of("archived")
                ),
                OutlineDocumentListResponse.class
            );
            List<OutlineDocumentListResponse.Meta> pageData = requirePage(
                body,
                "documents.list[archived]",
                collectionId
            );
            if (pageData.isEmpty()) {
                return all;
            }
            all.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) {
                return all;
            }
        }
        throw pageCapExhausted("documents.list[archived]", collectionId, all.size());
    }

    /**
     * The page's rows, or {@link OutlineApiException} when Outline answered without a {@code data} array.
     *
     * <p>Data-loss guard: a short read treated as end-of-listing becomes the reconcile's {@code seen} set, and
     * the tombstone-by-absence sweep then deletes every mirrored document past the truncated tail. Failing the
     * call skips the sweep, leaving the mirror intact.
     */
    private static List<OutlineDocumentListResponse.Meta> requirePage(
        OutlineDocumentListResponse body,
        String call,
        String collectionId
    ) {
        List<OutlineDocumentListResponse.Meta> pageData = body == null ? null : body.data();
        if (pageData == null) {
            throw new OutlineApiException(
                "Outline " +
                    call +
                    " returned no data for collection " +
                    collectionId +
                    " — refusing to treat a malformed page as the end of the listing"
            );
        }
        return pageData;
    }

    /** Same contract as {@link #requirePage}: an enumeration that hits the page cap is truncated, i.e. wrong. */
    private static OutlineApiException pageCapExhausted(String call, String collectionId, int collected) {
        return new OutlineApiException(
            "Outline " +
                call +
                " exceeded the " +
                MAX_PAGES +
                "-page cap for collection " +
                collectionId +
                " after " +
                collected +
                " documents — refusing to sync a truncated listing"
        );
    }

    /**
     * Lists the change-notification subscriptions the token owns ({@code webhookSubscriptions.list}). The
     * registrar's self-heal pass diffs its stored subscription id against this.
     */
    public List<OutlineWebhookSubscriptionListResponse.Subscription> listWebhookSubscriptions(
        String serverUrl,
        String token
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        List<OutlineWebhookSubscriptionListResponse.Subscription> all = new ArrayList<>();
        for (int page = 0, offset = 0; page < MAX_PAGES; page++, offset += PAGE_LIMIT) {
            OutlineWebhookSubscriptionListResponse body = post(
                resolvedUrl,
                token,
                "/api/webhookSubscriptions.list",
                Map.of("offset", offset, "limit", PAGE_LIMIT),
                OutlineWebhookSubscriptionListResponse.class
            );
            List<OutlineWebhookSubscriptionListResponse.Subscription> pageData = body == null ? null : body.data();
            if (pageData == null || pageData.isEmpty()) {
                break;
            }
            all.addAll(pageData);
            if (pageData.size() < PAGE_LIMIT) {
                break;
            }
        }
        return all;
    }

    /** A permanent failure whose wire cause was an HTTP 404 — the addressed resource is gone upstream. */
    private static boolean isNotFound(OutlineApiException e) {
        return (e.getCause() instanceof WebClientResponseException wire && wire.getStatusCode().value() == 404);
    }

    /** Outline answers 403 both for an out-of-scope token and for a call the key's user may not make. */
    private static boolean isForbidden(OutlineApiException e) {
        return (e.getCause() instanceof WebClientResponseException wire && wire.getStatusCode().value() == 403);
    }

    /** Exports a document's body as Markdown ({@code documents.export}); {@code null} when Outline responds without a body. */
    public String exportDocument(String serverUrl, String token, String documentId) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineExportResponse body = post(
            resolvedUrl,
            token,
            "/api/documents.export",
            Map.of("id", documentId),
            OutlineExportResponse.class
        );
        return body == null ? null : body.data();
    }

    /**
     * Creates a subscription ({@code webhookSubscriptions.create}) posting the given events to
     * {@code deliveryUrl}, signed with {@code signingSecret}; returns its id, or {@code null} when Outline responds without one.
     */
    public String createWebhookSubscription(
        String serverUrl,
        String token,
        String name,
        String deliveryUrl,
        String signingSecret,
        List<String> events
    ) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        OutlineWebhookSubscriptionResponse body = post(
            resolvedUrl,
            token,
            "/api/webhookSubscriptions.create",
            Map.of("name", name, "url", deliveryUrl, "secret", signingSecret, "events", events),
            OutlineWebhookSubscriptionResponse.class
        );
        return body == null || body.data() == null ? null : body.data().id();
    }

    public void deleteWebhookSubscription(String serverUrl, String token, String subscriptionId) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        post(resolvedUrl, token, "/api/webhookSubscriptions.delete", Map.of("id", subscriptionId), Void.class);
    }

    /** One {@code POST} through the retry decorator; each attempt goes through the circuit breaker so every failure counts toward the rate. */
    private <T> T post(String resolvedUrl, String token, String path, Object requestBody, Class<T> responseType) {
        return retry.executeSupplier(() -> executeOnce(resolvedUrl, token, path, requestBody, responseType));
    }

    /**
     * A single attempt through the circuit breaker. Translates the raw wire failures: HTTP 429 →
     * {@link OutlineRateLimitedException} (with {@code Retry-After}), a 5xx / transport failure →
     * retryable {@link OutlineApiException}, a 4xx → permanent {@link OutlineApiException}, an open breaker
     * → a distinct permanent {@link OutlineApiException}.
     */
    private <T> T executeOnce(
        String resolvedUrl,
        String token,
        String path,
        Object requestBody,
        Class<T> responseType
    ) {
        Supplier<T> call = () ->
            webClient
                .post()
                .uri(resolvedUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(responseType)
                .block(REQUEST_TIMEOUT);
        try {
            return circuitBreaker.executeSupplier(call);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw new OutlineRateLimitedException(parseRetryAfter(e), e);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            log.debug("Outline {} failed: status={}, serverUrl={}", path, status, resolvedUrl);
            throw new OutlineApiException(
                "Outline " + path + " failed (HTTP " + status + ")",
                e,
                e.getStatusCode().is5xxServerError()
            );
        } catch (CallNotPermittedException e) {
            throw new OutlineApiException("Outline API is temporarily unavailable (circuit open)", e);
        } catch (OutlineApiException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Outline {} failed: serverUrl={}, error={}", path, resolvedUrl, e.getMessage());
            throw new OutlineApiException("Could not reach the Outline server", e, /* retryable */ true);
        }
    }

    /** Reads the {@code Retry-After} header (delta-seconds) from a 429, or {@code null} when absent/unparseable. */
    private static Duration parseRetryAfter(WebClientResponseException e) {
        String header = e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private String resolveAndValidateServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new OutlineApiException("An Outline server URL is required");
        }
        String trimmed = serverUrl.trim();
        String normalized = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        // Throws IllegalArgumentException on a private/loopback/metadata target; the connect flow maps that to 400.
        ServerUrlValidator.validate(normalized);
        return normalized;
    }
}
