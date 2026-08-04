package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@Schema(description = "Whether a finding was produced using the current review rules")
public enum AssessmentClaimCurrentness {
    CURRENT,
    STALE,
    UNVERIFIABLE;

    public static AssessmentClaimCurrentness of(@Nullable PracticeRevision evaluated, Practice practice) {
        PracticeRevision current = practice.getCurrentRevision();
        if (
            evaluated != null &&
            current != null &&
            !evaluated.equals(current) &&
            evaluated.getAutomatedAssessmentPolicy() == null &&
            current.getAutomatedAssessmentPolicy() != null
        ) {
            return STALE;
        }
        return of(fingerprint(evaluated), fingerprint(current));
    }

    public static AssessmentClaimCurrentness of(
        @Nullable String evaluatedFingerprint,
        @Nullable String currentFingerprint
    ) {
        if (evaluatedFingerprint == null || currentFingerprint == null) {
            return UNVERIFIABLE;
        }
        return evaluatedFingerprint.equals(currentFingerprint) ? CURRENT : STALE;
    }

    private static @Nullable String fingerprint(@Nullable PracticeRevision revision) {
        return revision == null ? null : revision.getReviewRuleFingerprint();
    }
}
