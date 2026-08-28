package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
 * than throw or grant. The lookup is provider-scoped ({@code (gitProviderId, subject)}), not login-based.
 */
class AccountRoleQueryServiceTest extends BaseUnitTest {

    private static final long PROVIDER_ID = 5L;
    private static final String SUBJECT = "583231"; // a provider's numeric user id

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
        when(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_ID, SUBJECT, "AI_REVIEW")
        ).thenReturn(repositorySaysYes);

        assertThat(service.hasFeatureFlag(PROVIDER_ID, SUBJECT, "AI_REVIEW")).isEqualTo(repositorySaysYes);
    }

    @Test
    void hasFeatureFlag_withBlankOrNullInput_shortCircuitsToFalseWithoutQuerying() {
        assertThat(service.hasFeatureFlag(PROVIDER_ID, null, "AI_REVIEW")).isFalse();
        assertThat(service.hasFeatureFlag(PROVIDER_ID, SUBJECT, null)).isFalse();
        assertThat(service.hasFeatureFlag(PROVIDER_ID, SUBJECT, "  ")).isFalse();
        assertThat(service.hasFeatureFlag(PROVIDER_ID, "  ", "AI_REVIEW")).isFalse();

        verify(accountFeatureRepository, never()).existsActiveFeatureForProviderSubject(
            anyLong(),
            anyString(),
            anyString()
        );
    }

    @Test
    void hasFeatureFlag_whenBackendThrows_failsClosed() {
        when(
            accountFeatureRepository.existsActiveFeatureForProviderSubject(PROVIDER_ID, SUBJECT, "AI_REVIEW")
        ).thenThrow(new RuntimeException("db down"));

        assertThat(service.hasFeatureFlag(PROVIDER_ID, SUBJECT, "AI_REVIEW")).isFalse();
    }
}
