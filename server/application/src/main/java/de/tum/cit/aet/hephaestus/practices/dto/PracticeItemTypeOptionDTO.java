package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.practices.spi.ApplicabilityClauseKind;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeItemTypeOptionDTO(
        String kind,
        String label,
        List<ItemEventOptionDTO> events,
        List<AboutRole> aboutRoles,
        List<ReadSourceOptionDTO> readSources,
        List<StateDimensionDTO> reviewOnStateDimensions,
        boolean scanSupported,
        List<String> scanItemKinds,
        List<ApplicabilityClauseKind> applicabilityClauses) {
    public PracticeItemTypeOptionDTO {
        events = List.copyOf(events);
        aboutRoles = List.copyOf(aboutRoles);
        readSources = List.copyOf(readSources);
        reviewOnStateDimensions = List.copyOf(reviewOnStateDimensions);
        scanItemKinds = List.copyOf(scanItemKinds);
        applicabilityClauses = List.copyOf(applicabilityClauses);
    }
}
