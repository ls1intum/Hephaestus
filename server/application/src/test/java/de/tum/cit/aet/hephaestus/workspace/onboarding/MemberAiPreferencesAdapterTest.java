package de.tum.cit.aet.hephaestus.workspace.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemberAiPreferencesAdapterTest extends BaseUnitTest {
    private final WorkspaceOnboardingSettingsRepository settings = mock(WorkspaceOnboardingSettingsRepository.class);
    private final AccountDirectory accounts = mock(AccountDirectory.class);
    private final MemberAiPreferencesAdapter preferences = new MemberAiPreferencesAdapter(settings, accounts);

    @Test
    void shouldApplyTheAccountChoiceWhenAnyWorkspaceReviewsItsDeveloper() {
        var account = new Account("Developer");
        account.setAiChoice(AiChoice.NO_AI);
        when(accounts.find(10L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        10L, Account.Status.ACTIVE, "Developer", account.getAiChoice(), Instant.now(), true)));
        assertThat(preferences.forDeveloper(1L, 10L).permitsAi()).isFalse();
        assertThat(preferences.forDeveloper(2L, 10L).permitsAi()).isFalse();
    }

    @Test
    void shouldCarryTheAccountCeilingWhenTheWorkspaceHasNoSetupPage() {
        var account = new Account("Developer");
        account.setAiChoice(AiChoice.IN_HOUSE_ONLY);
        when(accounts.find(10L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        10L, Account.Status.ACTIVE, "Developer", account.getAiChoice(), Instant.now(), true)));
        var decision = preferences.forDeveloper(1L, 10L);
        assertThat(decision.choiceRequired()).isTrue();
        assertThat(decision.choice()).isEqualTo(AiChoice.IN_HOUSE_ONLY);
    }

    @Test
    void shouldRefuseUnansweredAccountsWhenTheWorkspaceRequiresAChoice() {
        var policy = new WorkspaceOnboardingSettings();
        policy.setAiChoiceRequired(true);
        when(settings.findByWorkspaceId(1L)).thenReturn(Optional.of(policy));
        when(accounts.find(10L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        10L, Account.Status.UNCLAIMED, "Roster developer", null, null, true)));
        assertThat(preferences.forDeveloper(1L, 10L).permitsAi()).isFalse();
    }

    @Test
    void shouldNotRequireAPersonChoiceWhenTheAssignmentExplicitlyHasNobody() {
        var policy = new WorkspaceOnboardingSettings();
        policy.setAiChoiceRequired(true);
        when(settings.findByWorkspaceId(1L)).thenReturn(Optional.of(policy));

        var decision = preferences.forDeveloper(1L, null);

        assertThat(decision.choiceRequired()).isFalse();
        assertThat(decision.choice()).isNull();
        assertThat(decision.permitsAi()).isTrue();
    }

    @Test
    void shouldPermitAnUnansweredAccountWhenTheWorkspaceChoiceIsOptional() {
        when(accounts.find(10L))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        10L, Account.Status.UNCLAIMED, "Roster developer", null, null, true)));
        assertThat(preferences.forDeveloper(1L, 10L).choice()).isNull();
        assertThat(preferences.forDeveloper(1L, 10L).permitsAi()).isTrue();
    }

    @Test
    void shouldRejectMissingAccountInsteadOfTreatingItAsUnanswered() {
        assertThatThrownBy(() -> preferences.forDeveloper(1L, 10L)).isInstanceOf(EntityNotFoundException.class);
    }
}
