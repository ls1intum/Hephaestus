package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentProvenance;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository.StoredComment;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The evidence identity of an issue update, one rule for every owner that keys, admits, coalesces,
 * supersedes or captures it.
 *
 * <p>An issue's fields and discussion form its identity: a comment can clarify the current request
 * or record what was done and what moved elsewhere. A comment written, edited or removed is new
 * evidence. Comments identified by recorded delivery provenance are never evidence, so a posted
 * piece of feedback does not occasion a review of itself.
 */
@Component
public class IssueEvidenceVersion {

    private final IssueCommentRepository comments;
    private final IssueCommentProvenance deliveredComments;

    public IssueEvidenceVersion(IssueCommentRepository comments, IssueCommentProvenance deliveredComments) {
        this.comments = comments;
        this.deliveredComments = deliveredComments;
    }

    /**
     * The comments an issue review reads, as stored: non-empty, not Hephaestus's own, oldest first.
     */
    public List<StoredComment> reviewedComments(long issueId) {
        Set<Long> deliveredIds = deliveredComments.deliveredIds(issueId);
        return comments.findStoredByIssueId(issueId).stream()
                .filter(comment -> !deliveredIds.contains(comment.getNativeId()))
                .toList();
    }

    public ItemVersion of(ScmEventPayload.IssueData issue) {

        List<@Nullable String> discussion = new ArrayList<>();
        for (StoredComment comment : reviewedComments(issue.id())) {
            discussion.add(String.valueOf(comment.getNativeId()));
            discussion.add(comment.getAuthorLogin());
            discussion.add(text(comment.getCreatedAt()));
            discussion.add(text(comment.getUpdatedAt()));
            discussion.add(comment.getBody());
        }
        List<@Nullable String> parts = new ArrayList<>();
        parts.add(issue.title());
        parts.add(issue.body());
        parts.add(issue.state().name());
        parts.add(issue.stateReason());
        parts.add(issue.issueType());
        parts.add(issue.milestone());
        parts.addAll(issue.labels().stream().sorted().toList());
        parts.add(null);
        parts.addAll(issue.assignees().stream().sorted().toList());
        parts.add(null);
        parts.addAll(discussion);
        return ScmVersion.ofContentDigest(parts.toArray(String[]::new));
    }

    private static @Nullable String text(@Nullable Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
