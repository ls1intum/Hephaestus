package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScmCaptureConsentTest extends BaseUnitTest {
    @Test
    void shouldWithholdWhenAnotherContributorDeclinesRegardlessOfTheAboutHolder() {
        var identities = mock(AccountIdentityQuery.class);
        var accounts = mock(AccountDirectory.class);
        var suppression = mock(PersonProcessingSuppression.class);
        var copies = mock(PersonDataCopyRecorder.class);
        var provider = new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com");
        provider.setId(1L);
        var author = person(provider, 11);
        var reviewer = person(provider, 22);
        when(identities.resolveAccountId(1L, "11", null)).thenReturn(Optional.of(101L));
        when(identities.resolveAccountId(1L, "22", null)).thenReturn(Optional.of(102L));
        when(accounts.find(101))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        101, Account.Status.ACTIVE, "Author", AiChoice.CLOUD, Instant.now(), true)));
        when(accounts.find(102))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        102, Account.Status.ACTIVE, "Reviewer", AiChoice.NO_AI, Instant.now(), true)));
        var consent = new ScmCaptureConsent(identities, accounts, suppression, copies);
        assertThatThrownBy(() -> consent.require(List.of(author, reviewer)))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.CONSENT_WITHHELD));
    }

    @Test
    void shouldFailCaptureWhenContributorProviderIdentityIsUnavailable() {
        var provider = new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com");
        var consent = new ScmCaptureConsent(
                mock(AccountIdentityQuery.class),
                mock(AccountDirectory.class),
                mock(PersonProcessingSuppression.class),
                mock(PersonDataCopyRecorder.class));

        assertThatThrownBy(() -> consent.require(List.of(person(provider, 11))))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.CAPTURE_FAILED));
    }

    @Test
    void shouldWithholdAnErasingAccountBeforeCopyingItsContributorIdentity() {
        var identities = mock(AccountIdentityQuery.class);
        var accounts = mock(AccountDirectory.class);
        var suppression = mock(PersonProcessingSuppression.class);
        var copies = mock(PersonDataCopyRecorder.class);
        var provider = new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com");
        provider.setId(1L);
        when(identities.resolveAccountId(1L, "11", null)).thenReturn(Optional.of(101L));
        when(accounts.find(101))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        101, Account.Status.DELETING, "Author", AiChoice.CLOUD, Instant.now(), true)));
        var consent = new ScmCaptureConsent(identities, accounts, suppression, copies);
        assertThatThrownBy(() -> consent.require(List.of(person(provider, 11))))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.CONSENT_WITHHELD));
        verifyNoInteractions(copies);
    }

    @Test
    void shouldWithholdAnAccountWhoseOtherExactIdentityIsSuppressed() {
        var identities = mock(AccountIdentityQuery.class);
        var accounts = mock(AccountDirectory.class);
        var suppression = mock(PersonProcessingSuppression.class);
        var copies = mock(PersonDataCopyRecorder.class);
        var provider = new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com");
        provider.setId(1L);
        when(identities.resolveAccountId(1L, "11", null)).thenReturn(Optional.of(101L));
        when(accounts.find(101))
                .thenReturn(Optional.of(new AccountDirectory.AccountView(
                        101, Account.Status.ACTIVE, "Author", AiChoice.CLOUD, Instant.now(), true)));
        when(suppression.isAccountSuppressed(101)).thenReturn(true);
        var consent = new ScmCaptureConsent(identities, accounts, suppression, copies);
        assertThatThrownBy(() -> consent.require(List.of(person(provider, 11))))
                .isInstanceOfSatisfying(
                        ItemCaptureException.class,
                        e -> assertThat(e.code()).isEqualTo(ItemCaptureCode.CONSENT_WITHHELD));
        verifyNoInteractions(copies);
    }

    private User person(IdentityProvider provider, long nativeId) {
        var user = new User();
        user.setProvider(provider);
        user.setNativeId(nativeId);
        user.setLogin("person-" + nativeId);
        user.setType(User.Type.USER);
        return user;
    }
}
