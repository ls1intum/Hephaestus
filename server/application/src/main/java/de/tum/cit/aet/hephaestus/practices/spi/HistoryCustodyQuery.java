package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.List;
import java.util.UUID;

public interface HistoryCustodyQuery {
    HistoryCustody find(long workspaceId, List<UUID> producingJobIds);
}
