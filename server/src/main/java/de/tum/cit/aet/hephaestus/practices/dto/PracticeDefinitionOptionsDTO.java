package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

@Schema(description = "Available and recommended authoring choices for each type of reviewed work")
public record PracticeDefinitionOptionsDTO(@NonNull List<PracticeWorkTypeDefinitionOptionsDTO> workTypes) {}
