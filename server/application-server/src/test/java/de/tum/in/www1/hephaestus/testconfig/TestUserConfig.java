package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestUserConfig {

    @Bean
    public ApplicationRunner seedTestUsers(UserRepository userRepository, GitProviderRepository gitProviderRepository) {
        return args -> {
            GitProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseGet(() ->
                    gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com"))
                );
            seed(userRepository, "testuser", 1, provider);
            seed(userRepository, "mentor", 2, provider);
            seed(userRepository, "admin", 3, provider);
        };
    }

    private void seed(UserRepository repo, String login, long userId, GitProvider provider) {
        TestUserFactory.ensureUser(repo, login, userId, provider);
    }
}
