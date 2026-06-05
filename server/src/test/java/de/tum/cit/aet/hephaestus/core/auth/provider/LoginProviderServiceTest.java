package de.tum.cit.aet.hephaestus.core.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pins the env → seed contract: a configured login provider is seeded once when absent, never
 * re-seeded when present (so an admin's later edits aren't clobbered), and unconfigured providers
 * are skipped. registration ids {@code github} / {@code gitlab} are stable across reboots.
 */
class LoginProviderServiceTest extends BaseUnitTest {

    private final LoginProviderRepository repository = mock(LoginProviderRepository.class);
    private final LoginProviderClientRegistrationRepository registrationCache = mock(
        LoginProviderClientRegistrationRepository.class
    );

    private LoginProviderService service(AuthProperties.GithubLogin github, AuthProperties.GitlabLogin gitlab) {
        AuthProperties props = new AuthProperties(
            URI.create("http://localhost:8080"),
            "hephaestus-spa",
            Duration.ofMinutes(15),
            "__Host-HEPHAESTUS_AT",
            "",
            Duration.ofHours(48),
            github,
            gitlab,
            List.of(),
            "",
            Duration.ofHours(1)
        );
        return new LoginProviderService(repository, registrationCache, props);
    }

    private LoginProviderService adminService() {
        return service(new AuthProperties.GithubLogin("", ""), unconfiguredGitlab());
    }

    private static AuthProperties.GitlabLogin unconfiguredGitlab() {
        return new AuthProperties.GitlabLogin("", "", URI.create("https://gitlab.com"), "GitLab");
    }

    private static LoginProviderService.Draft gitlabDraft(String registrationId, String baseUrl, String scopes) {
        return new LoginProviderService.Draft(
            registrationId,
            LoginProvider.ProviderType.GITLAB,
            "Self-hosted GitLab",
            baseUrl,
            "client-id",
            "client-secret",
            scopes
        );
    }

    @Test
    void seedsConfiguredGithubWhenAbsent() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(new AuthProperties.GithubLogin("client-id", "secret"), unconfiguredGitlab()).seedFromEnvOnStartup();

        ArgumentCaptor<LoginProvider> captor = ArgumentCaptor.forClass(LoginProvider.class);
        verify(repository).save(captor.capture());
        LoginProvider seeded = captor.getValue();
        assertThat(seeded.getRegistrationId()).isEqualTo("github");
        assertThat(seeded.getType()).isEqualTo(LoginProvider.ProviderType.GITHUB);
        assertThat(seeded.getBaseUrl()).isEqualTo("https://github.com");
        assertThat(seeded.isSeededFromEnv()).isTrue();
        assertThat(seeded.isEnabled()).isTrue();
    }

    @Test
    void seedsConfiguredGitlabWithGenericIdAndConfiguredLabel() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);
        // A self-hosted deployer only sets base-url + display-name + credentials; the registration id
        // stays the stable, instance-agnostic "gitlab" so IdentityLinks and the admin allowlist resolve.
        AuthProperties.GitlabLogin gitlab = new AuthProperties.GitlabLogin(
            "client-id",
            "secret",
            URI.create("https://gitlab.lrz.de"),
            "gitlab.lrz.de"
        );

        service(new AuthProperties.GithubLogin("", ""), gitlab).seedFromEnvOnStartup();

        ArgumentCaptor<LoginProvider> captor = ArgumentCaptor.forClass(LoginProvider.class);
        verify(repository).save(captor.capture());
        LoginProvider seeded = captor.getValue();
        assertThat(seeded.getRegistrationId()).isEqualTo("gitlab");
        assertThat(seeded.getType()).isEqualTo(LoginProvider.ProviderType.GITLAB);
        assertThat(seeded.getBaseUrl()).isEqualTo("https://gitlab.lrz.de");
        assertThat(seeded.getDisplayName()).isEqualTo("gitlab.lrz.de");
        assertThat(seeded.isSeededFromEnv()).isTrue();
        assertThat(seeded.isEnabled()).isTrue();
    }

    @Test
    void doesNotReseedWhenAlreadyPresent() {
        when(repository.existsByRegistrationId(any())).thenReturn(true);

        service(new AuthProperties.GithubLogin("client-id", "secret"), unconfiguredGitlab()).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void skipsUnconfiguredProviders() {
        when(repository.existsByRegistrationId(any())).thenReturn(false);

        service(new AuthProperties.GithubLogin("", ""), unconfiguredGitlab()).seedFromEnvOnStartup();

        verify(repository, never()).save(any());
    }

    @Test
    void createSelfHostedGitlabDefaultsScopesAndEvictsCache() {
        when(repository.existsByRegistrationId("gitlab-acme")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginProvider created = adminService().create(gitlabDraft("gitlab-acme", "https://gitlab.acme.test/", null));

        assertThat(created.getType()).isEqualTo(LoginProvider.ProviderType.GITLAB);
        assertThat(created.getBaseUrl()).isEqualTo("https://gitlab.acme.test"); // trailing slash stripped
        assertThat(created.getScopes()).isEqualTo("openid profile email read_user"); // defaulted by type
        assertThat(created.isSeededFromEnv()).isFalse();
        verify(registrationCache).evict("gitlab-acme");
    }

    @Test
    void createRejectsDuplicateRegistrationId() {
        when(repository.existsByRegistrationId("gitlab-acme")).thenReturn(true);

        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("gitlab-acme", "https://gitlab.acme.test", null))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsMalformedRegistrationId() {
        assertThatThrownBy(() ->
            adminService().create(gitlabDraft("Bad Id!", "https://gitlab.acme.test", null))
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disableRefusesWhenItIsTheLastEnabledProvider() {
        LoginProvider only = new LoginProvider();
        only.setRegistrationId("github");
        only.setType(LoginProvider.ProviderType.GITHUB);
        only.setEnabled(true);
        when(repository.findByRegistrationId("github")).thenReturn(java.util.Optional.of(only));
        when(repository.findByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(only));

        assertThatThrownBy(() ->
            adminService().update("github", new LoginProviderService.Patch(null, null, null, null, null, false))
        ).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }
}
