package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.GeneralReviewCommentContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.IssueContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ReviewThreadContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Enforces consent for every known human whose selected SCM content can be exported. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScmCaptureContributors {
    private final UserRepository users;
    private final ScmCaptureConsent consent;
    private final JdbcTemplate jdbc;

    void require(Issue work, @Nullable PullRequestReview review, Set<SourceKind> selected) {
        List<User> contributors = new ArrayList<>(work.getAssignees());
        if (work.getAuthor() != null) contributors.add(work.getAuthor());
        if (review != null && review.getAuthor() != null) contributors.add(review.getAuthor());
        if (work instanceof PullRequest pull && pull.getMergedBy() != null) contributors.add(pull.getMergedBy());
        List<Long> ids = new ArrayList<>();
        if (selected.contains(IssueContentSource.COMMENTS) || selected.contains(GeneralReviewCommentContentSource.KIND))
            ids.addAll(jdbc.queryForList(
                    "SELECT author_id FROM issue_comment WHERE issue_id=? AND author_id IS" + " NOT NULL",
                    Long.class,
                    work.getId()));
        if (selected.contains(PullRequestContentSource.COMMENTS) || selected.contains(ReviewThreadContentSource.KIND))
            ids.addAll(jdbc.queryForList(
                    "SELECT author_id FROM pull_request_review_comment WHERE"
                            + " pull_request_id=? AND author_id IS NOT NULL",
                    Long.class,
                    work.getId()));
        if (selected.contains(GeneralReviewCommentContentSource.KIND))
            ids.addAll(jdbc.queryForList(
                    "SELECT author_id FROM pull_request_review WHERE pull_request_id=? AND" + " author_id IS NOT NULL",
                    Long.class,
                    work.getId()));
        if (selected.contains(ReviewThreadContentSource.KIND))
            ids.addAll(jdbc.queryForList(
                    "SELECT resolved_by_id FROM pull_request_review_thread WHERE"
                            + " pull_request_id=? AND resolved_by_id IS NOT NULL",
                    Long.class,
                    work.getId()));
        if (work instanceof PullRequest && selected.contains(PullRequestContentSource.DIFF))
            ids.addAll(jdbc.query(
                    "SELECT DISTINCT roles.user_id FROM commit_pull_request cp JOIN ("
                            + "SELECT id AS commit_id,author_id AS user_id FROM git_commit UNION ALL "
                            + "SELECT id,committer_id FROM git_commit UNION ALL "
                            + "SELECT commit_id,user_id FROM commit_contributor) roles ON roles.commit_id=cp.commit_id "
                            + "WHERE cp.pull_request_id=? AND roles.user_id IS NOT NULL",
                    (rs, row) -> rs.getLong("user_id"),
                    work.getId()));
        if (work instanceof PullRequest && selected.contains(RepositoryTreeContentSource.KIND))
            ids.addAll(jdbc.query(
                    "SELECT DISTINCT roles.user_id FROM git_commit c JOIN ("
                            + "SELECT id AS commit_id,author_id AS user_id FROM git_commit UNION ALL "
                            + "SELECT id,committer_id FROM git_commit UNION ALL "
                            + "SELECT commit_id,user_id FROM commit_contributor) roles ON roles.commit_id=c.id "
                            + "WHERE c.repository_id=? AND roles.user_id IS NOT NULL",
                    (rs, row) -> rs.getLong("user_id"),
                    Objects.requireNonNull(work.getRepository()).getId()));
        users.findAllById(ids.stream().distinct().toList()).forEach(contributors::add);
        consent.require(contributors);
    }
}
