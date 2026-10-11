package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.MemberAiRoutingAdapter;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobPracticeScriptRunRepository.LatestRunRow;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptAsOfDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelPurpose;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptNeedDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptSummaries;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptSummaryDTO;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * What each practice's script needs. A script declares its models only when it runs, so the needs
 * come from the newest run that reported them, and only while that run's script is still the practice's
 * script. Whether a need is met comes from today's routing, so binding a model shows at once.
 */
@Service
@RequiredArgsConstructor
class PracticeNeedsAdapter implements PracticeScriptSummaries {

    private static final TypeReference<List<StoredModelUse>> MODELS = new TypeReference<>() {};

    private final AgentJobPracticeScriptRunRepository runs;
    private final MemberAiRoutingAdapter routing;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PracticeScriptSummaryDTO> latestCurrent(long workspaceId) {
        Map<PracticeScriptModelPurpose, List<DataHandlingTier>> unserved =
                new EnumMap<>(PracticeScriptModelPurpose.class);
        return runs.findLatestRunPerPracticeByWorkspaceId(workspaceId).stream()
                .map(row -> summary(
                        row,
                        purpose -> unserved.computeIfAbsent(
                                purpose, p -> routing.unservedTiers(workspaceId, AgentPurpose.valueOf(p.name())))))
                .toList();
    }

    private PracticeScriptSummaryDTO summary(
            LatestRunRow row, Function<PracticeScriptModelPurpose, List<DataHandlingTier>> unserved) {
        UUID jobId = row.getJobId();
        Instant finishedAt = row.getFinishedAt();
        String models = row.getModels();
        if (jobId == null || finishedAt == null || models == null) {
            return new PracticeScriptSummaryDTO(row.getPracticeSlug(), row.getPracticeName(), null, false, List.of());
        }
        if (!row.getCurrent()) {
            return new PracticeScriptSummaryDTO(row.getPracticeSlug(), row.getPracticeName(), null, true, List.of());
        }
        List<PracticeScriptNeedDTO> needs = objectMapper.readValue(models, MODELS).stream()
                .map(use -> new PracticeScriptNeedDTO(use.purpose(), use.need(), unserved.apply(use.purpose())))
                .toList();
        return new PracticeScriptSummaryDTO(
                row.getPracticeSlug(),
                row.getPracticeName(),
                new PracticeScriptAsOfDTO(jobId, finishedAt),
                false,
                needs);
    }
}
