package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenQuestion(
        String openQuestion, String wouldSettleIt, @Nullable EvidenceCitation citation) {}
