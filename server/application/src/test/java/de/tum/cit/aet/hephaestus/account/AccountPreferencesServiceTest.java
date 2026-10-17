package de.tum.cit.aet.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AccountDirectoryService;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountPreferencesServiceTest extends BaseUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldChangeAccountDeliveryChoiceWhenSettingsAreSaved(boolean enabled) {
        var accounts = mock(AccountRepository.class);
        var account = new Account("Octocat");
        account.setId(42L);
        when(accounts.findByIdForUpdate(42L)).thenReturn(Optional.of(account));
        var result = new AccountPreferencesService(new AccountDirectoryService(accounts, Clock.systemUTC()))
                .updateUserSettings(42L, new UserSettingsDTO(enabled));
        assertThat(result.practiceFeedbackDeliveryEnabled()).isEqualTo(enabled);
        assertThat(account.isFeedbackDeliveryEnabled()).isEqualTo(enabled);
    }
}
