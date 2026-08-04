package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Independent status; evidence authors cannot promote their own requirements")
public enum PracticeAutomatedAssessmentValidationStatus {
    AUTHOR_DECLARED,
    INDEPENDENTLY_VALIDATED,
    STALE,
    SUPERSEDED,
}
