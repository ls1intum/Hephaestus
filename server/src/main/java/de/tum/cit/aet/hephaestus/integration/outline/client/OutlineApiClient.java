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
 * Thin client for the handful of Outline API calls the integration makes. Outline is RPC-over-POST:
 * every call is {@code POST {serverUrl}/api/<resource>.<action>} with a JSON body and a bearer token.
 *
 * <p>The server URL is admin-supplied, so every request goes through the SSRF-guarded connector
 * ({@link WebClientConnectors#ssrfGuarded()}, which blocks hostnames that resolve to private ranges
 * and closes the DNS-rebind bypass) and the URL is validated up front with {@link ServerUrlValidator}
 * (which rejects literal private/loopback/metadata addresses). The two together are the SSRF posture.
 *
 * <p>Every remote call runs through the {@code outlineRestApi} circuit breaker so repeated outages fail
 * fast rather than stalling the sync cycle, wrapped in the {@code outlineRestApiRetry} decorator that
 * retries transient failures (5xx, transport errors, 429) with bounded backoff — a 429 waits out its
 * {@code Retry-After} hint. An HTTP 429 that survives the retries surfaces as
 * {@link OutlineRateLimitedException} so the sync pauses and resumes next cycle instead of aborting.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineApiClient {

    private static final Logger log = LoggerFactory.getLogger(OutlineApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Outline caps list endpoints at 100 rows per page; using the max keeps a full pass cheapest in calls. */
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

    /**
     * Identity resolved from Outline's {@code auth.info}: the team the token belongs to and the
     * calling user. The team id is stable per Outline instance and becomes the Connection's
     * instance key.
     */
    public record OutlineIdentity(String teamId, String teamName, String userId) {}

    /**
     * Validates an Outline API token by calling {@code auth.info} against the given server, returning
     * the resolved identity. Throws {@link OutlineApiException} on an unreachable host, a rejected
     * token, or a response without a team — the connect flow turns that into a structured error.
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

    /** An Outline API key's own metadata: how it is labelled there, when it lapses, when it was last used. */
    public record OutlineTokenDescription(String name, String last4, Instant expiresAt, Instant lastActiveAt) {}

    /**
     * Describes the token itself via {@code apiKeys.list}, matched on the {@code last4} suffix Outline
     * returns in place of the (write-only) key value. Empty when the key is not in the list — a key
     * scoped away from {@code apiKeys.list}, or one belonging to a user who cannot see it, still works
     * for content sync, so the absence of metadata is not an error and must not be surfaced as one.
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

    /**
     * Lists the collections the token can see ({@code collections.list}, offset/limit paged). The sync's
     * catalog pass refreshes mirrored-collection metadata and visibility from it.
     */
    public List<OutlineCollectionListResponse.Collection> listCollections(String serverUrl, String token) {
        return listCollections(serverUrl, token, MAX_PAGES);
    }

    /**
     * {@link #listCollections(String, String)} under an explicit page budget — the interactive admin
     * paths pass a small cap so a pathological instance cannot stall a request-thread proxy call.
     * When the cap stops a still-full page stream, the truncation is logged and the partial result
     * returned.
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
     * Fetches a collection's document tree ({@code collections.documents}). Returns the root nodes; each
     * node's {@code children} carry the nesting the sync flattens into parent relationships.
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
     * Lists per-document metadata for a collection ({@code documents.list}, offset/limit paged,
     * newest-{@code updatedAt} first). The ordering matters: the sync spends its bounded export budget
     * front-to-back, so the most recently edited documents mirror first. {@code updatedAt} is also the
     * incremental cursor the sync diffs against.
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
     * Fetches one document's metadata ({@code documents.info}). Returns {@link Optional#empty()} when the
     * document no longer exists upstream (HTTP 404) — the webhook refresh path treats that as "tombstone" —
     * and rethrows every other failure.
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
     * Lists a collection's ARCHIVED documents ({@code documents.list} with {@code statusFilter: ["archived"]}).
     * Outline's default {@code documents.list} (and {@code collections.documents}) excludes archived documents
     * entirely — archive is soft and recoverable, not a delete — so without this second call the live-enumeration
     * tombstone-by-absence sweep would wipe an archived document as if it had been permanently deleted. Same
     * offset/limit paging as {@link #listDocuments}, and the same all-or-nothing contract.
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
     * The page's rows, or an {@link OutlineApiException} when Outline answered without a {@code data} array
     * at all.
     *
     * <p>This is a data-loss guard, not defensive noise. A document enumeration that quietly stops short
     * becomes the reconcile's {@code seen} set, and the tombstone-by-absence sweep then <em>deletes</em>
     * every mirrored document that fell off the truncated tail — irreversibly, while advancing the
     * watermark as if the pass had been clean. Failing the call instead records the error on the collection
     * and skips the sweep entirely; the mirror simply stays as it was until Outline answers properly again.
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
     * Lists the change-notification subscriptions the token owns ({@code webhookSubscriptions.list},
     * offset/limit paged). The registrar's self-heal pass diffs its stored subscription id against this.
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

    /**
     * Exports a document's body as Markdown ({@code documents.export}). Returns {@code null} when Outline
     * responds without a body.
     */
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
     * Registers a change-notification subscription ({@code webhookSubscriptions.create}) that posts the given
     * document events to {@code deliveryUrl}, signed with {@code signingSecret}. Returns the created
     * subscription's id, or {@code null} when Outline responds without one.
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

    /** Deletes a change-notification subscription ({@code webhookSubscriptions.delete}) by its id. */
    public void deleteWebhookSubscription(String serverUrl, String token, String subscriptionId) {
        String resolvedUrl = resolveAndValidateServerUrl(serverUrl);
        post(resolvedUrl, token, "/api/webhookSubscriptions.delete", Map.of("id", subscriptionId), Void.class);
    }

    /**
     * Issues one {@code POST {resolvedUrl}{path}} through the retry decorator. The inner call goes
     * through the circuit breaker on every attempt, so each failure counts toward the failure rate.
     */
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
