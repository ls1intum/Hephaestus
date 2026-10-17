package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackSourceCountDTO(String itemKind, long count) {}
