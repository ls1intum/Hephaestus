package de.tum.cit.aet.hephaestus.integration.scm.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins the Stage-A invariant introduced by ADR 0016: the {@code keycloak_subject}
 * partial unique index on {@code "user"} accepts NULL freely (sync-only rows stay
 * outside the uniqueness contract) but rejects duplicates among populated subjects.
 *
 * <p>Stage B will flip the auth-time identity resolver to consult this column;
 * those guarantees must hold from the day the column ships.
 */
class UserKeycloakSubjectRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    private GitProvider provider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
    }

    @Test
    void setKeycloakSubjectIfChanged_seedsOnFirstCall_andIsIdempotentOnReplay() {
        Long userId = persistUser(1001L, "alice");

        int firstWrite = userRepository.setKeycloakSubjectIfChanged(userId, "sub-alice");
        assertThat(firstWrite).isEqualTo(1);
        assertThat(userRepository.findById(userId).orElseThrow().getKeycloakSubject()).isEqualTo("sub-alice");

        int replayWrite = userRepository.setKeycloakSubjectIfChanged(userId, "sub-alice");
        assertThat(replayWrite).isZero();
    }

    @Test
    void setKeycloakSubjectIfChanged_overwritesWhenSubjectActuallyDiffers() {
        Long userId = persistUser(1002L, "bob");
        userRepository.setKeycloakSubjectIfChanged(userId, "sub-bob-old");

        int rotated = userRepository.setKeycloakSubjectIfChanged(userId, "sub-bob-new");

        assertThat(rotated).isEqualTo(1);
        assertThat(userRepository.findById(userId).orElseThrow().getKeycloakSubject()).isEqualTo("sub-bob-new");
    }

    @Test
    void syncOnlyRowsKeepKeycloakSubjectNull_andCoexistFreely() {
        Long bot1 = persistUser(2001L, "bot-1");
        Long bot2 = persistUser(2002L, "bot-2");
        Long bot3 = persistUser(2003L, "bot-3");

        assertThat(userRepository.findById(bot1).orElseThrow().getKeycloakSubject()).isNull();
        assertThat(userRepository.findById(bot2).orElseThrow().getKeycloakSubject()).isNull();
        assertThat(userRepository.findById(bot3).orElseThrow().getKeycloakSubject()).isNull();
    }

    private Long persistUser(long nativeId, String login) {
        User user = new User();
        user.setNativeId(nativeId);
        user.setLogin(login);
        user.setName(login);
        user.setProvider(provider);
        user.setType(User.Type.USER);
        user.setHtmlUrl("https://example.com/" + login);
        user.setAvatarUrl("");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user).getId();
    }
}
