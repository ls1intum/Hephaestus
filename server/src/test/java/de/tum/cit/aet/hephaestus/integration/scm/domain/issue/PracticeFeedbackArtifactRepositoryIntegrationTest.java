package de.tum.cit.aet.hephaestus.integration.scm.domain.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PracticeFeedbackArtifactRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private UserRepository userRepository;

    private Long issueId;
    private Long pullRequestId;

    @BeforeEach
    void setUp() {
        IdentityProvider provider = identityProviderRepository.save(
            new IdentityProvider(IdentityProviderType.GITHUB, "https://github.example")
        );
        User author = userRepository.save(TestUserFactory.createUser(1001L, "developer", provider));

        Repository repository = new Repository();
        repository.setNativeId(2001L);
        repository.setProvider(provider);
        repository.setName("repo");
        repository.setNameWithOwner("owner/repo");
        repository.setHtmlUrl("https://github.example/owner/repo");
        repository.setDefaultBranch("main");
        repository = repositoryRepository.save(repository);

        Instant now = Instant.now();
        issueRepository.upsertCore(
            3001L,
            persistedId(provider),
            42,
            "Issue",
            "body",
            "OPEN",
            null,
            "https://github.example/owner/repo/issues/42",
            false,
            null,
            0,
            now,
            now,
            now,
            author.getId(),
            repository.getId(),
            null,
            null,
            null,
            null,
            null,
            null
        );
        pullRequestRepository.upsertCore(
            4001L,
            persistedId(provider),
            42,
            "Pull request",
            "body",
            "OPEN",
            null,
            "https://github.example/owner/repo/pull/42",
            false,
            null,
            0,
            now,
            now,
            now,
            author.getId(),
            repository.getId(),
            null,
            null,
            false,
            false,
            1,
            2,
            1,
            1,
            null,
            null,
            null,
            "feature",
            "main",
            "head",
            "base",
            null,
            null
        );

        issueId = issueRepository.findByRepositoryIdAndNumber(repository.getId(), 42).orElseThrow().getId();
        pullRequestId = pullRequestRepository.findByRepositoryIdAndNumber(repository.getId(), 42).orElseThrow().getId();
    }

    @Test
    void issueFinderFetchesDeliveryAssociationsAndExcludesPullRequests() {
        Issue issue = issueRepository.findByIdWithAuthorAndRepository(issueId).orElseThrow();

        assertNotNull(issue.getAuthor());
        assertThat(issue.getAuthor().getLogin()).isEqualTo("developer");
        assertThat(issue.requireRepository().getNameWithOwner()).isEqualTo("owner/repo");
        assertThat(issueRepository.findByIdWithAuthorAndRepository(pullRequestId)).isEmpty();
    }

    @Test
    void pullRequestFinderFetchesDeliveryAssociations() {
        var pullRequest = pullRequestRepository.findByIdWithAuthorAndRepository(pullRequestId).orElseThrow();

        assertNotNull(pullRequest.getAuthor());
        assertThat(pullRequest.getAuthor().getLogin()).isEqualTo("developer");
        assertThat(pullRequest.requireRepository().getNameWithOwner()).isEqualTo("owner/repo");
    }

    private static long persistedId(IdentityProvider provider) {
        Long id = provider.getId();
        assertNotNull(id);
        return id;
    }
}
