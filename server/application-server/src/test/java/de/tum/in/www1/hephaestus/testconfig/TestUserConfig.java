package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
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
            seed(userRepository, "testuser", 1);
            seed(userRepository, "mentor", 2);
            seed(userRepository, "admin", 3);
        };
    }

    private void seed(UserRepository repo, String login, long userId) {
        TestUserFactory.ensureUser(repo, login, userId);
    }
}
