package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ReviewerResolver — server-owned reviewer identity")
class ReviewerResolverTest extends BaseUnitTest {

    private static final long PR_ID = 456L;

    @Mock
    private PullRequestReviewRepository reviewRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private IssueCommentRepository issueCommentRepository;

    private ReviewerResolver resolver;
    private User author;

    @BeforeEach
    void setUp() {
        resolver = new ReviewerResolver(reviewRepository, reviewCommentRepository, issueCommentRepository);
        author = user(1L, "author", User.Type.USER);
        // Unstubbed list-returning repo methods default to empty via Mockito's RETURNS_DEFAULTS.
    }

    @Test
    @DisplayName("collapses duplicate identity rows sharing a login to one User (smallest id)")
    void dedupesByLoginKeepingSmallestId() {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(user(41L, "Carol", User.Type.USER)), review(user(6L, "carol", User.Type.USER)))
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("carol");
        assertThat(reviewers.get("carol").getId()).isEqualTo(6L); // deterministic: smallest id wins
    }

    @Test
    @DisplayName("excludes the PR author by NORMALIZED LOGIN even when a duplicate id row exists")
    void excludesAuthorByLoginNotId() {
        // A duplicate identity row of the author (different id, same login) must NOT leak back in as a reviewer.
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(user(99L, "Author", User.Type.USER)), review(user(5L, "dave", User.Type.USER)))
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("dave");
    }

    @Test
    @DisplayName("excludes bot users")
    void excludesBots() {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(user(7L, "ci-bot", User.Type.BOT)), review(user(8L, "eve", User.Type.USER)))
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("eve");
    }

    @Test
    @DisplayName("excludes Hephaestus's own identity, derived from the marker on its posted note")
    void excludesHephaestusIdentityByMarker() {
        // Hephaestus posts its practice-review summary as a conversation note carrying the tool marker; the
        // login of that note is treated as the tool's own identity and excluded, even though it is typed USER.
        when(issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(
                note(
                    user(20L, "heph-bot", User.Type.USER),
                    "Practice review summary <!-- hephaestus:practice-review:1 -->"
                ),
                note(user(9L, "frank", User.Type.USER), "Nice work, ship it")
            )
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("frank");
    }

    @Test
    @DisplayName("single reviewer resolves to a one-entry roster")
    void singleReviewerSet() {
        when(reviewRepository.findAllByPullRequestIdWithAuthor(PR_ID)).thenReturn(
            List.of(review(user(3L, "grace", User.Type.USER)))
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("grace");
        assertThat(reviewers).hasSize(1);
    }

    @Test
    @DisplayName("includes a reviewer who only left an inline (diff-anchored) comment")
    void includesInlineCommentAuthors() {
        when(reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(PR_ID)).thenReturn(
            List.of(inlineComment(user(4L, "heidi", User.Type.USER)))
        );

        Map<String, User> reviewers = resolver.reviewersByLogin(PR_ID, author);

        assertThat(reviewers).containsOnlyKeys("heidi");
    }

    // Fixtures

    private static User user(Long id, String login, User.Type type) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setLogin(login);
        u.setType(type);
        return u;
    }

    private static PullRequestReview review(User author) {
        PullRequestReview r = new PullRequestReview();
        r.setAuthor(author);
        return r;
    }

    private static PullRequestReviewComment inlineComment(User author) {
        PullRequestReviewComment c = new PullRequestReviewComment();
        c.setAuthor(author);
        return c;
    }

    private static IssueComment note(User author, String body) {
        IssueComment c = new IssueComment();
        c.setAuthor(author);
        c.setBody(body);
        return c;
    }
}
