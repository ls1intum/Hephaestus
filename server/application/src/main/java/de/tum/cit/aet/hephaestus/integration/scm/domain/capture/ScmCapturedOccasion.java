package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * What an event review's occasion retained for its sources: the work record {@link PullRequestContentSource#workRecord}
 * produced when the occasion was recorded, and the version of the linked issues' material it was recorded with.
 * Sources stage it instead of rereading the work, which may have changed since.
 *
 * @param decision a submitted review's decision as {@link ReviewThreadContentSource#occasionDecision} retained it;
 *     null for a pull request occasion
 * @param pairAttested whether the occasion is about the change between its diff base and head; a review submitted on
 *     an older commit is not, so no diff or commit of that pair is staged with its words
 */
public record ScmCapturedOccasion(
        ObjectNode record,
        @Nullable ObjectNode decision,
        @Nullable String linkedContextVersion,
        boolean pairAttested) {
    public ScmCapturedOccasion {
        record = record.deepCopy();
        decision = decision == null ? null : decision.deepCopy();
    }

    @Override
    public ObjectNode record() {
        return record.deepCopy();
    }

    @Override
    public @Nullable ObjectNode decision() {
        return decision == null ? null : decision.deepCopy();
    }
}
