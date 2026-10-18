package de.tum.cit.aet.hephaestus.integration.scm.domain.issue;

import org.jspecify.annotations.Nullable;

/** The progress the stored issue evidence can establish, without treating a partial mirror as a full inventory. */
public record IssueProgress(
        @Nullable Long total,
        @Nullable Long completed,
        @Nullable String source) {
    public static IssueProgress of(Issue issue, IssueRepository.@Nullable ChildRollup children) {
        var total = issue.getSubIssuesTotal();
        var completed = issue.getSubIssuesCompleted();
        if (total != null && completed != null) {
            return new IssueProgress(total.longValue(), completed.longValue(), "provider");
        }
        if (children != null && children.getTotal() > 0) {
            return new IssueProgress(children.getTotal(), children.getCompleted(), "children");
        }
        return new IssueProgress(null, null, null);
    }
}
