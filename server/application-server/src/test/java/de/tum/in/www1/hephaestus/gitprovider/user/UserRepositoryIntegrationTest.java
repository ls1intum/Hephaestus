package de.tum.in.www1.hephaestus.gitprovider.user;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

/**
 * Integration tests for the provider-scoped umlaut-folded display-name lookup
 * used as Strategy 4b in {@link de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver}.
 *
 * <p>Verifies the {@code de_fold_name(...)} SQL function (created by Liquibase
 * migration {@code 1776587029158_changelog.xml} and registered with Hibernate
 * via {@link de.tum.in.www1.hephaestus.config.HephaestusFunctionContributor})
 * folds stored umlauts (ö→oe, ä→ae, ü→ue, ß→ss) before comparing against an
 * ASCII-folded {@code firstname.lastname} derived from a commit email local-part.
 * Covers the case where e.g. {@code jannis.hoeferlin@tum.de} must resolve to a
 * DB {@code User.name = "Jannis Höferlin"}.
 *
 * <p>Because the test profile runs with {@code spring.liquibase.enabled=false}
 * and Hibernate DDL only, the function is re-declared via {@link Sql} before
 * the class boots so that the registered JPQL {@code function('de_fold_name', ...)}
 * resolves to a real executable Postgres routine.
 */
@DisplayName("UserRepository umlaut-folded name lookup Integration")
@Sql(scripts = "/sql/de_fold_name_function.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UserRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    private GitProvider gitlabProvider;
    private GitProvider otherProvider;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        gitlabProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://gitlab.example.com")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://gitlab.example.com"))
            );

        otherProvider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, "https://other.gitlab.com")
            .orElseGet(() ->
                gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, "https://other.gitlab.com"))
            );
    }

    private User persistUser(GitProvider provider, long nativeId, String login, String name) {
        User user = new User();
        user.setNativeId(nativeId);
        user.setProvider(provider);
        user.setLogin(login);
        user.setName(name);
        user.setAvatarUrl("https://example.com/" + login + ".png");
        user.setHtmlUrl("https://example.com/" + login);
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    @Test
    @DisplayName("folds lowercase umlaut ö→oe when matching stored name")
    void foldsLowercaseOeUmlaut() {
        User stored = persistUser(gitlabProvider, 10L, "jhoeferlin", "Jannis Höferlin");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Jannis Hoeferlin",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("folds lowercase umlaut ä→ae when matching stored name")
    void foldsLowercaseAeUmlaut() {
        User stored = persistUser(gitlabProvider, 20L, "tmaerz", "Tobias März");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Tobias Maerz",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("folds lowercase umlaut ü→ue when matching stored name")
    void foldsLowercaseUeUmlaut() {
        User stored = persistUser(gitlabProvider, 30L, "smueller", "Sebastian Müller");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Sebastian Mueller",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("folds ß→ss when matching stored name")
    void foldsSharpSUmlaut() {
        User stored = persistUser(gitlabProvider, 40L, "sgross", "Sabine Groß");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Sabine Gross",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("folds uppercase umlaut Ö→Oe when matching stored name via LOWER")
    void foldsUppercaseOeUmlaut() {
        // Hibernate applies LOWER before REPLACE, so 'Ö' lowercases to 'ö' and
        // then folds to 'oe'. The stored value starts with uppercase to exercise
        // the LOWER-first ordering in the JPQL.
        User stored = persistUser(gitlabProvider, 50L, "oexample", "Örsted Example");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Oersted Example",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("matches stored name that has no umlaut at all")
    void matchesAsciiStoredName() {
        User stored = persistUser(gitlabProvider, 60L, "ekiessig", "Erik Kiessig");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Erik Kiessig",
            gitlabProvider.getId()
        );

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(stored.getId());
    }

    @Test
    @DisplayName("does not cross provider boundaries")
    void doesNotCrossProviderBoundaries() {
        persistUser(otherProvider, 70L, "jhoeferlin", "Jannis Höferlin");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Jannis Hoeferlin",
            gitlabProvider.getId()
        );

        assertThat(matches).isEmpty();
    }

    @Test
    @DisplayName("returns all candidates across providers when providerId-less query is used")
    void returnsAllCandidatesAcrossProviders() {
        User gitlab = persistUser(gitlabProvider, 80L, "jhoeferlin", "Jannis Höferlin");
        User other = persistUser(otherProvider, 81L, "jhoeferlin-other", "Jannis Höferlin");

        List<User> matches = userRepository.findAllByUmlautFoldedName("Jannis Hoeferlin");

        assertThat(matches).extracting(User::getId).containsExactlyInAnyOrder(gitlab.getId(), other.getId());
    }

    @Test
    @DisplayName("returns empty when no stored name folds to the query")
    void returnsEmptyWhenNoMatch() {
        persistUser(gitlabProvider, 90L, "ekiessig", "Erik Kiessig");

        List<User> matches = userRepository.findAllByUmlautFoldedNameAndProviderId(
            "Jannis Hoeferlin",
            gitlabProvider.getId()
        );

        assertThat(matches).isEmpty();
    }

    @Test
    @DisplayName("query is case-insensitive on the input")
    void queryIsCaseInsensitiveOnInput() {
        persistUser(gitlabProvider, 100L, "jhoeferlin", "Jannis Höferlin");

        assertThat(
            userRepository.findAllByUmlautFoldedNameAndProviderId("JANNIS HOEFERLIN", gitlabProvider.getId())
        ).hasSize(1);
        assertThat(
            userRepository.findAllByUmlautFoldedNameAndProviderId("jannis hoeferlin", gitlabProvider.getId())
        ).hasSize(1);
    }
}
