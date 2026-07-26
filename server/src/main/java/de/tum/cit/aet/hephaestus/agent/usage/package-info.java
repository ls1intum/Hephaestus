/**
 * Per-workspace LLM usage rollup, the monthly budget caps, and the instance-admin view over both.
 *
 * <h2>Two purses, named on one axis</h2>
 *
 * <p>Every figure in this package's DTOs belongs to exactly one of two purses: {@code instance*} is
 * spend the host pays for on shared models, {@code ownProvider*} is spend the workspace pays for
 * through its own connected provider. They are never added together. The pair is named symmetrically
 * — {@code instanceTotalCostUsd}/{@code ownProviderTotalCostUsd},
 * {@code instanceBudgetVerdict}/{@code ownProviderBudgetVerdict} — so the two halves read as one
 * concept with two owners rather than as two unrelated concepts.
 *
 * <p>No field says "priced": BOTH totals exclude usage whose price is not yet known, so qualifying
 * only one of them would imply a difference that does not exist. {@code unpricedEventCount} states
 * the exclusion once, for both.
 */
package de.tum.cit.aet.hephaestus.agent.usage;
