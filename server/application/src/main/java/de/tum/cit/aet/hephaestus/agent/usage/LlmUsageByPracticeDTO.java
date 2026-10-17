package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(
        description = "The decision, embedding and reranking calls that one practice's script made in "
                + "finished reviews this month. The cost of each review's calls is split between its practices by "
                + "the priced tokens that each used.")
public record LlmUsageByPracticeDTO(
        @Nullable
        @Schema(
                description = "The practice slug. Null for calls that cannot be split by practice any more, such as "
                        + "those of a deleted review.")
        String practiceSlug,

        @Nullable @Schema(description = "The practice name. Null when the slug names no current practice.")
        String practiceName,

        @NonNull @Schema(description = "The models for practice scripts that the script called.")
        List<AgentPurpose> purposes,

        @NonNull
        @Schema(
                description = "Reviews in which the script called a model for practice scripts. A review with several "
                        + "practices counts for each of them.")
        Long reviews,

        @NonNull Long calls,
        @NonNull Long inputTokens,
        @NonNull Long outputTokens,

        @NonNull @Schema(description = "This practice's share of the spend on shared (instance) models, in USD.")
        BigDecimal instanceTotalCostUsd,

        @NonNull
        @Schema(description = "This practice's share of the spend on this workspace's own provider(s), in USD.")
        BigDecimal ownProviderTotalCostUsd,

        @NonNull
        @Schema(
                description = "Reviews with a practice script ledger row whose price is not yet known and that holds "
                        + "this practice's calls, each review counted once. Those rows are excluded from both "
                        + "totals above. A review with several practices counts for each of them.")
        Long unpricedEventCount) {}
