package de.tum.cit.aet.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class AccountPreferencesQueryAdapterTest extends BaseUnitTest {
    @Test
    void shouldPropagateLookupFailureWhenAccountStorageIsUnavailable() {
        var accounts = mock(AccountDirectory.class);
        var adapter = new AccountPreferencesQueryAdapter(accounts, mock(ResearchParticipationQuery.class));
        when(accounts.find(7L)).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> adapter.preferencesForAccount(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
