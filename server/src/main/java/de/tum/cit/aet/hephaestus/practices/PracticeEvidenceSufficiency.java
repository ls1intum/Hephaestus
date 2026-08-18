package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether the selected evidence is enough when every requirement passes")
public enum PracticeEvidenceSufficiency {
    SUFFICIENT_WHEN_REQUIREMENTS_MET,
    DECLARED_EVIDENCE_INSUFFICIENT,
    NONE,
}
