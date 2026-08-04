package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Server-supported options for authoring practice evidence requirements")
public record PracticeEvidenceAuthoringDTO(@NonNull List<PracticeEvidenceArtifactOptionsDTO> artifacts) {}
