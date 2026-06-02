package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the fail-closed contract of {@link AccountRoleQueryService#hasFeatureFlag}: it is on the
 * authorization path, so a missing input or a backend failure must deny (return {@code false}) rather
 * than throw or grant.
 */
class AccountRoleQueryServiceTest extends BaseUnitTest {

    private AccountFeatureRepository accountFeatureRepository;
    private AccountRoleQueryService service;

    @BeforeEach
    void setUp() {
        accountFeatureRepository = mock(AccountFeatureRepository.class);
        service = new AccountRoleQueryService(accountFeatureRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void hasFeatureFlag_passesThroughRepositoryVerdict(boolean repositorySaysYes) {
        when(accountFeatureRepository.existsActiveFeatureForLogin("octocat", "AI_REVIEW")).thenReturn(
            repositorySaysYes
        );

        assertThat(service.hasFeatureFlag("octocat", "AI_REVIEW")).isEqualTo(repositorySaysYes);
    }

    @Test
    void hasFeatureFlag_withBlankOrNullInput_shortCircuitsToFalseWithoutQuerying() {
        assertThat(service.hasFeatureFlag(null, "AI_REVIEW")).isFalse();
        assertThat(service.hasFeatureFlag("octocat", null)).isFalse();
        assertThat(service.hasFeatureFlag("octocat", "  ")).isFalse();

        verify(accountFeatureRepository, never()).existsActiveFeatureForLogin(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void hasFeatureFlag_whenBackendThrows_failsClosed() {
        when(accountFeatureRepository.existsActiveFeatureForLogin("octocat", "AI_REVIEW")).thenThrow(
            new RuntimeException("db down")
        );

        assertThat(service.hasFeatureFlag("octocat", "AI_REVIEW")).isFalse();
    }
}
