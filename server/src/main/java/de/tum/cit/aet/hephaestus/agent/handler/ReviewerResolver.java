package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.providers.GeneralReviewCommentContentProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the set of REAL human reviewers of a pull request — the server owns reviewer identity, never the
 * model. Used to attribute the reviewer-audience practices (see {@link
 * de.tum.cit.aet.hephaestus.practices.model.ReviewerAudiencePractices}) to the reviewer whose comments
 * were assessed.
 *
 * <p>The reviewer set is the DISTINCT authors of everything a reviewer can leave on a PR:
 * <ul>
 *   <li>review decisions ({@code PullRequestReview}) — CHANGES_REQUESTED / APPROVED / COMMENTED,</li>
 *   <li>inline (diff-anchored) review comments ({@code PullRequestReviewComment}),</li>
 *   <li>position-less conversation-tab notes — GitLab routes these to {@code IssueComment} keyed by the
 *       same id (a {@code PullRequest} IS an {@code Issue}); merged-MR reviewer notes commonly land here.</li>
 * </ul>
 *
 * <p><b>Exclusions (all mandatory).</b>
 * <ul>
 *   <li>The PR AUTHOR — excluded by NORMALIZED LOGIN, never by id. Duplicate identity rows can share one
 *       login under different ids, so an id-only exclusion would leak the author back in as a "reviewer".</li>
 *   <li>Bot users ({@link User.Type#BOT}).</li>
 *   <li>Hephaestus's own posted notes — its practice-review summary lands as an {@code IssueComment}
 *       carrying the {@code <!-- hephaestus: -->} marker; the login of any marker-bearing note is treated
 *       as the tool's own identity and excluded (derived from the data, no config needed).</li>
 * </ul>
 *
 * <p><b>Canonicalisation.</b> The result is keyed by normalized login → ONE {@link User} (the smallest id
 * wins, deterministically), so duplicate identity rows for one human collapse to a single reviewer and the
 * roster never double-counts them.
 */
@Service
public class ReviewerResolver {

    private static final Logger log = LoggerFactory.getLogger(ReviewerResolver.class);

    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewCommentRepository reviewCommentRepository;
    private final IssueCommentRepository issueCommentRepository;

    public ReviewerResolver(
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        IssueCommentRepository issueCommentRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.issueCommentRepository = issueCommentRepository;
    }

    /**
     * The real human reviewers of {@code pullRequestId}, keyed by normalized login. Empty when the PR drew
     * no resolvable reviewer (e.g. only the author or bots participated) — the caller must then DISCARD any
     * reviewer-audience finding rather than misattribute it.
     *
     * @param pullRequestId the PR id (== the issue id in the shared table, for the conversation notes)
     * @param prAuthor      the PR author, excluded by normalized login (may be null → no author exclusion)
     */
    @Transactional(readOnly = true)
    public Map<String, User> reviewersByLogin(long pullRequestId, @Nullable User prAuthor) {
        String authorLogin = prAuthor == null ? null : normalizeLogin(prAuthor.getLogin());

        List<IssueComment> conversationNotes = issueCommentRepository.findByIssueIdWithAuthorOrderByCreatedAt(
            pullRequestId
        );

        // Hephaestus's own identity: the login of any conversation note carrying the tool marker. The mirror's
        // bot login is an opaque access-token identity indistinguishable from a human by login alone, so we
        // derive it from the marker (the same signal the content providers use) instead of guessing a name.
        Set<String> hephaestusLogins = new HashSet<>();
        for (IssueComment note : conversationNotes) {
            if (note == null || note.getAuthor() == null) {
                continue;
            }
            String body = note.getBody();
            if (body != null && body.contains(GeneralReviewCommentContentProvider.HEPHAESTUS_MARKER)) {
                String normalized = normalizeLogin(note.getAuthor().getLogin());
                if (normalized != null) {
                    hephaestusLogins.add(normalized);
                }
            }
        }

        Map<String, User> byLogin = new HashMap<>();

        List<PullRequestReview> reviews = reviewRepository.findAllByPullRequestIdWithAuthor(pullRequestId);
        for (PullRequestReview review : reviews) {
            consider(byLogin, review == null ? null : review.getAuthor(), authorLogin, hephaestusLogins);
        }

        List<PullRequestReviewComment> inlineComments =
            reviewCommentRepository.findByPullRequestIdWithAuthorOrderByCreatedAt(pullRequestId);
        for (PullRequestReviewComment comment : inlineComments) {
            consider(byLogin, comment == null ? null : comment.getAuthor(), authorLogin, hephaestusLogins);
        }

        for (IssueComment note : conversationNotes) {
            consider(byLogin, note == null ? null : note.getAuthor(), authorLogin, hephaestusLogins);
        }

        log.info(
            "Resolved reviewer set: prId={}, reviewers={}, logins={}",
            pullRequestId,
            byLogin.size(),
            byLogin.keySet()
        );
        return byLogin;
    }

    /**
     * Add {@code candidate} to the roster unless it is the author (by login), a bot, or a Hephaestus
     * identity. Canonicalises on normalized login, keeping the smallest id when duplicate rows collide.
     */
    private static void consider(
        Map<String, User> byLogin,
        @Nullable User candidate,
        @Nullable String authorLogin,
        Set<String> hephaestusLogins
    ) {
        if (candidate == null) {
            return;
        }
        String normalized = normalizeLogin(candidate.getLogin());
        if (normalized == null) {
            return;
        }
        if (normalized.equals(authorLogin)) {
            return; // the PR author is never their own reviewer — excluded by login, never by id
        }
        if (candidate.getType() == User.Type.BOT) {
            return;
        }
        if (hephaestusLogins.contains(normalized)) {
            return;
        }
        byLogin.merge(normalized, candidate, ReviewerResolver::smallerId);
    }

    /** Deterministic canonical pick for two rows sharing a login: the smaller (non-null) id. */
    private static User smallerId(User a, User b) {
        Long ida = a.getId();
        Long idb = b.getId();
        if (ida == null) {
            return b;
        }
        if (idb == null) {
            return a;
        }
        return ida <= idb ? a : b;
    }

    /** Locale-safe (ROOT) login normalization: trimmed + lower-cased, or null when blank/absent. */
    @Nullable
    public static String normalizeLogin(@Nullable String login) {
        if (login == null) {
            return null;
        }
        String trimmed = login.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
