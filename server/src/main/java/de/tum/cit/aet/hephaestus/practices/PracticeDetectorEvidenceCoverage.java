package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether the declared integration evidence can support every detector judgment")
public enum PracticeDetectorEvidenceCoverage {
    DECLARED_REQUIREMENTS_SUFFICIENT,
    CONDITIONAL,
    NONE,
}
