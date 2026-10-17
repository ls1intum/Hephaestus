package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import java.util.List;
import java.util.UUID;

/** Exact retained custody supplied by the owner before history words cross a runtime boundary. */
public record HistoryCopy(
        boolean complete, List<PersonCopyIdentity> identities, List<Long> repositories, List<UUID> sourceJobs) {
    public HistoryCopy {
        identities = List.copyOf(identities);
        repositories = List.copyOf(repositories);
        sourceJobs = List.copyOf(sourceJobs);
    }
}
