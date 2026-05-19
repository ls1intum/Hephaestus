package de.tum.in.www1.hephaestus.agent.context.providers.mentor;

/**
 * One-row projection consumed by {@link UserAspectProvider}. Collapses the previous nine
 * separate count round-trips into a single SELECT — see
 * {@link MentorAspectQueryRepository#fetchUserCounts} for the JPQL.
 *
 * <p>Every bucket is wrapped in {@code COALESCE(..., 0L)} on the SQL side so the boxed
 * {@code Long}s are guaranteed non-null; the accessors unbox safely without an NPE risk.
 */
public record MentorUserCounts(
    Long openPRs,
    Long mergedThisWeek,
    Long mergedLastWeek,
    Long openIssues,
    Long reviewsGivenThisWeek,
    Long reviewsGivenLastWeek,
    Long reviewsReceivedThisWeek,
    Long pendingReviewRequests,
    Long unresolvedThreads
) {}
