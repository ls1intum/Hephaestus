package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptKindTotal;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ScriptUsage;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnWorkerRole
class ProxyRoutingService {
    private final ReviewJobRepository agentJobRepository;
    private final ObjectMapper objectMapper;
    private final MentorProxyCredentialRegistry mentors;

    ProxyRoutingService(ReviewJobRepository jobs, ObjectMapper mapper, MentorProxyCredentialRegistry mentors) {
        this.agentJobRepository = jobs;
        this.objectMapper = mapper;
        this.mentors = mentors;
    }

    ProxyRouting resolve(ProxyIdentity identity, HttpServletRequest request) {
        if (identity.mentor() != null) {
            if (request.getRequestURI().startsWith(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PATH))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review job credential required");
            return mentors.resolve(identity.mentor())
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mentor execution unavailable"));
        }
        var jwt = Objects.requireNonNull(identity.job());
        var job = agentJobRepository
                .findById(jwt.jobId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Review attempt unavailable"));
        if (job.getStatus() != ReviewJobStatus.RUNNING
                || job.getAttempt() != jwt.attempt()
                || job.getWorkspaceId() != jwt.workspaceId()
                || !Objects.equals(job.getWorkerId(), identity.workerId()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Review attempt unavailable");
        var snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
        // The live checks above decide whether this attempt may call at all; the signed deadline decides until when.
        Instant workDeadline = jwt.workDeadline();
        if (!request.getRequestURI().startsWith(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PATH))
            return reviewRouting(job, snapshot, workDeadline);
        var slot = practiceScriptSlot(request.getRequestURI());
        if (slot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return practiceScriptRouting(job, snapshot, slot, workDeadline)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ProxyRouting reviewRouting(ReviewJob job, ConfigSnapshot snapshot, Instant workDeadline) {
        return routing(
                "job:" + job.getId(),
                job,
                snapshot.model(),
                UsageSourceType.REVIEW_JOB,
                spentSoFarUsd(job, snapshot),
                0L,
                0L,
                null,
                workDeadline);
    }

    // Include all direct models' in-flight spend until their ledger rows exist; only the same funding source counts.
    private Optional<ProxyRouting> practiceScriptRouting(
            ReviewJob job, ConfigSnapshot snapshot, ModelKind slot, Instant workDeadline) {
        FrozenModel model = slot == ModelKind.CHAT ? snapshot.model() : snapshot.practiceScriptSlot(slot);
        if (model == null) {
            return Optional.empty();
        }
        List<PracticeScriptKindTotal> used =
                agentJobRepository.sumPracticeScriptUsageByKind(job.getWorkspaceId(), job.getId(), job.getAttempt());
        return Optional.of(routing(
                "job:" + job.getId() + ":practice-script",
                job,
                model,
                ScriptUsage.ledgerSourceType(slot),
                practiceScriptSpentUsd(job, snapshot, used, model.connectionScope()),
                used.stream().mapToLong(PracticeScriptKindTotal::tokens).sum(),
                used.stream().mapToLong(PracticeScriptKindTotal::searchUnits).sum(),
                slot,
                workDeadline));
    }

    private static ProxyRouting routing(
            String principal,
            ReviewJob job,
            FrozenModel model,
            UsageSourceType sourceType,
            BigDecimal spentUsd,
            long directModelTokens,
            long directModelSearchUnits,
            @Nullable ModelKind slot,
            Instant workDeadline) {
        return new ProxyRouting(
                principal,
                model.apiProtocol().wire(),
                model.baseUrl(),
                model.connectionScope(),
                model.connectionId(),
                model.modelId(),
                job.getWorkspaceId(),
                new ProxyRouting.BilledAttempt(
                        sourceType,
                        job.getId(),
                        job.getAttempt(),
                        spentUsd,
                        job.getWorkerId(),
                        directModelTokens,
                        directModelSearchUnits),
                slot,
                slot == null ? null : model.dataHandlingTier(),
                workDeadline);
    }

    private static BigDecimal practiceScriptSpentUsd(
            ReviewJob job,
            ConfigSnapshot snapshot,
            List<PracticeScriptKindTotal> used,
            @Nullable UsageFundingSource purse) {
        BigDecimal spent = Objects.equals(snapshot.model().connectionScope(), purse)
                ? spentSoFarUsd(job, snapshot)
                : BigDecimal.ZERO;
        for (PracticeScriptKindTotal total : used) {
            FrozenModel model = snapshot.practiceScriptSlot(total.modelKind());
            LlmPriceSnapshot price = model == null ? null : model.priceSnapshot();
            if (model == null || price == null || !Objects.equals(model.connectionScope(), purse)) {
                continue;
            }
            BigDecimal cost = price.calculateCost(
                            total.inputTokens(),
                            total.outputTokens(),
                            total.cacheReadTokens(),
                            total.cacheWriteTokens())
                    .usd();
            if (cost != null) {
                spent = spent.add(cost);
            }
        }
        return spent;
    }

    static @Nullable ModelKind practiceScriptSlot(String path) {
        String rest = path.substring(JobTokenAuthenticationFilter.PRACTICE_SCRIPT_PATH.length());
        int end = rest.indexOf('/');
        if (end <= 0) {
            return null;
        }
        String slot = rest.substring(0, end);
        return Arrays.stream(ModelKind.values())
                .filter(kind -> kind.name().toLowerCase(Locale.ROOT).equals(slot))
                .findFirst()
                .orElse(null);
    }

    // Budget gates use frozen rates; reasoning tokens are already included in output.
    private static BigDecimal spentSoFarUsd(ReviewJob job, ConfigSnapshot snapshot) {
        LlmPriceSnapshot price = snapshot.model().priceSnapshot();
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cost = price.calculateCost(
                        job.getLlmInputTokens(),
                        job.getLlmOutputTokens(),
                        job.getLlmCacheReadTokens(),
                        job.getLlmCacheWriteTokens())
                .usd();
        return cost != null ? cost : BigDecimal.ZERO;
    }
}
