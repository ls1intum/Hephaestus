/**
 * LLM usage ledger, pricing, per-workspace rollup, monthly budget caps, and instance reporting.
 *
 * <p>Every figure in this package's DTOs belongs to exactly one of two purses: {@code instance*} is
 * spend the host pays for on shared models, {@code ownProvider*} is spend the workspace pays for
 * through its own connected provider. They are never added together. Both totals exclude usage whose
 * price is not yet known; {@code unpricedEventCount} states that exclusion once, for both. It counts the
 * runs (or, per practice, the reviews) that have such usage, not the ledger rows.
 */
@NullMarked
package de.tum.cit.aet.hephaestus.agent.usage;

import org.jspecify.annotations.NullMarked;
