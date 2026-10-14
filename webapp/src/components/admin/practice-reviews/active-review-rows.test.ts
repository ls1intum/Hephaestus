import { describe, expect, it } from "vitest";

import type { ReviewSummary } from "@/api/types.gen";

import { reconcileActiveRows } from "./active-review-rows";
import { reviews } from "./fixtures";

function firstReview(): ReviewSummary {
	const [first] = reviews;
	if (first === undefined) {
		throw new Error("The fixtures have no review");
	}
	return first;
}

const base = firstReview();

function review(id: string, status: ReviewSummary["status"], minute: number): ReviewSummary {
	return { ...base, id, status, createdAt: new Date(Date.UTC(2026, 9, 7, 10, minute)) };
}

describe("reconcileActiveRows", () => {
	it("updates a running row in place without re-reading the list", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETE", 20)];
		const polled = [{ ...review("a", "RUNNING", 30), activeJobCount: 3 }];
		const result = reconcileActiveRows(loaded, polled, true);
		expect(result.refetch).toBe(false);
		expect(result.updated.get("a")?.activeJobCount).toBe(3);
	});

	it("re-reads the list once a loaded review stops being active", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETE", 20)];
		expect(reconcileActiveRows(loaded, [], true).refetch).toBe(true);
	});

	it("does not take a truncated poll for a finished review", () => {
		const loaded = [review("a", "RUNNING", 30)];
		expect(reconcileActiveRows(loaded, [review("z", "QUEUED", 40)], false).refetch).toBe(true);
		expect(reconcileActiveRows(loaded, [], false).refetch).toBe(false);
	});

	it("re-reads the list when a new review arrives among the loaded rows", () => {
		const loaded = [review("a", "RUNNING", 30), review("b", "COMPLETE", 20)];
		const polled = [review("a", "RUNNING", 30), review("new", "QUEUED", 45)];
		expect(reconcileActiveRows(loaded, polled, true).refetch).toBe(true);
	});

	it("ignores an active review older than every loaded row", () => {
		const loaded = [review("a", "COMPLETE", 30)];
		expect(reconcileActiveRows(loaded, [review("old", "QUEUED", 5)], true).refetch).toBe(false);
	});
});
