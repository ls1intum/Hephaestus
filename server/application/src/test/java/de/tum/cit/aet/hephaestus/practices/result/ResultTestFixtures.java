package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Result;

public final class ResultTestFixtures {
    private ResultTestFixtures() {}

    public static Result.ResultBuilder resultBuilder(Result row) {
        return Result.builder()
                .id(row.getId())
                .workspaceId(row.getWorkspaceId())
                .reviewId(row.getReviewId())
                .practiceId(row.getPracticeId())
                .practiceVersionId(row.getPracticeVersionId())
                .itemId(row.getItemId())
                .itemVersion(row.getItemVersion())
                .developerId(row.getDeveloperId())
                .outcome(row.getOutcome())
                .severity(row.getSeverity())
                .summary(row.getSummary())
                .explanation(row.getExplanation())
                .evidence(row.getEvidence())
                .example(row.getExample())
                .coverage(row.getCoverage())
                .evidenceVersion(row.getEvidenceVersion())
                .recordedAt(row.getRecordedAt())
                .endedAt(row.getEndedAt())
                .endReason(row.getEndReason());
    }
}
