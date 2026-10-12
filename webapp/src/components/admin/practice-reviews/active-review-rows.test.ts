import { describe, expect, it } from "vitest";

import type { ReviewRunSummary } from "@/api/types.gen";

import { reconcileActiveRows } from "./active-review-rows";
import { reviewRuns } from "./fixtures";

function firstRun(): ReviewRunSummary {
	const [run] = reviewRuns;
	if (run === undefined) {
		throw new Error("The fixtures have no review");
	}
	return run;
}

const base = firstRun();

function review(id: string, status: ReviewRunSummary["status"], minute: number): ReviewRunSummary {
	return { ...base, id, status, createdAt: new Date(Date.UTC(2026, 9, 7, 10, minute)) };
}

describe("reconcileActiveRows", () => {
	it("updates a running row in place without re-reading the list", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETED", 20)];
		const polled = [{ ...review("a", "RUNNING", 30), practicesEvaluated: 3 }];
		const result = reconcileActiveRows(loaded, polled, true);
		expect(result.refetch).toBe(false);
		expect(result.updated.get("a")?.practicesEvaluated).toBe(3);
	});

	it("re-reads the list once a loaded review stops being active", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETED", 20)];
		expect(reconcileActiveRows(loaded, [], true).refetch).toBe(true);
	});

	it("does not take a truncated poll for a finished review", () => {
		const loaded = [review("a", "RUNNING", 30)];
		expect(reconcileActiveRows(loaded, [review("z", "QUEUED", 40)], false).refetch).toBe(true);
		expect(reconcileActiveRows(loaded, [], false).refetch).toBe(false);
	});

	it("re-reads the list when a new review arrives among the loaded rows", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETED", 20)];
		const polled = [review("a", "RUNNING", 30), review("new", "QUEUED", 45)];
		expect(reconcileActiveRows(loaded, polled, true).refetch).toBe(true);
	});

	it("ignores an active review older than every loaded row", () => {
		const loaded = [review("a", "COMPLETED", 30)];
		expect(reconcileActiveRows(loaded, [review("old", "QUEUED", 5)], true).refetch).toBe(false);
	});
});
