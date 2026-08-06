package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * The relation a person can have to an artifact, and therefore the relations an observation about
 * that artifact may be attributed to.
 *
 * <p>Core owns this enum because consent and delivery depend on it: feedback is always addressed to a
 * developer in one of these relations, never to a mentor or grader, and the gate's permission check
 * runs against a specific relation rather than "anyone touching the artifact". An artifact kind that
 * cannot identify a role must not declare it — an attribution to a role we cannot resolve is an
 * observation with nobody to deliver it to.
 */
public enum ActorRole {
    /** Produced the artifact. The subject of practically every observation the system records today. */
    AUTHOR,

    /**
     * Holds the artifact. This is the relation the review gate checks a permission against, so an
     * artifact kind with no notion of assignment cannot be gated the way pull requests and issues are.
     */
    ASSIGNEE,

    /**
     * Responded to the artifact. Named by the signals about responses ({@code scm.pull_request.reviewed}),
     * and the only relation a practice about the quality of reviewing can be about.
     */
    REVIEWER,
}
