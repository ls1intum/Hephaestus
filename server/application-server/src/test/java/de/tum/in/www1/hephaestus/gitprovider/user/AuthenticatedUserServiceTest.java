package de.tum.in.www1.hephaestus.gitprovider.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link AuthenticatedUserService}.
 * <p>
 * Production motivation: a Keycloak account can federate multiple IdPs (GitHub + GitLab-LRZ),
 * in which case the app DB holds one {@code "user"} row per linked IdP and any lookup that
 * considers only {@code preferred_username} silently misses the row on the other provider.
 * These tests lock in the claim-driven resolution contract.
 */
@DisplayName("AuthenticatedUserService")
class AuthenticatedUserServiceTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns empty when no JWT is present")
    void returnsEmptyWithoutAuth() {
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);

        assertThat(service.findPrimaryUser()).isEmpty();
        assertThat(service.findAllLinkedUsers()).isEmpty();
    }

    @Test
    @DisplayName("returns empty for a legacy token that carries only preferred_username (no identity claim)")
    void refusesToLookUpByLoginAlone() {
        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "someone"));

        // Explicitly asserts the security-critical invariant: preferred_username is not a
        // fallback identity lookup. A token missing the IdP-mapped id claim must not
        // resolve to a user row just because a login happens to match.
        assertThat(service.findAllLinkedUsers()).isEmpty();
        assertThat(service.findPrimaryUser()).isEmpty();
    }

    @Test
    @DisplayName("aggregates both GitHub and GitLab rows when both claims are present")
    void aggregatesAcrossLinkedProviders() {
        User githubUser = makeUser(101L, "FelixTJDietrich", GitProviderType.GITHUB, 5898705L);
        User gitlabUser = makeUser(202L, "ga84xah", GitProviderType.GITLAB, 18024L);
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITHUB), eq(5898705L))).thenReturn(
            List.of(githubUser)
        );
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITLAB), eq(18024L))).thenReturn(
            List.of(gitlabUser)
        );

        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "ga84xah", "github_id", 5898705L, "gitlab_id", 18024L));

        assertThat(service.findAllLinkedUsers()).containsExactlyInAnyOrder(githubUser, gitlabUser);
    }

    @Test
    @DisplayName("findPrimaryUser prefers the linked row whose login matches preferred_username")
    void prefersMatchingLogin() {
        // The GitHub login differs from Keycloak's preferred_username — the original multi-IdP bug.
        User githubUser = makeUser(101L, "FelixTJDietrich", GitProviderType.GITHUB, 5898705L);
        User gitlabUser = makeUser(202L, "ga84xah", GitProviderType.GITLAB, 18024L);
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITHUB), eq(5898705L))).thenReturn(
            List.of(githubUser)
        );
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITLAB), eq(18024L))).thenReturn(
            List.of(gitlabUser)
        );

        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "ga84xah", "github_id", 5898705L, "gitlab_id", 18024L));

        assertThat(service.findPrimaryUser()).contains(gitlabUser);
    }

    @Test
    @DisplayName("findPrimaryUser falls back to lowest-id row when no login matches")
    void fallsBackToLowestIdWhenNoLoginMatches() {
        User githubUser = makeUser(101L, "felixfromgh", GitProviderType.GITHUB, 5898705L);
        User gitlabUser = makeUser(202L, "felixfromlab", GitProviderType.GITLAB, 18024L);
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITHUB), eq(5898705L))).thenReturn(
            List.of(githubUser)
        );
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITLAB), eq(18024L))).thenReturn(
            List.of(gitlabUser)
        );

        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "someone-else", "github_id", 5898705L, "gitlab_id", 18024L));

        assertThat(service.findPrimaryUser()).contains(githubUser);
    }

    @Test
    @DisplayName("findLinkedUserForProvider picks the row matching the requested provider type")
    void findsProviderSpecificRow() {
        User githubUser = makeUser(101L, "FelixTJDietrich", GitProviderType.GITHUB, 5898705L);
        User gitlabUser = makeUser(202L, "ga84xah", GitProviderType.GITLAB, 18024L);
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITHUB), eq(5898705L))).thenReturn(
            List.of(githubUser)
        );
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITLAB), eq(18024L))).thenReturn(
            List.of(gitlabUser)
        );

        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "ga84xah", "github_id", 5898705L, "gitlab_id", 18024L));

        assertThat(service.findLinkedUserForProvider(GitProviderType.GITHUB)).contains(githubUser);
        assertThat(service.findLinkedUserForProvider(GitProviderType.GITLAB)).contains(gitlabUser);
    }

    @Test
    @DisplayName("findLinkedUserForProvider falls back to primary when the requested provider is not linked")
    void fallsBackToPrimaryWhenProviderMissing() {
        User githubUser = makeUser(101L, "FelixTJDietrich", GitProviderType.GITHUB, 5898705L);
        when(userRepository.findAllByProviderTypeAndNativeId(eq(GitProviderType.GITHUB), eq(5898705L))).thenReturn(
            List.of(githubUser)
        );

        AuthenticatedUserService service = new AuthenticatedUserService(userRepository);
        setJwt(Map.of("preferred_username", "FelixTJDietrich", "github_id", 5898705L));

        assertThat(service.findLinkedUserForProvider(GitProviderType.GITLAB)).contains(githubUser);
    }

    private static User makeUser(long id, String login, GitProviderType providerType, long nativeId) {
        GitProvider provider = new GitProvider();
        provider.setType(providerType);
        provider.setServerUrl(providerType == GitProviderType.GITHUB ? "https://github.com" : "https://gitlab.example");
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setName(login);
        user.setAvatarUrl("");
        user.setHtmlUrl("");
        user.setType(User.Type.USER);
        user.setNativeId(nativeId);
        user.setProvider(provider);
        return user;
    }

    private static void setJwt(Map<String, Object> claims) {
        Map<String, Object> claimsCopy = new HashMap<>(claims);
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "HS256")
            .claims(map -> map.putAll(claimsCopy))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
