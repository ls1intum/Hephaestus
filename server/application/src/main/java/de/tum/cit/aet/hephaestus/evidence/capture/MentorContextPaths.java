package de.tum.cit.aet.hephaestus.evidence.capture;

/** Paths shared by context producers and the mentor's fetch protocol. */
public final class MentorContextPaths {
    public static final String MERGE_READINESS = ContentSource.OUTPUT_PREFIX + "merge_readiness.json";
    public static final String RECENT_AUTHORED_WORK = ContentSource.OUTPUT_PREFIX + "recent_authored_work.json";
    public static final int MAX_CHARS = 200_000;

    private MentorContextPaths() {}

    public static String reviewAttempts(String segment, long sourceId) {
        return ContentSource.OUTPUT_PREFIX + "review_attempts/" + segment + "/" + sourceId + ".json";
    }
}
