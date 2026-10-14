package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import java.util.List;
import java.util.UUID;

public record HistoryCustody(
        boolean complete,
        List<PersonCopyIdentity> copiedIdentities,
        List<Long> repositoryIds,
        List<UUID> sourceJobIds) {
    public HistoryCustody {
        copiedIdentities = List.copyOf(copiedIdentities);
        repositoryIds = List.copyOf(repositoryIds);
        sourceJobIds = List.copyOf(sourceJobIds);
    }
}
