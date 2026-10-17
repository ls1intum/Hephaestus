package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record FeedbackModeRollupDTO(int off, int approveFirst, int automatic, FeedbackMode defaultMode) {}
