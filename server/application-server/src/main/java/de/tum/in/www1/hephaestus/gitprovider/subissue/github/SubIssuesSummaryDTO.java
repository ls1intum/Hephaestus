package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

/**
 * DTO for sub-issues summary data from webhook events.
 */
public record SubIssuesSummaryDTO(Integer total, Integer completed, Integer percentCompleted) {
    /**
     * Creates an empty summary with zero values.
     */
    public static SubIssuesSummaryDTO empty() {
        return new SubIssuesSummaryDTO(0, 0, 0);
    }
}
