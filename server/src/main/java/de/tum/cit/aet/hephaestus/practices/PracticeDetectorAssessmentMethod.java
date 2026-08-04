package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How Hephaestus evaluates a practice when its evidence is sufficient")
public enum PracticeDetectorAssessmentMethod {
    MECHANICAL,
    SEMANTIC,
    NONE,
}
