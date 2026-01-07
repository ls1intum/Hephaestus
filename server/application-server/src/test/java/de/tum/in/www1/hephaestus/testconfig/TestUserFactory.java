package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;

/**
 * Factory + helper utilities for deterministic test users.
 */
public final class TestUserFactory {

    private TestUserFactory() {}

    public static User ensureUser(UserRepository repository, String login, long fallbackId) {
        return repository.findByLogin(login).orElseGet(() -> repository.save(createUser(fallbackId, login)));
    }

    public static User createUser(long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setName(login);
        user.setAvatarUrl("https://github.com/" + login + ".png");
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setEmail(login + "@example.com");
        // Note: User preferences (notificationsEnabled, participateInResearch) are now
        // stored in the UserPreferences entity in the account module.
        // Note: leaguePoints is set on WorkspaceMembership, not User
        return user;
    }
}
