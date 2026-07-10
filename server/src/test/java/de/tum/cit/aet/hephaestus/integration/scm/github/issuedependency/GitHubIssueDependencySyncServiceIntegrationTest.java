package de.tum.cit.aet.hephaestus.integration.scm.github.issuedependency;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link GitHubIssueDependencySyncService}.
 * <p>
 * Tests the blocked_by/blocking relationship management.
 * <p>
 * Note: This test class uses @Transactional because it directly calls service methods
 * and needs to access lazy-loaded relationships. This is safe because there are no
 * parallel HTTP handler threads that would compete for database connections.
 */
@Transactional
class GitHubIssueDependencySyncServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubIssueDependencySyncService issueDependencySyncService;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private EntityManager entityManager;

    private IdentityProvider gitProvider;
    private Repository testRepository;
    private Issue blockedIssue;
    private Issue blockingIssue;

    @BeforeEach
    void setUp() {
        // Create git provider
        gitProvider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

        // Create test repository
        testRepository = new Repository();
        testRepository.setNativeId(98765L);
        testRepository.setName("test-repo");
        testRepository.setNameWithOwner("test-org/test-repo");
        testRepository.setHtmlUrl("https://github.com/test-org/test-repo");
        testRepository.setProvider(gitProvider);
        testRepository = repositoryRepository.save(testRepository);

        // Create issue that will be blocked
        blockedIssue = new Issue();
        blockedIssue.setNativeId(1001L);
        blockedIssue.setNumber(10);
        blockedIssue.setTitle("Feature: Implement something");
        blockedIssue.setState(Issue.State.OPEN);
        blockedIssue.setRepository(testRepository);
        blockedIssue.setProvider(gitProvider);
        blockedIssue = issueRepository.save(blockedIssue);

        // Create issue that will block
        blockingIssue = new Issue();
        blockingIssue.setNativeId(1002L);
        blockingIssue.setNumber(5);
        blockingIssue.setTitle("Bug: Fix prerequisite");
        blockingIssue.setState(Issue.State.OPEN);
        blockingIssue.setRepository(testRepository);
        blockingIssue.setProvider(gitProvider);
        blockingIssue = issueRepository.save(blockingIssue);
    }

    @Test
    void shouldAddBlockingRelationship() {
        issueDependencySyncService.processIssueDependencyEvent(
            blockedIssue.getId(),
            blockingIssue.getId(),
            true // isBlock
        );

        Issue refreshedBlockedIssue = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        assertThat(refreshedBlockedIssue.getBlockedBy()).hasSize(1);
        assertThat(refreshedBlockedIssue.getBlockedBy()).extracting(Issue::getId).contains(blockingIssue.getId());
    }

    @Test
    void shouldRemoveBlockingRelationship() {
        // Given: Add the relationship first
        issueDependencySyncService.processIssueDependencyEvent(
            blockedIssue.getId(),
            blockingIssue.getId(),
            true // isBlock
        );

        Issue refreshed = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        assertThat(refreshed.getBlockedBy()).hasSize(1);

        // When: Remove the relationship
        issueDependencySyncService.processIssueDependencyEvent(
            blockedIssue.getId(),
            blockingIssue.getId(),
            false // unblock
        );

        Issue refreshedAfterRemove = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        assertThat(refreshedAfterRemove.getBlockedBy()).isEmpty();
    }

    @Test
    void shouldBeIdempotentWhenAddingDuplicate() {
        // Given: Add relationship
        issueDependencySyncService.processIssueDependencyEvent(blockedIssue.getId(), blockingIssue.getId(), true);

        // When: Add same relationship again
        issueDependencySyncService.processIssueDependencyEvent(blockedIssue.getId(), blockingIssue.getId(), true);

        // Then: Should still have only one relationship
        Issue refreshed = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        assertThat(refreshed.getBlockedBy()).hasSize(1);
    }

    @Test
    void shouldHandleMissingBlockedIssueGracefully() {
        // When: Process event for non-existent blocked issue
        issueDependencySyncService.processIssueDependencyEvent(
            999999L, // non-existent
            blockingIssue.getId(),
            true
        );

        // Then: No exception, and blocking issue unchanged
        Issue refreshedBlocking = issueRepository.findById(blockingIssue.getId()).orElseThrow();
        assertThat(refreshedBlocking.getBlocking()).isEmpty();
    }

    @Test
    void shouldHandleMissingBlockingIssueGracefully() {
        // When: Process event for non-existent blocking issue
        issueDependencySyncService.processIssueDependencyEvent(
            blockedIssue.getId(),
            999999L, // non-existent
            true
        );

        // Then: No exception, blocked issue unchanged
        Issue refreshedBlocked = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        assertThat(refreshedBlocked.getBlockedBy()).isEmpty();
    }

    @Test
    void blockingRelationshipShouldAppearOnBothSides() {
        issueDependencySyncService.processIssueDependencyEvent(blockedIssue.getId(), blockingIssue.getId(), true);

        // Clear persistence context to force fresh fetch from database
        // This is needed because the inverse side (blocking) of the ManyToMany relationship
        // is only reflected in the database, not in the in-memory cached entities
        entityManager.flush();
        entityManager.clear();

        // Then: Check both directions
        Issue refreshedBlocked = issueRepository.findById(blockedIssue.getId()).orElseThrow();
        Issue refreshedBlocking = issueRepository.findById(blockingIssue.getId()).orElseThrow();

        assertThat(refreshedBlocked.getBlockedBy()).extracting(Issue::getId).contains(blockingIssue.getId());
        assertThat(refreshedBlocking.getBlocking()).extracting(Issue::getId).contains(blockedIssue.getId());
    }
}
