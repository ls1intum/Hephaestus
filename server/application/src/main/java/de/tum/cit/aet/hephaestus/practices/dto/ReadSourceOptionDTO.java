package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationCapabilities;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReadSourceOptionDTO(String source, String label, List<ReadNeed> needs, CitationCapabilities citation) {
    public ReadSourceOptionDTO {
        needs = List.copyOf(needs);
    }
}
