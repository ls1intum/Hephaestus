package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the reversibility the GitLab deletion sweep depends on: a tombstoned issue or merge request
 * that reappears upstream is revived by the ordinary upsert. The GitLab issue and merge-request
 * processors persist through the same shared {@code IssueRepository.upsertCore} /
 * {@code PullRequestRepository.upsertCore} that {@code GitLabDeletionSweepService} relies on, and both
 * clear {@code deleted_at}. Without this, a sweep that tombstoned on bad data would need an operator to
 * undo it; with it, the next ordinary sync heals the mistake, which is what makes acting on inference
 * tolerable at all.
 */
@Tag("integration")
class GitLabDeletionSweepSelfHealIntegrationTest extends BaseIntegrationTest {

    private static final long PROVIDER_NATIVE = 1L;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private IdentityProviderRepository providerRepository;

    private Repository repository;
    private Long providerId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        IdentityProvider provider = providerRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITLAB, "https://gitlab.com")
            .orElseGet(() ->
                providerRepository.save(new IdentityProvider(IdentityProviderType.GITLAB, "https://gitlab.com"))
            );
        providerId = provider.getId();

        Organization org = new Organization();
        org.setNativeId(PROVIDER_NATIVE);
        org.setLogin("acme");
        org.setName("Acme");
        org.setAvatarUrl("");
        org.setHtmlUrl("https://gitlab.com/acme");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org.setProvider(provider);
        org = organizationRepository.save(org);

        repository = new Repository();
        repository.setNativeId(555L);
        repository.setName("widgets");
        repository.setNameWithOwner("acme/widgets");
        repository.setHtmlUrl("https://gitlab.com/acme/widgets");
        repository.setVisibility(Repository.Visibility.PRIVATE);
        repository.setDefaultBranch("main");
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setPushedAt(Instant.now());
        repository.setOrganization(org);
        repository.setProvider(provider);
        repository = repositoryRepository.save(repository);
    }

    @Test
    void tombstonedIssueIsRevivedWhenTheUpstreamUpsertSeesItAgain() {
        int number = 5;
        upsertIssue(number);
        assertThat(issueRepository.findLiveIssueNumbersByRepositoryId(repository.getId())).containsExactly(number);

        int tombstoned = issueRepository.tombstoneByRepositoryIdAndNumbers(
            repository.getId(),
            java.util.List.of(number),
            Instant.now()
        );
        assertThat(tombstoned).isEqualTo(1);
        assertThat(issueRepository.findLiveIssueNumbersByRepositoryId(repository.getId())).isEmpty();

        // Upstream handed the issue back — the same code path the sweep's reversibility promise rests on.
        upsertIssue(number);

        assertThat(issueRepository.findLiveIssueNumbersByRepositoryId(repository.getId())).containsExactly(number);
    }

    @Test
    void tombstonedMergeRequestIsRevivedWhenTheUpstreamUpsertSeesItAgain() {
        int number = 7;
        upsertMergeRequest(number);
        assertThat(issueRepository.findLivePullRequestNumbersByRepositoryId(repository.getId())).containsExactly(
            number
        );

        int tombstoned = issueRepository.tombstoneByRepositoryIdAndNumbers(
            repository.getId(),
            java.util.List.of(number),
            Instant.now()
        );
        assertThat(tombstoned).isEqualTo(1);
        assertThat(issueRepository.findLivePullRequestNumbersByRepositoryId(repository.getId())).isEmpty();

        upsertMergeRequest(number);

        assertThat(issueRepository.findLivePullRequestNumbersByRepositoryId(repository.getId())).containsExactly(
            number
        );
    }

    private void upsertIssue(int number) {
        Instant now = Instant.now();
        issueRepository.upsertCore(
            1000L + number,
            providerId,
            number,
            "Issue " + number,
            "body",
            "OPEN",
            null,
            "https://gitlab.com/acme/widgets/-/issues/" + number,
            null,
            null,
            0,
            now,
            now,
            now,
            null,
            repository.getId(),
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private void upsertMergeRequest(int number) {
        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            2000L + number,
            providerId,
            number,
            "MR " + number,
            "body",
            "OPEN",
            null,
            "https://gitlab.com/acme/widgets/-/merge_requests/" + number,
            null,
            null,
            0,
            now,
            now,
            now,
            null,
            repository.getId(),
            null,
            null,
            false,
            false,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            "feature",
            "main",
            null,
            null,
            null,
            null
        );
    }
}
