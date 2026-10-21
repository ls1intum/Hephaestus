package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class HistoryCustodyQueryAdapter implements HistoryCustodyQuery {
    private final EvidenceFolderPersonDataCatalog copies;
    private final ReviewJobRepository jobs;
    private final ObjectMapper mapper;

    @Override
    public HistoryCustody find(long workspaceId, List<UUID> producingJobIds) {
        return copies.custody(workspaceId, producingJobIds);
    }

    /** The retained manifest keeps its capture time when admission retires the staged files. */
    @Override
    public Optional<Instant> capturedAt(long workspaceId, UUID reviewId, UUID producingJobId) {
        var manifest = jobs.findByIdAndWorkspaceId(producingJobId, workspaceId)
                .filter(job -> reviewId.equals(job.getReviewId()))
                .map(job -> job.getCapture())
                .map(capture -> capture.path("manifest"))
                .filter(node -> node.isObject());
        if (manifest.isEmpty()) return Optional.empty();
        try {
            return Optional.of(mapper.treeToValue(manifest.orElseThrow(), JobFolderIndex.Retained.class)
                    .capturedAt());
        } catch (JacksonException invalid) {
            return Optional.empty();
        }
    }
}
