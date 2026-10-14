import { describe, expect, it } from "vitest";

import { BUDGET_HOLD, isCancellable, jobPhases, jobWait } from "./job-utils";

const NOW = new Date("2026-05-20T12:00:00Z").getTime();
const SOON = new Date("2026-05-20T12:05:00Z");
const EARLIER = new Date("2026-05-20T11:55:00Z");

describe("jobWait", () => {
	it("reports a backoff for a queued job whose next attempt is still ahead", () => {
		expect(jobWait({ status: "QUEUED", availableAt: SOON }, NOW)).toStrictEqual({
			kind: "backoff",
		});
	});

	it("reports nothing for a queued job that is already claimable", () => {
		expect(jobWait({ status: "QUEUED", availableAt: EARLIER }, NOW)).toBeNull();
	});

	it("reports nothing once the job has left the queue, whatever its timestamps say", () => {
		expect(jobWait({ status: "RUNNING", availableAt: SOON }, NOW)).toBeNull();
		expect(jobWait({ status: "COMPLETED", availableAt: SOON }, NOW)).toBeNull();
	});
});

describe("jobPhases", () => {
	const at = (seconds: number) => new Date(NOW + seconds * 1000);
	const finished = {
		status: "COMPLETED",
		attempt: 1,
		availableAt: at(0),
		startedAt: at(12),
		completedAt: at(312),
	} as const;

	it("measures each phase of a finished job between its own boundaries", () => {
		expect(jobPhases(finished)).toStrictEqual({
			wait: "12 seconds",
			execution: "5 minutes",
			retried: false,
		});
	});

	it("keeps an exact zero rather than calling it unknown", () => {
		expect(jobPhases({ ...finished, startedAt: at(0) }).wait).toBe("0 seconds");
	});

	it("keeps a positive subsecond span distinct from an exact zero", () => {
		expect(jobPhases({ ...finished, startedAt: at(0.5) }).wait).toBe("Under 1 s");
	});

	it("names the live phase instead of a figure", () => {
		expect(jobPhases({ ...finished, status: "RUNNING", completedAt: null })).toMatchObject({
			wait: "12 seconds",
			execution: "In progress",
		});
		expect(
			jobPhases({ ...finished, status: "QUEUED", startedAt: null, completedAt: null }),
		).toStrictEqual({ wait: "In progress", execution: "Not started", retried: false });
	});

	it("records nothing that depends on an unreadable or reversed boundary", () => {
		expect(jobPhases({ ...finished, startedAt: new Date("not a date") })).toMatchObject({
			wait: "Not recorded",
			execution: "Not recorded",
		});
		expect(jobPhases({ ...finished, completedAt: at(5) }).execution).toBe("Not recorded");
	});

	it("marks a retried job's wait as this attempt's", () => {
		expect(jobPhases({ ...finished, attempt: 3 })).toMatchObject({
			wait: "12 seconds",
			retried: true,
		});
	});
});

describe("BUDGET_HOLD", () => {
	it("never suggests a held review failed", () => {
		expect(BUDGET_HOLD.label).toBe("Over the AI budget");
		expect(BUDGET_HOLD.detail).toMatch(/waiting, not failed/u);
		expect(BUDGET_HOLD.detail).toMatch(/continues on its own/u);
	});
});

describe("isCancellable", () => {
	it.each([
		["QUEUED", true],
		["RUNNING", true],
		["COMPLETE", false],
		["PARTIAL", false],
		["FAILED", false],
		["CANCELLED", false],
	] as const)("%s is cancellable: %s", (status, cancellable) => {
		expect(isCancellable(status)).toBe(cancellable);
	});
});
