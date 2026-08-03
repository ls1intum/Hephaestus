package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Validity of an evaluation claim against the practice revision currently in force")
public enum EvaluationClaimStatus {
    CURRENT,
    STALE,
    UNVERIFIABLE;

    public static EvaluationClaimStatus of(@Nullable PracticeRevision evaluated, Practice practice) {
        return of(evaluated == null ? null : evaluated.getId(), revisionId(practice.getCurrentRevision()));
    }

    public static EvaluationClaimStatus of(@Nullable Long evaluatedRevisionId, @Nullable Long currentRevisionId) {
        if (evaluatedRevisionId == null || currentRevisionId == null) {
            return UNVERIFIABLE;
        }
        return evaluatedRevisionId.equals(currentRevisionId) ? CURRENT : STALE;
    }

    private static @Nullable Long revisionId(@Nullable PracticeRevision revision) {
        return revision == null ? null : revision.getId();
    }
}
