package de.tum.cit.aet.hephaestus.integration.identity.connect;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class RegistrationToGitProviderResolverIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private RegistrationToGitProviderResolver resolver;

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private UserRepository users;

    @Test
    @Transactional
    void shouldBatchOnlyExactProviderSubjectAndUnscopedTeamMatches() {
        String suffix = UUID.randomUUID().toString();
        var first = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITHUB, "https://first-" + suffix + ".example.test"));
        var second = providers.saveAndFlush(
                new IdentityProvider(IdentityProviderType.GITLAB, "https://second-" + suffix + ".example.test"));
        var firstUser = users.saveAndFlush(TestUserFactory.createUser(7, "batch-first-" + suffix, first));
        var secondUser = users.saveAndFlush(TestUserFactory.createUser(8, "batch-second-" + suffix, second));
        users.saveAndFlush(TestUserFactory.createUser(8, "batch-unrequested-" + suffix, first));
        var firstKey = new GitProviderRegistry.IdentityKey(Objects.requireNonNull(first.getId()), "7", "");
        var secondKey = new GitProviderRegistry.IdentityKey(Objects.requireNonNull(second.getId()), "8", "");
        var profiles = resolver.profiles(List.of(
                firstKey,
                secondKey,
                new GitProviderRegistry.IdentityKey(firstKey.providerId(), "7", "team"),
                new GitProviderRegistry.IdentityKey(firstKey.providerId(), "U7", ""),
                new GitProviderRegistry.IdentityKey(firstKey.providerId(), "99", "")));
        assertThat(profiles).containsOnlyKeys(firstKey, secondKey);
        assertThat(profiles.get(firstKey))
                .isEqualTo(new GitProviderRegistry.IdentityProfile(
                        firstUser.getLogin(), firstUser.getName(), firstUser.getAvatarUrl(), firstUser.getHtmlUrl()));
        assertThat(profiles.get(secondKey))
                .isEqualTo(new GitProviderRegistry.IdentityProfile(
                        secondUser.getLogin(),
                        secondUser.getName(),
                        secondUser.getAvatarUrl(),
                        secondUser.getHtmlUrl()));
        assertThat(resolver.profiles(List.of())).isEmpty();
    }
}
