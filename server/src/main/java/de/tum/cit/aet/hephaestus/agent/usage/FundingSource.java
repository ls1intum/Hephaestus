package de.tum.cit.aet.hephaestus.agent.usage;

/** Whose credential paid for a unit of LLM usage, and so which of the two caps it counts against. */
public enum FundingSource {
    INSTANCE,
    WORKSPACE,
}
