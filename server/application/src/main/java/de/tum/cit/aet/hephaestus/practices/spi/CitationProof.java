package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CitationProof(CitationVerificationStatus status, String artifactSha256, String quoteSha256) {}
