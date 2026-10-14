package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.spi.MemberAiPreferences;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccountChoiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private AccountDirectory directory;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private IdentityRepository identities;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private AccountIdentityQuery identityQuery;

    @Autowired
    private AccountPreferencesQuery preferences;

    @Autowired
    private MemberAiPreferences aiPreferences;

    @Test
    void shouldUseOneChoiceAndDeliverySettingWhenTwoScmIdentitiesAndSlackResolveTheSameAccount() {
        var account = accounts.saveAndFlush(new Account("Choice developer"));
        long accountId = Objects.requireNonNull(account.getId());
        var github = identity(account, IdentityProviderType.GITHUB, "https://github.com", "");
        var gitlab = identity(account, IdentityProviderType.GITLAB, "https://gitlab.com", "");
        var slack = identity(account, IdentityProviderType.SLACK, "https://slack.com", "choice-team");
        directory.setAiChoice(accountId, AiChoice.NO_AI);
        directory.setFeedbackDeliveryEnabled(accountId, false);
        for (var identity : List.of(github, gitlab, slack)) {
            var resolved = identityQuery.resolveAccountId(
                    identity.getProviderId(), identity.getProviderUserKey(), identity.getTeamKey());
            assertThat(resolved).contains(accountId);
            long developerId = resolved.orElseThrow();
            assertThat(aiPreferences.forDeveloper(1L, developerId).permitsAi()).isFalse();
            assertThat(aiPreferences.forDeveloper(2L, developerId).permitsAi()).isFalse();
            assertThat(preferences.practiceFeedbackDeliveryEnabled(developerId)).isFalse();
        }
        var saved = directory.find(accountId).orElseThrow();
        assertThat(saved.aiChoice()).isEqualTo(AiChoice.NO_AI);
        assertThat(saved.aiChoiceAt()).isNotNull();
        directory.setAiChoice(accountId, AiChoice.IN_HOUSE_ONLY);
        directory.setFeedbackDeliveryEnabled(accountId, true);
        assertThat(aiPreferences.forDeveloper(1L, accountId).choice()).isEqualTo(AiChoice.IN_HOUSE_ONLY);
        assertThat(preferences.practiceFeedbackDeliveryEnabled(accountId)).isTrue();
        directory.setAiChoice(accountId, null);
        assertThat(directory.find(accountId).orElseThrow().aiChoice()).isNull();
        assertThat(directory.find(accountId).orElseThrow().aiChoiceAt()).isNull();
    }

    private Identity identity(Account account, IdentityProviderType type, String origin, String team) {
        var provider = providers
                .findByTypeAndServerUrl(type, origin)
                .orElseGet(() -> providers.saveAndFlush(new IdentityProvider(type, origin)));
        var identity = new Identity();
        identity.setAccount(account);
        identity.setProviderId(Objects.requireNonNull(provider.getId()));
        identity.setProviderUserKey(UUID.randomUUID().toString());
        identity.setTeamKey(team);
        identity.setLinkedVia(Identity.LinkedVia.MANUAL);
        return identities.saveAndFlush(identity);
    }
}
