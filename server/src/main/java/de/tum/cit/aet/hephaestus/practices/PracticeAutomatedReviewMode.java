package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Author-declared approach for automated review; this does not describe human review")
public enum PracticeAutomatedReviewMode {
    LANGUAGE_MODEL,
    NONE,
}
