package de.tum.cit.aet.hephaestus.practices.dto;

import de.tum.cit.aet.hephaestus.practices.PracticeEvidenceDeclaration;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Evidence choices and recommended rule for one work artifact")
public record PracticeEvidenceArtifactOptionsDTO(
    @NonNull WorkArtifact artifactType,
    @NonNull PracticeEvidenceDeclaration baseline,
    @NonNull List<PracticeEvidenceSourceOptionDTO> sources
) {}
