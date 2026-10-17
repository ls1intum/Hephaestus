package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DutyBasis(String criterionQuote, int event, String explanation) implements ClaimBasis {}
