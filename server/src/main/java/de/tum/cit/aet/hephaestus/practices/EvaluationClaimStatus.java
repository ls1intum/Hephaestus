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
        return of(fingerprint(evaluated), fingerprint(practice.getCurrentRevision()));
    }

    public static EvaluationClaimStatus of(@Nullable String evaluatedFingerprint, @Nullable String currentFingerprint) {
        if (evaluatedFingerprint == null || currentFingerprint == null) {
            return UNVERIFIABLE;
        }
        return evaluatedFingerprint.equals(currentFingerprint) ? CURRENT : STALE;
    }

    private static @Nullable String fingerprint(@Nullable PracticeRevision revision) {
        return revision == null ? null : revision.getDetectionFingerprint();
    }
}
