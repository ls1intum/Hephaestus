package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Action when required evidence is unavailable, incomplete, or not current")
public enum PracticeInsufficientEvidenceAction {
    SKIP_AUTOMATED_ASSESSMENT,
}
