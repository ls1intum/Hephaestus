package de.tum.cit.aet.hephaestus.agent.usage;

import org.jspecify.annotations.Nullable;

/**
 * Whether a workspace may still spend, decided separately for each funding source. "Is this workspace
 * blocked?" has no single answer — only "is THIS call blocked?", which depends on who is paying.
 *
 * <p>Keeping the two verdicts apart is what makes the ownership story hold: a workspace admin can
 * never loosen the instance's protection, because they cannot write anything the instance verdict
 * reads.
 */
public record LlmBudgetDecision(LlmBudgetBlockReason instanceFunded, LlmBudgetBlockReason workspaceFunded) {
    public static final LlmBudgetDecision ALLOWED = new LlmBudgetDecision(
        LlmBudgetBlockReason.NONE,
        LlmBudgetBlockReason.NONE
    );

    /**
     * Why spend from {@code fundingSource} is blocked, and which purse decided it.
     *
     * @param purse the purse that produced the block, or {@code null} when nothing is blocked. For an
     *     unattributable call this is the purse actually consulted, which is not always the one the
     *     caller assumed — so log lines and metric tags must read it here rather than re-deriving it
     *     from the requested funding source.
     */
    public record Block(@Nullable FundingSource purse, LlmBudgetBlockReason reason) {
        static final Block NONE = new Block(null, LlmBudgetBlockReason.NONE);

        public boolean blocked() {
            return reason != LlmBudgetBlockReason.NONE;
        }
    }

    /**
     * The verdict for spend funded by {@code fundingSource}.
     *
     * <p>An unknown funding source (a legacy row whose frozen snapshot predates funding attribution)
     * is judged by BOTH caps — it might be either purse, so it may only proceed when neither is
     * exhausted. Fail-safe, not fail-open: an unattributable call must not be a way around a cap.
     */
    public Block decideFor(@Nullable FundingSource fundingSource) {
        if (fundingSource == null) {
            if (instanceFunded != LlmBudgetBlockReason.NONE) {
                return new Block(FundingSource.INSTANCE, instanceFunded);
            }
            return workspaceFunded != LlmBudgetBlockReason.NONE
                ? new Block(FundingSource.WORKSPACE, workspaceFunded)
                : Block.NONE;
        }
        LlmBudgetBlockReason reason = switch (fundingSource) {
            case INSTANCE -> instanceFunded;
            case WORKSPACE -> workspaceFunded;
        };
        return reason != LlmBudgetBlockReason.NONE ? new Block(fundingSource, reason) : Block.NONE;
    }

    /** The reason spend from {@code fundingSource} is blocked, or {@link LlmBudgetBlockReason#NONE}. */
    public LlmBudgetBlockReason forFunding(@Nullable FundingSource fundingSource) {
        return decideFor(fundingSource).reason();
    }

    public boolean blocks(@Nullable FundingSource fundingSource) {
        return decideFor(fundingSource).blocked();
    }
}
