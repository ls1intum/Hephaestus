package de.tum.in.www1.hephaestus.gitprovider.issue;

/**
 * Represents the type of an issue.
 * Matches GitHub's type field for issue types feature.
 */
public enum IssueType {
    /** Bug report. */
    BUG,
    /** Feature request. */
    FEATURE,
    /** Documentation issue. */
    DOCUMENTATION,
    /** Question. */
    QUESTION,
    /** General issue (no specific type). */
    ISSUE,
}
