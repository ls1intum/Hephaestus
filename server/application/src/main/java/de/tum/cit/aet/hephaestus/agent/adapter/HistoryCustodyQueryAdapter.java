package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoryCustodyQueryAdapter implements HistoryCustodyQuery {
    private final EvidenceFolderPersonDataCatalog copies;

    @Override
    public HistoryCustody find(long workspaceId, List<UUID> producingJobIds) {
        return copies.custody(workspaceId, producingJobIds);
    }
}
