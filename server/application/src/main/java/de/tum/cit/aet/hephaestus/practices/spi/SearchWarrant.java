package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchWarrant(List<SourceKind> consulted, String lookedFor, String boundary) {
    public SearchWarrant {
        consulted = List.copyOf(consulted);
    }
}
