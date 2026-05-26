package de.tum.cit.aet.hephaestus.analytics.posthog;

import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * PostHog REST client. Constructor fails loudly when the bean is enabled but {@code projectId} or
 * {@code personalApiKey} is missing — partial misconfiguration should crash boot, not degrade.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.posthog", name = "enabled", havingValue = "true")
public class PosthogClient {

    private static final Logger log = LoggerFactory.getLogger(PosthogClient.class);

    private final RestClient restClient;
    private final String projectId;

    public PosthogClient(PosthogProperties posthogProperties) {
        boolean hasCredentials =
            StringUtils.hasText(posthogProperties.projectId()) &&
            StringUtils.hasText(posthogProperties.personalApiKey());
        if (!hasCredentials) {
            log.error("Failed to initialize PostHog client: reason=missing_credentials");
            throw new PosthogClientException("PostHog configuration requires project ID and personal API key");
        }
        this.projectId = posthogProperties.projectId();
        String resolvedHost = normalizeHost(posthogProperties.apiHost());
        this.restClient = RestClient.builder()
            .baseUrl(resolvedHost)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + posthogProperties.personalApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        log.info("Activated PostHog client: projectId={}, host={}", projectId, resolvedHost);
    }

    public boolean deletePersonData(String distinctId) {
        if (!StringUtils.hasText(distinctId)) {
            throw new PosthogClientException("distinctId must not be empty");
        }
        log.info("Requesting PostHog person deletion: distinctId={}", distinctId);
        try {
            JsonNode response = restClient
                .get()
                .uri(uriBuilder ->
                    uriBuilder
                        .path("/api/projects/{projectId}/persons/")
                        .queryParam("distinct_ids", distinctId)
                        .build(projectId)
                )
                .retrieve()
                .body(JsonNode.class);

            if (response == null || !response.has("results") || !response.get("results").isArray()) {
                log.debug("Found no PostHog person: distinctId={}", distinctId);
                return false;
            }

            Iterator<JsonNode> iterator = response.get("results").values().iterator();
            if (!iterator.hasNext()) {
                log.info("Received empty PostHog result set: distinctId={}", distinctId);
                return false;
            }

            Set<String> personIds = new HashSet<>();

            while (iterator.hasNext()) {
                JsonNode personNode = iterator.next();
                String personId = extractPersonId(personNode);
                if (!StringUtils.hasText(personId)) {
                    continue;
                }
                personIds.add(personId);
            }

            if (personIds.isEmpty()) {
                log.info("Skipped PostHog deletion: distinctId={}, reason=no_valid_person_id", distinctId);
                return false;
            }

            for (String personId : personIds) {
                restClient
                    .delete()
                    .uri(uriBuilder ->
                        uriBuilder
                            .path("/api/projects/{projectId}/persons/{personId}/")
                            .queryParam("delete_events", true)
                            .build(projectId, personId)
                    )
                    .retrieve()
                    .toBodilessEntity();
                log.info("Requested PostHog deletion: personId={}", personId);
            }

            log.info("Completed PostHog deletion request: distinctId={}, personCount={}", distinctId, personIds.size());
            return true;
        } catch (RestClientException exception) {
            log.warn("Failed to delete PostHog data: distinctId={}", distinctId, exception);
            throw new PosthogClientException("Failed to delete PostHog data", exception);
        }
    }

    private String extractPersonId(JsonNode personNode) {
        if (personNode.hasNonNull("uuid")) {
            return personNode.get("uuid").asText();
        }
        if (personNode.hasNonNull("id")) {
            return personNode.get("id").asText();
        }
        return null;
    }

    private String normalizeHost(String apiHost) {
        if (!StringUtils.hasText(apiHost)) {
            throw new PosthogClientException("PostHog apiHost must not be empty");
        }
        String candidate = apiHost.trim();
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if (host != null && host.contains(".i.posthog.com")) {
                String normalizedHost = host.replace(".i.posthog.com", ".posthog.com");
                String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
                String normalized = scheme + "://" + normalizedHost;
                if (uri.getPort() != -1) {
                    normalized += ":" + uri.getPort();
                }
                log.info("Normalized PostHog host: fromHost={}, toHost={}", candidate, normalized);
                return normalized;
            }
            return candidate;
        } catch (IllegalArgumentException exception) {
            throw new PosthogClientException("Invalid PostHog apiHost: " + apiHost, exception);
        }
    }
}
