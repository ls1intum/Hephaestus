package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;

public record CitationRefusal(int index, CandidateRefusal reason, String message) {}
