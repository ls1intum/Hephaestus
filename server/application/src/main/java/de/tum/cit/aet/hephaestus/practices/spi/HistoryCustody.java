package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import java.util.List;
import java.util.UUID;

public record HistoryCustody(
        boolean complete,
        @JsonSetter(nulls = Nulls.AS_EMPTY) boolean refused,
        List<PersonCopyIdentity> copiedIdentities,
        List<Long> repositoryIds,
        List<UUID> sourceJobIds) {
    public HistoryCustody {
        complete = complete && !refused;
        copiedIdentities = List.copyOf(copiedIdentities);
        repositoryIds = List.copyOf(repositoryIds);
        sourceJobIds = List.copyOf(sourceJobIds);
    }
}
