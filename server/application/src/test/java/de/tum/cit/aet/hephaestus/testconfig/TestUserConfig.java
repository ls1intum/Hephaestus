package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestUserConfig {

    @Bean
    public ApplicationRunner seedTestUsers(
        UserRepository userRepository,
        IdentityProviderRepository gitProviderRepository
    ) {
        return args -> {
            IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() ->
                    gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
                );
            seed(userRepository, "testuser", 1, provider);
            seed(userRepository, "mentor", 2, provider);
            seed(userRepository, "admin", 3, provider);
        };
    }

    private void seed(UserRepository repo, String login, long userId, IdentityProvider provider) {
        TestUserFactory.ensureUser(repo, login, userId, provider);
    }
}
