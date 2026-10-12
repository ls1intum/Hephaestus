package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import java.time.Instant;
import java.util.UUID;

/**
 * The results the trend and standing tests read: one piece of work a practice reviewed, and what it made
 * of it. A pull request unless a test says otherwise, and a run of its own unless the test names one, so a
 * test spells out only the facts it asserts on.
 */
public final class TrendResults {

    private TrendResults() {}

    /** The practice was demonstrated on this piece of work. */
    public static PracticeResult clean(long artifactId, String observedAt) {
        return judged(artifactId, observedAt, Outcome.MET);
    }

    /** {@link #clean} on a kind other than a pull request. */
    public static PracticeResult clean(long artifactId, String observedAt, ArtifactKind kind) {
        return practiceResult(artifactId, UUID.randomUUID(), observedAt, kind, Outcome.MET);
    }

    /** {@link #clean} on a named run. */
    public static PracticeResult clean(long artifactId, UUID jobId, String observedAt) {
        return practiceResult(artifactId, jobId, observedAt, ArtifactKinds.PULL_REQUEST, Outcome.MET);
    }

    /** The practice was there to judge and was judged: {@code BAD} is a problem in what was done. */
    public static PracticeResult judged(long artifactId, String observedAt, Outcome outcome) {
        return judged(artifactId, UUID.randomUUID(), observedAt, outcome);
    }

    /** {@link #judged} on a named run. */
    public static PracticeResult judged(long artifactId, UUID jobId, String observedAt, Outcome outcome) {
        return practiceResult(artifactId, jobId, observedAt, ArtifactKinds.PULL_REQUEST, outcome);
    }

    /** The practice was missing from this piece of work. */
    public static PracticeResult problem(long artifactId, String observedAt) {
        return problem(artifactId, UUID.randomUUID(), observedAt);
    }

    /** {@link #problem} on a named run. */
    public static PracticeResult problem(long artifactId, UUID jobId, String observedAt) {
        return practiceResult(artifactId, jobId, observedAt, ArtifactKinds.PULL_REQUEST, Outcome.NOT_MET);
    }

    /** The practice looked at this piece of work and found nothing to judge. */
    public static PracticeResult noVerdict(long artifactId, String observedAt) {
        return noVerdict(artifactId, UUID.randomUUID(), observedAt);
    }

    /** {@link #noVerdict} on a named run. */
    public static PracticeResult noVerdict(long artifactId, UUID jobId, String observedAt) {
        return practiceResult(artifactId, jobId, observedAt, ArtifactKinds.PULL_REQUEST, Outcome.NOT_APPLICABLE);
    }

    private static PracticeResult practiceResult(
            long artifactId, UUID jobId, String observedAt, ArtifactKind kind, Outcome outcome) {
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .agentJobId(jobId)
                .artifactKind(kind)
                .artifactId(artifactId)
                .outcome(outcome)
                .observedAt(Instant.parse(observedAt))
                .build();
    }
}
