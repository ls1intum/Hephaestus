package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProviderRepository;
import de.tum.cit.aet.hephaestus.testconfig.RealAuthIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AccountProvisioningIntegrationTest extends RealAuthIntegrationTest {

    @Autowired
    private AccountProvisioningService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private LoginProviderRepository loginProviderRepository;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void shouldRollBackNewlyClaimedAccountWhenTheEnclosingProvisioningTransactionFails() {
        String registration = "rollback-" + UUID.randomUUID();
        String subject = "subject-" + UUID.randomUUID();
        seedProvider(registration, LoginProvider.ProviderType.GITHUB);
        var createdId = new AtomicLong();
        var transaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    var resolved = service.resolveOrProvision(
                            registration,
                            subject,
                            principal(subject, "rollback@example.com", true, "Rollback developer"),
                            AuthIntentCookie.Intent.login(null, null));
                    createdId.set(persistedId(resolved.account().getId()));
                    throw new IllegalStateException("Provisioning aborted");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provisioning aborted");

        assertThat(createdId.get()).isPositive();
        assertThat(accountRepository.findById(createdId.get())).isEmpty();
        assertThat(identityRepository.findByAccount_Id(createdId.get())).isEmpty();
    }

    @Test
    void twoIdentitiesSharingOneEmailResolveToSeparateAccounts() {
        seedProvider("github-noauth-a", LoginProvider.ProviderType.GITHUB);
        seedProvider("gitlab-noauth-b", LoginProvider.ProviderType.GITLAB);
        String sharedEmail = "victim@shared.example";

        Account viaGithub = service.resolveOrProvision(
                        "github-noauth-a",
                        "gh-subject-victim",
                        principal("gh-subject-victim", sharedEmail, true, "victim"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();
        // A DIFFERENT provider + subject carrying the SAME verified email — the nOAuth attack
        // shape.
        Account viaGitlab = service.resolveOrProvision(
                        "gitlab-noauth-b",
                        "gl-subject-attacker",
                        principal("gl-subject-attacker", sharedEmail, true, "attacker"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();

        // Two distinct accounts: the shared email did NOT fold the second identity into the first.
        assertThat(persistedId(viaGitlab.getId())).isNotEqualTo(persistedId(viaGithub.getId()));
        // Both store the email as contact metadata (proving it WAS seen, just never used as a key).
        assertThat(viaGithub.getPrimaryEmail()).isEqualTo(sharedEmail);
        assertThat(viaGitlab.getPrimaryEmail()).isEqualTo(sharedEmail);
        // Each account owns exactly its own identity link, on its own provider+subject.
        assertThat(identityRepository.findActiveByAccountId(persistedId(viaGithub.getId())))
                .extracting(Identity::getProviderUserKey)
                .containsExactly("gh-subject-victim");
        assertThat(identityRepository.findActiveByAccountId(persistedId(viaGitlab.getId())))
                .extracting(Identity::getProviderUserKey)
                .containsExactly("gl-subject-attacker");
    }

    @Test
    void sameProviderDifferentSubjectSharingOneEmailAlsoSeparates() {
        seedProvider("github-samesubj", LoginProvider.ProviderType.GITHUB);
        String sharedEmail = "two-accounts@shared.example";

        Account first = service.resolveOrProvision(
                        "github-samesubj",
                        "subject-one",
                        principal("subject-one", sharedEmail, true, "one"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();
        Account second = service.resolveOrProvision(
                        "github-samesubj",
                        "subject-two",
                        principal("subject-two", sharedEmail, true, "two"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();

        // Even within ONE provider, the subject is the key — a shared email never merges subjects.
        assertThat(persistedId(second.getId())).isNotEqualTo(persistedId(first.getId()));
    }

    @Test
    void returningLoginWithSameProviderSubjectResolvesTheSameAccount() {
        seedProvider("github-returning", LoginProvider.ProviderType.GITHUB);

        Account firstLogin = service.resolveOrProvision(
                        "github-returning",
                        "returning-subject",
                        principal("returning-subject", "ada@returning.example", true, "ada"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();
        // A second login for the SAME (provider, subject) must reuse the account, not JIT a new one
        // —
        // even though the IdP now reports a different email (people change emails; the link is the
        // key).
        Account secondLogin = service.resolveOrProvision(
                        "github-returning",
                        "returning-subject",
                        principal("returning-subject", "ada-new@returning.example", true, "ada"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();

        assertThat(persistedId(secondLogin.getId())).isEqualTo(persistedId(firstLogin.getId()));
        // Idempotent: still exactly one identity link for the account.
        assertThat(identityRepository.findActiveByAccountId(persistedId(firstLogin.getId())))
                .hasSize(1);
    }

    @Test
    void deletingAccountCannotAttachANewNativeIdentityOrUpdateAnExistingLink() {
        seedProvider("github-deleting", LoginProvider.ProviderType.GITHUB);
        Account account = service.resolveOrProvision(
                        "github-deleting",
                        "original-native-subject",
                        principal("original-native-subject", "contact@example.test", true, "display"),
                        AuthIntentCookie.Intent.login(null, null))
                .account();
        long accountId = persistedId(account.getId());
        account.setStatus(Account.Status.DELETING);
        accountRepository.saveAndFlush(account);

        Assertions.assertThatThrownBy(() -> service.resolveOrProvision(
                        "github-deleting",
                        "new-native-subject",
                        principal("new-native-subject", "contact@example.test", true, "display"),
                        AuthIntentCookie.Intent.link(accountId, null)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        Assertions.assertThatThrownBy(() -> service.resolveOrProvision(
                        "github-deleting",
                        "original-native-subject",
                        principal("original-native-subject", "contact@example.test", true, "display"),
                        AuthIntentCookie.Intent.login(null, null)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(identityRepository.findActiveByAccountId(accountId))
                .extracting(Identity::getProviderUserKey)
                .containsExactly("original-native-subject");
    }

    private void seedProvider(String registrationId, LoginProvider.ProviderType type) {
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(type);
        provider.setDisplayName(registrationId);
        // A baseUrl unique per registrationId keeps both uq_login_provider_type_base_url and the
        // git_provider (type, server_url) key collision-free across the tests in this class.
        provider.setBaseUrl("https://" + registrationId + ".example");
        provider.setClientId("test-client-id");
        provider.setClientSecret("test-client-secret");
        provider.setScopes("read:user");
        loginProviderRepository.save(provider);
    }

    private static OAuth2User principal(String subject, String email, boolean emailVerified, String login) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", subject, "login", login, "email", email, "email_verified", emailVerified),
                "id");
    }

    private static long persistedId(@Nullable Long id) {
        assertNotNull(id);
        return id;
    }
}
