package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InapplicabilityWarrant(List<SourceKind> consulted, String prerequisite, String ruledOutBy) {
    public InapplicabilityWarrant {
        consulted = List.copyOf(consulted);
    }
}
