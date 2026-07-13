package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WebClientConnectors;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OAuth2 user service for Outline registrations. Outline (v0.77+) is a plain OAuth2 provider —
 * <em>not</em> OIDC: it issues no id_token and has no discovery or GET-userinfo endpoint. Identity
 * comes from {@code POST {base}/api/auth.info} with the user's access token, which returns
 * {@code {data:{user:{id,name,email,avatarUrl}, team:{id,name}}}}.
 *
 * <p>This service performs that POST (the registration's userInfoUri, set by
 * {@code LoginProviderClientRegistrationRepository}, points at {@code /api/auth.info}) and flattens
 * the nested payload into top-level principal attributes: {@code id}, {@code name}, {@code email},
 * {@code avatar_url}, plus {@code team_id} — the flat key {@code AccountProvisioningService.teamIdOf}
 * already resolves, so the Outline team UUID lands on {@code IdentityLink.teamId} exactly like a
 * Slack workspace id. The principal keys on the immutable Outline user UUID ({@code id} — the
 * registration's userNameAttributeName), never a mutable name/email (nOAuth defence).
 *
 * <p>A missing user id or team id fails the login with an {@link OAuth2AuthenticationException}
 * (fail closed — a link without a stable subject or tenant key would corrupt identity resolution).
 * The outbound call rides the SSRF-guarded connector: the base URL is admin-configured (and
 * HTTPS/SSRF-validated on write), but defence in depth is the house rule for any URL that is not
 * ours. Routed ONLY for OUTLINE-typed registrations — see {@code AuthSecurityConfig#oauthUserService}.
 */
public class OutlineAuthInfoUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    static final String TEAM_ID_ATTRIBUTE = "team_id";
    private static final String SUBJECT_ATTRIBUTE = "id";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
        new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public OutlineAuthInfoUserService() {
        this(WebClient.builder().clientConnector(WebClientConnectors.ssrfGuarded()).build());
    }

    /** Test seam: inject a WebClient with a stubbed exchange function. */
    OutlineAuthInfoUserService(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String authInfoUri = userRequest.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri();
        Map<String, Object> body;
        try {
            body = webClient
                .post()
                .uri(authInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userRequest.getAccessToken().getTokenValue())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(JSON_OBJECT)
                .block(REQUEST_TIMEOUT);
        } catch (RuntimeException ex) {
            // Identity IS the product of this call — unlike GitHub's best-effort email enrichment,
            // an unreachable auth.info must fail the login, not proceed with an empty principal.
            throw authError("outline_auth_info_unavailable", "Outline auth.info call failed: " + ex.getMessage());
        }
        Map<String, Object> attributes = toAttributes(body);
        return new DefaultOAuth2User(Set.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, SUBJECT_ATTRIBUTE);
    }

    /**
     * Flattens Outline's nested {@code {data:{user,team}}} payload into principal attributes.
     * Fails closed when the user id or team id is missing.
     */
    static Map<String, Object> toAttributes(Map<String, Object> body) {
        Map<String, Object> data = nestedObject(body, "data");
        Map<String, Object> user = nestedObject(data, "user");
        Map<String, Object> team = nestedObject(data, "team");

        String userId = stringValue(user, "id");
        if (userId == null) {
            throw authError("outline_missing_user_id", "Outline auth.info returned no user id");
        }
        String teamId = stringValue(team, "id");
        if (teamId == null) {
            throw authError("outline_missing_team_id", "Outline auth.info returned no team id");
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(SUBJECT_ATTRIBUTE, userId);
        putIfPresent(attributes, "name", stringValue(user, "name"));
        putIfPresent(attributes, "email", stringValue(user, "email"));
        putIfPresent(attributes, "avatar_url", stringValue(user, "avatarUrl"));
        attributes.put(TEAM_ID_ATTRIBUTE, teamId);
        putIfPresent(attributes, "team_name", stringValue(team, "name"));
        return attributes;
    }

    private static OAuth2AuthenticationException authError(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description);
    }

    private static Map<String, Object> nestedObject(Map<String, Object> parent, String key) {
        if (parent != null && parent.get(key) instanceof Map<?, ?> nested) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) nested;
            return typed;
        }
        return Map.of();
    }

    private static String stringValue(Map<String, Object> map, String key) {
        return (map.get(key) instanceof String s && !s.isBlank()) ? s : null;
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
