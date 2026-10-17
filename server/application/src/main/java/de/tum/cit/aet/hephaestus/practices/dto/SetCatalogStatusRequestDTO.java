package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.practices.ClosedPracticeInput;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SetCatalogStatusRequestDTO(
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL)
        boolean excluded) implements ClosedPracticeInput {}
