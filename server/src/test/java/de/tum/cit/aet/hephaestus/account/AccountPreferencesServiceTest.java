package de.tum.cit.aet.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClient;
import de.tum.cit.aet.hephaestus.analytics.posthog.PosthogClientException;
import de.tum.cit.aet.hephaestus.core.auth.spi.ConsentSource;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchConsentAudit;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

/**
 * S4 research opt-out SPI. Deterministic: mocks lock the <strong>lenient</strong> {@code setForLogin} contract —
 * a missing analytics subject or a PostHog failure never fails the opt-out (contrast {@code updateUserSettings},
 * which throws {@code BAD_REQUEST}/{@code BAD_GATEWAY}), and an opt-out always appends the audit event.
 */
class AccountPreferencesServiceTest extends BaseUnitTest {

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectProvider<PosthogClient> posthogClientProvider;

    @Mock
    private ObjectProvider<ResearchConsentAudit> researchConsentAuditProvider;

    @Mock
    private PosthogClient posthogClient;

    @Mock
    private ResearchConsentAudit audit;

    private AccountPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new AccountPreferencesService(
            userPreferencesRepository,
            userRepository,
            posthogClientProvider,
            researchConsentAuditProvider
        );
    }

    private static User user(long id, String login) {
        User u = new User();
        u.setId(id);
        u.setLogin(login);
        return u;
    }

    private UserPreferences prefs(User u, boolean participate) {
        UserPreferences p = new UserPreferences(u);
        p.setParticipateInResearch(participate);
        return p;
    }

    @Test
    void optOut_missingSubject_stillSucceeds_andWritesAudit() {
        User u = user(42L, "octocat");
        UserPreferences p = prefs(u, true);
        when(userRepository.findByLogin("octocat")).thenReturn(Optional.of(u));
        when(userPreferencesRepository.findByUserId(42L)).thenReturn(Optional.of(p));
        when(posthogClientProvider.getIfAvailable()).thenReturn(posthogClient);
        // No PostHog person matches the fallback (user-id) distinct id — returns false, must NOT fail the opt-out.
        when(posthogClient.deletePersonData("42")).thenReturn(false);
        when(researchConsentAuditProvider.getIfAvailable()).thenReturn(audit);

        service.setForLogin("octocat", false, ConsentSource.SLACK_APP_HOME);

        assertThat(p.isParticipateInResearch()).isFalse();
        verify(userPreferencesRepository).save(p);
        verify(posthogClient).deletePersonData("42"); // subjectId absent → falls back to the user id
        verify(audit).recordOptOut("octocat", ConsentSource.SLACK_APP_HOME);
    }

    @Test
    void optOut_posthogFailure_stillSucceeds_andWritesAudit() {
        User u = user(42L, "octocat");
        UserPreferences p = prefs(u, true);
        when(userRepository.findByLogin("octocat")).thenReturn(Optional.of(u));
        when(userPreferencesRepository.findByUserId(42L)).thenReturn(Optional.of(p));
        when(posthogClientProvider.getIfAvailable()).thenReturn(posthogClient);
        when(posthogClient.deletePersonData("42")).thenThrow(new PosthogClientException("posthog down"));
        when(researchConsentAuditProvider.getIfAvailable()).thenReturn(audit);

        assertThatCode(() ->
            service.setForLogin("octocat", false, ConsentSource.SLACK_APP_HOME)
        ).doesNotThrowAnyException();

        assertThat(p.isParticipateInResearch()).isFalse();
        verify(userPreferencesRepository).save(p);
        verify(audit).recordOptOut("octocat", ConsentSource.SLACK_APP_HOME);
    }

    @Test
    void optOut_withPosthogAndAuditDisabled_stillSucceeds() {
        User u = user(42L, "octocat");
        UserPreferences p = prefs(u, true);
        when(userRepository.findByLogin("octocat")).thenReturn(Optional.of(u));
        when(userPreferencesRepository.findByUserId(42L)).thenReturn(Optional.of(p));
        when(posthogClientProvider.getIfAvailable()).thenReturn(null); // analytics disabled off-server-role
        when(researchConsentAuditProvider.getIfAvailable()).thenReturn(null); // audit adapter absent

        assertThatCode(() ->
            service.setForLogin("octocat", false, ConsentSource.SLACK_APP_HOME)
        ).doesNotThrowAnyException();

        assertThat(p.isParticipateInResearch()).isFalse();
        verify(userPreferencesRepository).save(p);
    }

    @Test
    void optIn_doesNotRevokeOrAudit() {
        User u = user(42L, "octocat");
        UserPreferences p = prefs(u, false);
        when(userRepository.findByLogin("octocat")).thenReturn(Optional.of(u));
        when(userPreferencesRepository.findByUserId(42L)).thenReturn(Optional.of(p));

        service.setForLogin("octocat", true, ConsentSource.SETTINGS_UI);

        assertThat(p.isParticipateInResearch()).isTrue();
        verify(userPreferencesRepository).save(p);
        verifyNoInteractions(posthogClientProvider, researchConsentAuditProvider);
    }

    @Test
    void blankLogin_isNoOp() {
        service.setForLogin("   ", false, ConsentSource.SLACK_APP_HOME);

        verifyNoInteractions(
            userRepository,
            userPreferencesRepository,
            posthogClientProvider,
            researchConsentAuditProvider
        );
    }

    @Test
    void unknownLogin_isLenientNoOp() {
        when(userRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        assertThatCode(() ->
            service.setForLogin("ghost", false, ConsentSource.SLACK_APP_HOME)
        ).doesNotThrowAnyException();

        verify(userPreferencesRepository, never()).save(any());
        verifyNoInteractions(researchConsentAuditProvider);
    }
}
