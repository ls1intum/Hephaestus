package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;

/**
 * Factory + helper utilities for deterministic test users.
 */
public final class TestUserFactory {

    private TestUserFactory() {}

    public static User ensureUser(UserRepository repository, String login, long fallbackId) {
        return repository.findByLogin(login).orElseGet(() -> repository.save(createUser(fallbackId, login)));
    }

    public static User ensureUser(UserRepository repository, String login, long fallbackId, IdentityProvider provider) {
        return repository
            .findByLoginAndProviderId(login, provider.getId())
            .orElseGet(() -> repository.save(createUser(fallbackId, login, provider)));
    }

    public static User createUser(long id, String login) {
        User user = new User();
        user.setNativeId(id);
        user.setLogin(login);
        user.setName(login);
        user.setAvatarUrl("https://github.com/" + login + ".png");
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setEmail(login + "@example.com");
        // Note: User preferences (participateInResearch, aiReviewEnabled) are now
        // stored in the UserPreferences entity in the account module.
        // Note: leaguePoints is set on WorkspaceMembership, not User
        return user;
    }

    public static User createUser(long id, String login, IdentityProvider provider) {
        User user = createUser(id, login);
        user.setProvider(provider);
        return user;
    }
}
