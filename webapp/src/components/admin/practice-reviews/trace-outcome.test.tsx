import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { LINEAGE_STATE_CASES } from "./lineage-state-fixtures";
import { ReviewLineageGraph } from "./ReviewLineageGraph";

/**
 * The trace reads why a completed review recorded what it did from the lineage itself, so a review
 * that assessed nothing never reads as one that looked and found nothing.
 */

afterEach(cleanup);

/** A completed review's case, with its lineage once without and once with the outcome named. */
function caseOf(key: string) {
	const entry = Object.values(LINEAGE_STATE_CASES)
		.flat()
		.find((candidate) => candidate.key === key);
	const review = entry?.lineage.review;
	const outcome = entry?.reviewOutcome;
	if (entry === undefined || review === undefined || outcome === undefined) {
		throw new Error(`The fixtures have no completed review "${key}"`);
	}
	return {
		lineage: entry.lineage,
		outcome,
		named: { ...entry.lineage, review: { ...review, outcome } },
	};
}

describe("the trace's verdict on a completed review", () => {
	it.each(["coalesced", "insufficient-evidence"] as const)(
		"tells a %s review from the lineage alone as it does from the review's own read",
		(key) => {
			const { lineage, outcome, named } = caseOf(key);
			const fromJob = render(
				<ReviewLineageGraph
					lineage={{ status: "ready", lineage }}
					here={{ kind: "review" }}
					reviewOutcome={outcome}
					onOpen={() => undefined}
				/>,
			).container.textContent;
			cleanup();
			const fromLineage = render(
				<ReviewLineageGraph
					lineage={{ status: "ready", lineage: named }}
					here={{ kind: "review" }}
					onOpen={() => undefined}
				/>,
			).container.textContent;
			cleanup();
			const unknown = render(
				<ReviewLineageGraph
					lineage={{ status: "ready", lineage }}
					here={{ kind: "review" }}
					onOpen={() => undefined}
				/>,
			).container.textContent;

			expect(fromLineage).toBe(fromJob);
			expect(fromLineage).not.toBe(unknown);
			expect(screen.queryByText(/assessed nothing/u)).toBeNull();
		},
	);
});
