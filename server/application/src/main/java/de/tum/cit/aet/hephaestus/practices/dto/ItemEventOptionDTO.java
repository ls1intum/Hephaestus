package de.tum.cit.aet.hephaestus.practices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ItemEventOptionDTO(
        String name, String label, boolean recommended, boolean connected, List<IntegrationKind> raisedBy) {
    public ItemEventOptionDTO {
        raisedBy = List.copyOf(raisedBy);
    }
}
