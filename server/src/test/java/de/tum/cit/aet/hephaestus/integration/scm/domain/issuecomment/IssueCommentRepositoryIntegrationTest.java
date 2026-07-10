package de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.AuthorAssociation;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for {@code findByIssueIdWithAuthorOrderByCreatedAt}, the query that materialises the
 * general (conversation-tab) MR discussion for the reviewer-craft firewall.
 *
 * <p>The unit test ({@code GeneralReviewCommentContentSourceTest}) mocks the repository, so it can only
 * cover the in-memory filtering. This test runs the JPQL against a real Postgres schema to prove the three
 * load-bearing contracts the provider depends on: (a) {@code LEFT JOIN FETCH ic.author} actually initialises
 * the {@code FetchType.LAZY} author so {@code getLogin()} is readable AFTER the persistence context closes
 * (no N+1, no {@code LazyInitializationException}), (b) {@code ORDER BY ic.createdAt ASC} returns
 * oldest-first, and (c) the {@code issue.id} filter resolves against the shared {@code Issue}/{@code
 * PullRequest} id space and excludes other issues' comments.
 */
class IssueCommentRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    private IdentityProvider provider;
    private Repository repository;
    private long nativeIdSeq = 1_000L;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

        Organization org = new Organization();
        org.setNativeId(nextNativeId());
        org.setLogin("acme");
        org.setHtmlUrl("https://github.com/acme");
        org.setProvider(provider);
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        org = organizationRepository.save(org);

        repository = new Repository();
        repository.setNativeId(nextNativeId());
        repository.setName("repo");
        repository.setNameWithOwner("acme/repo");
        repository.setHtmlUrl("https://github.com/acme/repo");
        repository.setVisibility(Repository.Visibility.PUBLIC);
        repository.setDefaultBranch("main");
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setPushedAt(Instant.now());
        repository.setOrganization(org);
        repository.setProvider(provider);
        repository = repositoryRepository.save(repository);
    }

    private long nextNativeId() {
        return nativeIdSeq++;
    }

    private Issue persistIssue(int number) {
        Issue issue = new Issue();
        issue.setNativeId(nextNativeId());
        issue.setProvider(provider);
        issue.setNumber(number);
        issue.setTitle("Issue #" + number);
        issue.setState(Issue.State.OPEN);
        issue.setHtmlUrl("https://github.com/acme/repo/issues/" + number);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue.setRepository(repository);
        return issueRepository.save(issue);
    }

    private User persistUser(String login) {
        User user = new User();
        user.setNativeId(nextNativeId());
        user.setProvider(provider);
        user.setLogin(login);
        user.setAvatarUrl("https://github.com/" + login + ".png");
        user.setHtmlUrl("https://github.com/" + login);
        user.setType(User.Type.USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private IssueComment persistComment(Issue issue, User author, String body, Instant createdAt) {
        IssueComment comment = new IssueComment();
        comment.setNativeId(nextNativeId());
        comment.setProvider(provider);
        comment.setBody(body);
        comment.setHtmlUrl("https://github.com/acme/repo/issues/" + issue.getNumber() + "#c" + comment.getNativeId());
        comment.setAuthorAssociation(AuthorAssociation.MEMBER);
        comment.setCreatedAt(createdAt);
        comment.setIssue(issue);
        comment.setAuthor(author);
        return issueCommentRepository.save(comment);
    }

    @Test
    void returnsCommentsOldestFirstWithAuthorInitialisedOutsideSession() {
        Issue issue = persistIssue(42);
        User reviewer = persistUser("reviewer-a");
        User contributor = persistUser("contributor-b");
        // Persist newest first to prove the ORDER BY does the sorting, not insertion order.
        persistComment(issue, contributor, "addressed the feedback", Instant.parse("2025-06-01T11:00:00Z"));
        persistComment(issue, reviewer, "this branch is always taken", Instant.parse("2025-06-01T10:00:00Z"));

        List<IssueComment> result = issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(issue.getId());

        // (b) oldest-first ordering, independent of insertion order.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBody()).isEqualTo("this branch is always taken");
        assertThat(result.get(1).getBody()).isEqualTo("addressed the feedback");

        // (a) the lazy author is readable AFTER the query's persistence context has closed — proves the
        // LEFT JOIN FETCH eagerly initialised it (a bare LAZY load would throw here).
        assertThatCode(() -> {
            assertThat(result.get(0).getAuthor().getLogin()).isEqualTo("reviewer-a");
            assertThat(result.get(1).getAuthor().getLogin()).isEqualTo("contributor-b");
        }).doesNotThrowAnyException();
    }

    @Test
    void filtersByIssueIdAndDoesNotLeakOtherIssuesComments() {
        Issue target = persistIssue(1);
        Issue other = persistIssue(2);
        User user = persistUser("reviewer-a");
        persistComment(target, user, "on the target issue", Instant.parse("2025-06-01T10:00:00Z"));
        persistComment(other, user, "on a different issue", Instant.parse("2025-06-01T10:00:00Z"));

        List<IssueComment> result = issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(target.getId());

        // (c) the issue.id filter scopes to one issue only.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBody()).isEqualTo("on the target issue");
    }
}
