package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Why one purse's cap refuses further LLM spend — the per-purse component of
 * {@link LlmBudgetDecision}, shared by every enforcement gate so each applies the same verdict while
 * picking its own wording. Neither block is reachable for an uncapped purse.
 */
public enum LlmBudgetBlockReason {
    NONE,
    EXHAUSTED,
    UNPRICED_USAGE_BLOCKED,
}
