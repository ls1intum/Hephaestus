package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountReferenceServiceTest extends BaseUnitTest {
    private final AccountIdentityQuery identities = mock(AccountIdentityQuery.class);
    private final AccountDirectory accounts = mock(AccountDirectory.class);
    private final GitProviderRegistry providers = mock(GitProviderRegistry.class);
    private final AccountReferenceService references = new AccountReferenceService(identities, accounts, providers);

    @Test
    void shouldExposeOnlyIdentityProfilesVisibleInTheRequestedWorkspace() {
        when(accounts.find(42L))
                .thenReturn(Optional.of(
                        new AccountDirectory.AccountView(42L, Account.Status.ACTIVE, "Developer", null, null, true)));
        when(identities.activeLinksForAccount(42L))
                .thenReturn(List.of(
                        new AccountIdentityQuery.IdentityView(1L, 10L, "github-native-key", ""),
                        new AccountIdentityQuery.IdentityView(2L, 20L, "foreign-native-key", "foreign-team")));
        when(providers.visibleInWorkspace(10L, 7L)).thenReturn(true);
        when(providers.providerTypeName(10L)).thenReturn("GITHUB");
        when(providers.profile(10L, "github-native-key", ""))
                .thenReturn(Optional.of(new GitProviderRegistry.IdentityProfile(
                        "developer", "Current mirror name", "avatar", "profile")));

        var reference = references.reference(7L, 42L);

        assertThat(reference.id()).isEqualTo("42");
        assertThat(reference.displayName()).isEqualTo("Developer");
        assertThat(reference.avatarUrl()).isEqualTo("avatar");
        assertThat(reference.identities()).singleElement().satisfies(identity -> {
            assertThat(identity.provider()).isEqualTo("GITHUB");
            assertThat(identity.login()).isEqualTo("developer");
            assertThat(identity.displayName()).isEqualTo("Current mirror name");
        });
    }

    @Test
    void shouldKeepUnclaimedAccountIdentityWhenItsVisibleMirrorHasNoProfile() {
        long accountId = 9_007_199_254_740_993L;
        when(accounts.find(accountId))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        accountId, Account.Status.UNCLAIMED, "Roster developer", null, null, true)));
        when(identities.activeLinksForAccount(accountId))
                .thenReturn(List.of(new AccountIdentityQuery.IdentityView(1L, 10L, "unavailable-profile", "")));
        when(providers.visibleInWorkspace(10L, 7L)).thenReturn(true);
        when(providers.providerTypeName(10L)).thenReturn("GITLAB");

        var reference = references.reference(7L, accountId);

        assertThat(reference.id()).isEqualTo("9007199254740993");
        assertThat(reference.status()).isEqualTo(Account.Status.UNCLAIMED);
        assertThat(reference.avatarUrl()).isNull();
        assertThat(reference.identities()).singleElement().satisfies(identity -> {
            assertThat(identity.provider()).isEqualTo("GITLAB");
            assertThat(identity.login()).isNull();
            assertThat(identity.displayName()).isNull();
            assertThat(identity.avatarUrl()).isNull();
        });
    }
}
