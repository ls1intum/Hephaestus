package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Author-declared approach for automated assessment; this does not describe human assessment")
public enum PracticeAutomatedAssessmentMode {
    LANGUAGE_MODEL,
    NONE,
}
