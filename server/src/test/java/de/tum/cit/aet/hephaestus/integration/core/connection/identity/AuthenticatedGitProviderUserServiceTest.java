package de.tum.cit.aet.hephaestus.integration.core.connection.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery.IdentityLinkView;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

/**
 * Locks the post-cutover identity resolution: {@code sub → Account → active IdentityLink → User}.
 * The Hephaestus cookie-JWT carries no {@code gitlab_id} / {@code github_id} claim, so the SCM
 * actor mirror must be provisioned from {@link AccountIdentityQuery}, never from JWT claims.
 */
class AuthenticatedGitProviderUserServiceTest extends BaseUnitTest {

    private static final long ACCOUNT_ID = 42L;
    private static final long GITLAB_PROVIDER_ID = 7L;
    private static final long GITHUB_PROVIDER_ID = 9L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    @Mock
    private AccountIdentityQuery accountIdentityQuery;

    @InjectMocks
    private AuthenticatedGitProviderUserService service;

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "ES256")
            .subject(String.valueOf(ACCOUNT_ID))
            .claim("preferred_username", "gitlabuser")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveOrProvision_provisionsGitLabUserFromIdentityLink_whenNoClaimPresent() {
        // No pre-existing SCM user → must provision from the account's GitLab IdentityLink.
        when(userRepository.getCurrentUser()).thenReturn(Optional.empty());
        IdentityLinkView gitlab = view(100L, GITLAB_PROVIDER_ID, "18024", "gitlabuser");
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(gitlab));
        GitProvider provider = gitProvider(GITLAB_PROVIDER_ID, GitProviderType.GITLAB, "https://gitlab.lrz.de");
        when(gitProviderRepository.findById(GITLAB_PROVIDER_ID)).thenReturn(Optional.of(provider));

        User provisioned = user(555L, "gitlabuser", 18024L, provider);
        when(userRepository.findByLoginAndProviderId("gitlabuser", GITLAB_PROVIDER_ID)).thenReturn(
            Optional.of(provisioned)
        );
        when(userRepository.findById(555L)).thenReturn(Optional.of(provisioned));

        Optional<User> result = service.resolveOrProvisionCurrentUser(null);

        assertThat(result).containsSame(provisioned);
        // native_id = numeric subject; login = usernameAtSignup; never reads a JWT claim.
        verify(userRepository).upsertUser(
            eq(18024L),
            eq(GITLAB_PROVIDER_ID),
            eq("gitlabuser"),
            anyString(),
            anyString(),
            anyString(),
            eq("USER"),
            any(),
            any(),
            any()
        );
        // IdentityLink → ExternalActor wiring is closed idempotently.
        verify(accountIdentityQuery).linkExternalActor(100L, 555L);
    }

    @Test
    void resolveOrProvision_prefersGitLabOverGitHub_whenAccountHasBoth() {
        when(userRepository.getCurrentUser()).thenReturn(Optional.empty());
        IdentityLinkView github = view(200L, GITHUB_PROVIDER_ID, "999", "ghuser");
        IdentityLinkView gitlab = view(100L, GITLAB_PROVIDER_ID, "18024", "gitlabuser");
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(github, gitlab));
        GitProvider gh = gitProvider(GITHUB_PROVIDER_ID, GitProviderType.GITHUB, "https://github.com");
        GitProvider gl = gitProvider(GITLAB_PROVIDER_ID, GitProviderType.GITLAB, "https://gitlab.lrz.de");
        lenient().when(gitProviderRepository.findById(GITHUB_PROVIDER_ID)).thenReturn(Optional.of(gh));
        when(gitProviderRepository.findById(GITLAB_PROVIDER_ID)).thenReturn(Optional.of(gl));
        User provisioned = user(555L, "gitlabuser", 18024L, gl);
        when(userRepository.findByLoginAndProviderId("gitlabuser", GITLAB_PROVIDER_ID)).thenReturn(
            Optional.of(provisioned)
        );
        when(userRepository.findById(555L)).thenReturn(Optional.of(provisioned));

        service.resolveOrProvisionCurrentUser(null);

        verify(userRepository).upsertUser(
            eq(18024L),
            eq(GITLAB_PROVIDER_ID),
            eq("gitlabuser"),
            anyString(),
            anyString(),
            anyString(),
            eq("USER"),
            any(),
            any(),
            any()
        );
    }

    @Test
    void ensureGitLab_succeeds_forGitLabLoginOnlyUser() {
        IdentityLinkView gitlab = view(100L, GITLAB_PROVIDER_ID, "18024", "gitlabuser");
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(gitlab));
        GitProvider gl = gitProvider(GITLAB_PROVIDER_ID, GitProviderType.GITLAB, "https://gitlab.lrz.de");
        when(gitProviderRepository.findById(GITLAB_PROVIDER_ID)).thenReturn(Optional.of(gl));
        User provisioned = user(555L, "gitlabuser", 18024L, gl);
        when(userRepository.findByLoginAndProviderId("gitlabuser", GITLAB_PROVIDER_ID)).thenReturn(
            Optional.of(provisioned)
        );
        lenient().when(userRepository.findById(555L)).thenReturn(Optional.of(provisioned));

        // No exception — a GitLab-logged-in user is allowed to create a GitLab workspace.
        service.ensureCurrentGitLabUserExists("https://gitlab.lrz.de");

        verify(accountIdentityQuery).linkExternalActor(100L, 555L);
    }

    @Test
    void ensureGitLab_throwsConflict_forGitHubOnlyUser() {
        IdentityLinkView github = view(200L, GITHUB_PROVIDER_ID, "999", "ghuser");
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of(github));
        GitProvider gh = gitProvider(GITHUB_PROVIDER_ID, GitProviderType.GITHUB, "https://github.com");
        when(gitProviderRepository.findById(GITHUB_PROVIDER_ID)).thenReturn(Optional.of(gh));

        assertThatThrownBy(() -> service.ensureCurrentGitLabUserExists("https://gitlab.lrz.de"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).upsertUser(
            anyLong(),
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void resolveOrProvision_returnsEmpty_whenAccountHasNoLinks() {
        when(userRepository.getCurrentUser()).thenReturn(Optional.empty());
        when(accountIdentityQuery.activeLinksForAccount(ACCOUNT_ID)).thenReturn(List.of());

        assertThat(service.resolveOrProvisionCurrentUser(null)).isEmpty();
    }

    private static IdentityLinkView view(long linkId, long providerId, String subject, String username) {
        return new IdentityLinkView(linkId, providerId, subject, username, "Display", null, null, null);
    }

    private static GitProvider gitProvider(long id, GitProviderType type, String serverUrl) {
        GitProvider p = new GitProvider(type, serverUrl);
        p.setId(id);
        return p;
    }

    private static User user(long id, String login, long nativeId, GitProvider provider) {
        User u = new User();
        u.setId(id);
        u.setNativeId(nativeId);
        u.setProvider(provider);
        u.setLogin(login);
        u.setAvatarUrl("");
        u.setHtmlUrl("");
        u.setType(User.Type.USER);
        return u;
    }
}
