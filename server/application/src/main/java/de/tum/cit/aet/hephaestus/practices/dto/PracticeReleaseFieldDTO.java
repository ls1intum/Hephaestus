package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionField;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeReleaseFieldDTO(
        PracticeDefinitionField field, boolean changedLocally, boolean offeredChanged, boolean conflict) {}
