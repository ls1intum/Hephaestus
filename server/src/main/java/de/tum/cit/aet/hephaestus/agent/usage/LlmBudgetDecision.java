package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Whether a workspace may still spend, decided separately for each funding source (#1368).
 *
 * <p>A workspace can run on two purses at once — a shared instance model the host pays for
 * ({@link FundingSource#INSTANCE}) and its own connected provider ({@link FundingSource#WORKSPACE}).
 * Each purse has its own cap, set by whoever pays: the instance admin caps instance-funded spend,
 * the workspace admin caps its own. So "is this workspace blocked?" has no single answer — only
 * "is THIS call blocked?", which depends on who is paying for it.
 *
 * <p>Keeping the two verdicts apart is what makes the ownership story hold: a workspace admin can
 * never loosen the instance's protection (they cannot write anything the instance verdict reads),
 * and the host's exhausted budget cannot pause work the workspace pays for itself.
 */
public record LlmBudgetDecision(LlmBudgetBlockReason instanceFunded, LlmBudgetBlockReason workspaceFunded) {
    public static final LlmBudgetDecision ALLOWED = new LlmBudgetDecision(
        LlmBudgetBlockReason.NONE,
        LlmBudgetBlockReason.NONE
    );

    /**
     * The reason spend from {@code fundingSource} is blocked, or {@link LlmBudgetBlockReason#NONE}.
     *
     * <p>An unknown funding source (a legacy row whose frozen snapshot predates funding attribution)
     * is judged by BOTH caps — it might be either purse, so it may only proceed when neither is
     * exhausted. Fail-safe, not fail-open: an unattributable call must not be a way around a cap.
     */
    public LlmBudgetBlockReason forFunding(FundingSource fundingSource) {
        if (fundingSource == null) {
            return instanceFunded != LlmBudgetBlockReason.NONE ? instanceFunded : workspaceFunded;
        }
        return switch (fundingSource) {
            case INSTANCE -> instanceFunded;
            case WORKSPACE -> workspaceFunded;
        };
    }

    public boolean blocks(FundingSource fundingSource) {
        return forFunding(fundingSource) != LlmBudgetBlockReason.NONE;
    }

    /** True when at least one purse is blocked — for surfaces that report "something is paused". */
    public boolean blocksAnything() {
        return instanceFunded != LlmBudgetBlockReason.NONE || workspaceFunded != LlmBudgetBlockReason.NONE;
    }
}
