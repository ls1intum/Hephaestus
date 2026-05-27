package de.tum.cit.aet.hephaestus.integration.slack.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Slack {@code oauth.v2.access} client.
 *
 * <p>POSTs the authorisation {@code code} back to Slack with the app's
 * {@code client_id} / {@code client_secret} (form-encoded) and parses the bot-install
 * payload into {@link OAuthV2Access}. The endpoint base URL is configurable via
 * {@code hephaestus.integration.slack.api-base} so tests can point at a local
 * mock-web-server; production defaults to {@code https://slack.com}.
 *
 * <p>Secrets are never logged. On any error (transport failure, non-2xx, malformed
 * body, or Slack-reported {@code ok=false}) we throw {@link SlackOAuthException}
 * carrying the Slack error code (or a short transport hint), never the request body.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(SlackOAuthClient.class);

    private static final String TOKEN_ENDPOINT = "/api/oauth.v2.access";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public SlackOAuthClient(
        @Value("${hephaestus.integration.slack.client-id:}") String clientId,
        @Value("${hephaestus.integration.slack.client-secret:}") String clientSecret,
        @Value("${hephaestus.integration.slack.api-base:https://slack.com}") String apiBase
    ) {
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.restClient = RestClient.builder().baseUrl(stripTrailingSlash(apiBase)).build();
    }

    /**
     * Exchange an authorisation {@code code} for an installation's bot token + team metadata.
     *
     * @throws SlackOAuthException on HTTP failure, malformed body, or {@code ok=false}.
     */
    public OAuthV2Access exchangeCode(String code, @Nullable String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new SlackOAuthException("slack oauth client not configured");
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        if (redirectUri != null && !redirectUri.isBlank()) {
            body.add("redirect_uri", redirectUri);
        }

        OAuthV2Access response;
        try {
            response = restClient
                .post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(OAuthV2Access.class);
        } catch (RestClientException e) {
            // Transport, non-2xx, or deserialisation failure. Don't surface the exception
            // message verbatim — it can carry the request body or response excerpt.
            log.warn("Slack oauth.v2.access transport failure: {}", e.getClass().getSimpleName());
            throw new SlackOAuthException("transport_failure", e);
        }
        if (response == null) {
            throw new SlackOAuthException("empty_response");
        }
        if (!response.ok()) {
            String error = response.error() == null ? "unknown" : response.error();
            log.warn("Slack oauth.v2.access returned ok=false: error={}", error);
            throw new SlackOAuthException(error);
        }
        return response;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Slack {@code oauth.v2.access} response. Only the fields we read are bound;
     * unknown fields (enterprise install metadata, incoming_webhook, etc.) are ignored.
     *
     * <p>{@code expiresIn} + {@code refreshToken} are populated only for token-rotation
     * apps. We don't yet support rotation, so the strategy rejects responses that
     * carry these fields rather than silently dropping them.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OAuthV2Access(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("error") @Nullable String error,
        @JsonProperty("access_token") @Nullable String accessToken,
        @JsonProperty("bot_user_id") @Nullable String botUserId,
        @JsonProperty("app_id") @Nullable String appId,
        @JsonProperty("team") @Nullable Team team,
        @JsonProperty("scope") @Nullable String scope,
        @JsonProperty("expires_in") @Nullable Integer expiresIn,
        @JsonProperty("refresh_token") @Nullable String refreshToken
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Team(@JsonProperty("id") @Nullable String id, @JsonProperty("name") @Nullable String name) {}
    }
}
