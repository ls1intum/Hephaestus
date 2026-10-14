package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import java.util.Objects;

public record PracticeReadSource(
        @JsonProperty(required = true) SourceKind source,
        @JsonProperty(required = true) ReadNeed need) implements ClosedPracticeInput {
    public PracticeReadSource {
        Objects.requireNonNull(source);
        Objects.requireNonNull(need);
    }
}
