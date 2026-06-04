package de.tum.cit.aet.hephaestus.core.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Pins the env → seed contract: a configured login provider is seeded once when absent, never
 * re-seeded when present (so an admin's later edits aren't clobbered), and unconfigured providers
 * are skipped. registration ids {@code github} / {@code gitlab-lrz} are preserved.
 */
class LoginProviderServiceTest extends BaseUnitTest {

    private final LoginProviderRepository repository = mock(LoginProviderRepository.class);

    private LoginProviderService service(AuthProperties.GithubLogin github, AuthProperties.GitlabLrzLogin gitlab) {
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
        return new LoginProviderService(repository, props);
    }

    private static AuthProperties.GitlabLrzLogin unconfiguredGitlab() {
        return new AuthProperties.GitlabLrzLogin("", "", URI.create("https://gitlab.lrz.de"));
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
}
