package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CitationCheck(
        long workspaceId,
        ItemRef item,
        ItemVersion version,
        @Nullable ItemVersion baseVersion,
        EvidenceDirectory evidence,
        List<CitationToCheck> citations,
        List<BasisToCheck> bases) {
    public CitationCheck {
        if (workspaceId != item.workspaceId())
            throw new IllegalArgumentException("Citation workspace does not match the item");
        citations = List.copyOf(citations);
        bases = List.copyOf(bases);
    }
}
