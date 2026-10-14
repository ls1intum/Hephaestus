package de.tum.cit.aet.hephaestus.agent.scan;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.gateway.SandboxGatewayProperties;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobInput;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMemberAiPolicy;
import de.tum.cit.aet.hephaestus.agent.job.ReviewMethodVerifier;
import de.tum.cit.aet.hephaestus.agent.usage.LlmAdmissionService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ScanExecutionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reserves the finite worst cost before the single next file job enters the queue. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScanBudget {
    private final ReviewMethodVerifier methods;
    private final ReviewJobRepository jobs;
    private final SandboxGatewayProperties gateway;
    private final JdbcClient jdbc;
    private final ReviewMemberAiPolicy policy;
    private final LlmAdmissionService admission;
    private final LlmBudgetService monthly;
    private final PracticeReviewProperties limits;

    public boolean permits(ScanExecutionSnapshot scan, List<ReviewAssignmentSnapshot> selected) {
        var job = new ReviewJob();
        job.setId(UUID.randomUUID());
        job.setReviewId(scan.review().id());
        job.setWorkspaceId(scan.review().workspaceId());
        job.setInput(new ReviewJobInput(
                selected.getFirst().item().itemId(),
                selected.stream().map(ReviewAssignmentSnapshot::id).toList()));
        var binding = policy.binding(job)
                .orElseThrow(() -> new IllegalStateException("The frozen scan model is unavailable"));
        methods.require(selected.getFirst().practice().method(), "review", binding);
        var review = admission.admit(binding);
        if (monthly.decide(job.getWorkspaceId()).blocks(review.price().fundingSource())) return false;
        var calls = Math.multiplyExact((long) Math.max(3, selected.size()), limits.practiceModelCalls());
        Integer contextWindow = review.resolved().contextWindow();
        if (contextWindow == null) throw new IllegalStateException("A budgeted scan requires a bounded model context");
        int input = contextWindow;
        var output = Objects.requireNonNullElse(review.resolved().maxOutputTokens(), input);
        if (input <= 0 || output <= 0)
            throw new IllegalStateException("The frozen scan model has no finite token bound");
        BigDecimal estimate = cost(
                        review.price(),
                        Math.multiplyExact(calls, Math.min(input, gateway.maxRequestBytes())),
                        Math.multiplyExact(calls, output))
                .add(BigDecimal.valueOf(calls).movePointLeft(6));
        BigDecimal directRate = rate(review.price());
        long directOvershoot = Math.addExact(Math.min(input, gateway.maxRequestBytes()), output);
        for (var purpose : AgentPurpose.practiceScript()) {
            boolean present = selected.getFirst().practice().method().models().stream()
                    .anyMatch(slot -> slot.kind() == purpose.kind() && slot.model() != null);
            if (!present) continue;
            var directBinding = policy.practiceScriptBinding(job, purpose)
                    .orElseThrow(() -> new IllegalStateException("A frozen direct scan model is unavailable"));
            methods.require(
                    selected.getFirst().practice().method(),
                    purpose.kind().name().toLowerCase(Locale.ROOT),
                    directBinding);
            var model = admission.admit(directBinding);
            if ("cohere-rerank".equals(model.resolved().apiProtocol())
                    && model.price().pricingState() != UsagePricingState.NO_CHARGE)
                throw new IllegalStateException("A token price cannot reserve billed search units");
            if (monthly.decide(job.getWorkspaceId()).blocks(model.price().fundingSource())) return false;
            directRate = directRate.max(rate(model.price()));
            long context = Objects.requireNonNull(
                    model.resolved().contextWindow(), "A budgeted direct model requires a bounded context");
            long maxOutput = Objects.requireNonNullElse(model.resolved().maxOutputTokens(), Math.toIntExact(context));
            if (context <= 0 || maxOutput <= 0)
                throw new IllegalStateException("The direct model has no finite token bound");
            directOvershoot =
                    Math.max(directOvershoot, Math.addExact(Math.min(context, gateway.maxRequestBytes()), maxOutput));
        }
        estimate = estimate.add(directRate
                .multiply(BigDecimal.valueOf(Math.addExact(limits.directModelTokensPerAttempt(), directOvershoot)))
                .movePointLeft(6));
        var spent = spent(job.getWorkspaceId(), job.getReviewId());
        return spent.priced() && spent.cost().add(estimate).compareTo(scan.budgetUsd()) <= 0;
    }

    public Spend spent(long workspaceId, UUID reviewId) {
        return jdbc.sql("SELECT COALESCE(SUM(cost_usd),0) cost,COALESCE(bool_and(cost_usd IS NOT NULL AND"
                        + " pricing_state<>'UNPRICED'),true) priced FROM llm_usage_event WHERE workspace_id=:ws"
                        + " AND review_id=:review")
                .param("ws", workspaceId)
                .param("review", reviewId)
                .query((rs, n) -> new Spend(Objects.requireNonNull(rs.getBigDecimal("cost")), rs.getBoolean("priced")))
                .single();
    }

    public static BigDecimal cost(LlmPriceSnapshot price, long input, long output) {
        if (price.pricingState() == UsagePricingState.NO_CHARGE) return BigDecimal.ZERO;
        if (price.pricingState() == UsagePricingState.UNPRICED)
            throw new IllegalStateException("A scan cannot reserve an unpriced model");
        return inputRate(price)
                .multiply(BigDecimal.valueOf(input))
                .add(Objects.requireNonNullElse(price.per1mOutputUsd(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(output)))
                .movePointLeft(6);
    }

    static BigDecimal rate(LlmPriceSnapshot price) {
        if (price.pricingState() == UsagePricingState.NO_CHARGE) return BigDecimal.ZERO;
        if (price.pricingState() == UsagePricingState.UNPRICED)
            throw new IllegalStateException("A scan cannot reserve an unpriced model");
        return inputRate(price).max(Objects.requireNonNullElse(price.per1mOutputUsd(), BigDecimal.ZERO));
    }

    private static BigDecimal inputRate(LlmPriceSnapshot price) {
        var rate = Objects.requireNonNullElse(price.per1mInputUsd(), BigDecimal.ZERO);
        if (price.per1mCacheReadUsd() != null) rate = rate.max(price.per1mCacheReadUsd());
        if (price.per1mCacheWriteUsd() != null) rate = rate.max(price.per1mCacheWriteUsd());
        return rate;
    }

    /** Includes all completed calls of every model in the active attempt; settled attempts are already in the ledger. */
    public BigDecimal inFlight(ReviewJob job, ConfigSnapshot snapshot) {
        var price = snapshot.priceSnapshot();
        if (price == null) throw new IllegalStateException("The active scan has no frozen price");
        BigDecimal total = Objects.requireNonNull(price.calculateCost(
                        job.getLlmInputTokens(),
                        job.getLlmOutputTokens(),
                        job.getLlmCacheReadTokens(),
                        job.getLlmCacheWriteTokens())
                .usd());
        for (var used : jobs.sumPracticeScriptUsageByKind(job.getWorkspaceId(), job.getId(), job.getAttempt())) {
            var model = used.modelKind() == ModelKind.CHAT
                    ? snapshot.model()
                    : snapshot.practiceScriptSlot(used.modelKind());
            if (model == null || model.priceSnapshot() == null || used.searchUnits() > 0)
                throw new IllegalStateException("The scan's direct spend is unverifiable");
            total = total.add(Objects.requireNonNull(model.priceSnapshot()
                    .calculateCost(
                            used.inputTokens(), used.outputTokens(), used.cacheReadTokens(), used.cacheWriteTokens())
                    .usd()));
        }
        return total;
    }

    public record Spend(BigDecimal cost, boolean priced) {}
}
