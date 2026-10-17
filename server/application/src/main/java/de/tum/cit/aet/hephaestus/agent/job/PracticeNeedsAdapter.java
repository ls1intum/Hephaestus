package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.MemberAiRoutingAdapter;
import de.tum.cit.aet.hephaestus.agent.job.ScriptRunRepository.LatestRunRow;
import de.tum.cit.aet.hephaestus.practices.spi.ModelSlot;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptAsOfDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNeedDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptSummaries;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptSummaryDTO;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Script needs come only from the newest report for the current script; availability uses current routing.
@Service
@RequiredArgsConstructor
class PracticeNeedsAdapter implements PracticeScriptSummaries {

    private static final TypeReference<List<StoredModelUse>> MODELS = new TypeReference<>() {};

    private final ScriptRunRepository runs;
    private final MemberAiRoutingAdapter routing;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PracticeScriptSummaryDTO> latestCurrent(long workspaceId) {
        Map<ModelSlot, List<DataHandlingTier>> unserved = new EnumMap<>(ModelSlot.class);
        return runs.findLatestRunPerPracticeByWorkspaceId(workspaceId).stream()
                .map(row -> summary(
                        row,
                        purpose -> unserved.computeIfAbsent(
                                purpose, p -> routing.unservedTiers(workspaceId, AgentPurpose.forSlot(p)))))
                .toList();
    }

    private PracticeScriptSummaryDTO summary(LatestRunRow row, Function<ModelSlot, List<DataHandlingTier>> unserved) {
        UUID jobId = row.getJobId();
        Instant finishedAt = row.getFinishedAt();
        String models = row.getModels();
        if (jobId == null || row.getReviewId() == null || finishedAt == null || models == null) {
            return new PracticeScriptSummaryDTO(row.getPracticeSlug(), row.getPracticeName(), null, false, List.of());
        }
        if (!row.getCurrent()) {
            return new PracticeScriptSummaryDTO(row.getPracticeSlug(), row.getPracticeName(), null, true, List.of());
        }
        List<PracticeScriptNeedDTO> needs = objectMapper.readValue(models, MODELS).stream()
                .map(use -> new PracticeScriptNeedDTO(use.slot(), use.need(), unserved.apply(use.slot())))
                .toList();
        return new PracticeScriptSummaryDTO(
                row.getPracticeSlug(),
                row.getPracticeName(),
                new PracticeScriptAsOfDTO(jobId, Objects.requireNonNull(row.getReviewId()), finishedAt),
                false,
                needs);
    }
}
