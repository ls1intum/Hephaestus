package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestUserConfig {

    @Bean
    public ApplicationRunner seedTestUsers(UserRepository userRepository) {
        return args -> {
            seed(userRepository, "testuser", 1, List.of());
            seed(userRepository, "mentor", 2, List.of("mentor_access"));
            seed(userRepository, "admin", 3, List.of("admin"));
        };
    }

    private void seed(UserRepository repo, String login, long userId, List<String> roles) {
        repo
            .findByLogin(login)
            .orElseGet(() -> {
                User user = new User();
                // deterministic, collision-safe ID from the userId string
                user.setId(userId);
                user.setLogin(login);
                user.setName(login);
                user.setEmail(login + "@example.com");
                user.setAvatarUrl("https://github.com/" + login + ".png");
                user.setHtmlUrl("https://github.com/" + login);
                user.setType(User.Type.USER);
                user.setFollowers(0);
                user.setFollowing(0);
                user.setLeaguePoints(0);
                user.setNotificationsEnabled(true);
                // you can tweak defaults here if you need description, etc.
                return repo.save(user);
            });
    }
}
