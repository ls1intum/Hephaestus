package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST client for GitLab Personal Access Token self-inspection and rotation.
 *
 * <p>Uses the GitLab {@code /personal_access_tokens/self} endpoints to check
 * token expiry and rotate tokens before they expire. The self-rotation API
 * immediately revokes the old token and returns a new one.
 *
 * <p><b>Token family security:</b> GitLab tracks token families. If a revoked
 * token is used after rotation, GitLab kills <em>all</em> tokens in the family.
 * This makes it critical to persist the new token immediately after rotation.
 *
 * @see <a href="https://docs.gitlab.com/ee/api/personal_access_tokens.html#rotate-a-personal-access-token">GitLab PAT Rotation</a>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabTokenRotationClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String SELF_TOKEN_ENDPOINT = "/api/v4/personal_access_tokens/self";

    private final GitLabTokenService tokenService;
    private final WebClient webClient;

    public GitLabTokenRotationClient(GitLabTokenService tokenService, WebClient.Builder webClientBuilder) {
        this.tokenService = tokenService;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Retrieves information about the PAT used for the given scope.
     *
     * @param scopeId the workspace/scope ID
     * @return token info including expiry date
     */
    public TokenInfo getTokenInfo(Long scopeId) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient
            .get()
            .uri(serverUrl + SELF_TOKEN_ENDPOINT)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .bodyToMono(Map.class)
            .block(REQUEST_TIMEOUT);

        if (response == null) {
            throw new IllegalStateException("Empty response from GitLab token self-introspection");
        }

        long id = ((Number) response.get("id")).longValue();
        String name = (String) response.get("name");
        String expiresAtStr = (String) response.get("expires_at");
        LocalDate expiresAt = expiresAtStr != null ? LocalDate.parse(expiresAtStr) : null;

        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) response.get("scopes");
        boolean active = Boolean.TRUE.equals(response.get("active"));

        return new TokenInfo(id, name, expiresAt, scopes != null ? scopes : List.of(), active);
    }

    /**
     * Rotates the PAT used for the given scope.
     *
     * <p><b>WARNING:</b> The old token is <em>immediately</em> revoked. The caller
     * must persist the returned token value before making any further API calls.
     *
     * @param scopeId   the workspace/scope ID
     * @param expiresAt new expiry date for the rotated token
     * @return the new token value and expiry date
     */
    public RotatedToken rotateToken(Long scopeId, LocalDate expiresAt) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient
            .post()
            .uri(serverUrl + SELF_TOKEN_ENDPOINT + "/rotate")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .bodyValue(Map.of("expires_at", expiresAt.toString()))
            .retrieve()
            .bodyToMono(Map.class)
            .block(REQUEST_TIMEOUT);

        if (response == null) {
            throw new IllegalStateException("Empty response from GitLab token rotation");
        }

        String newToken = (String) response.get("token");
        String newExpiresAtStr = (String) response.get("expires_at");
        LocalDate newExpiresAt = newExpiresAtStr != null ? LocalDate.parse(newExpiresAtStr) : expiresAt;

        log.info("Rotated GitLab PAT: scopeId={}, newExpiresAt={}", scopeId, newExpiresAt);
        return new RotatedToken(newToken, newExpiresAt);
    }

    /**
     * Token self-inspection result.
     *
     * @param id        the token ID
     * @param name      the token name
     * @param expiresAt expiry date (null if no expiry)
     * @param scopes    list of scopes granted to the token
     * @param active    whether the token is currently active
     */
    public record TokenInfo(long id, String name, @Nullable LocalDate expiresAt, List<String> scopes, boolean active) {
        @Override
        public String toString() {
            return "TokenInfo[id=" + id + ", name=" + name + ", expiresAt=" + expiresAt + ", active=" + active + "]";
        }
    }

    /**
     * Result of a token rotation.
     *
     * @param token     the new token value (the old token is now revoked)
     * @param expiresAt the new expiry date
     */
    public record RotatedToken(String token, LocalDate expiresAt) {
        @Override
        public String toString() {
            return "RotatedToken[expiresAt=" + expiresAt + "]";
        }
    }
}
