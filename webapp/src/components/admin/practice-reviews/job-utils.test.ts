import { describe, expect, it } from "vitest";

import { firstStartedAt, jobPhases } from "./job-utils";

const NOW = new Date("2026-05-20T12:00:00Z").getTime();
const at = (seconds: number) => new Date(NOW + seconds * 1000);

describe("jobPhases", () => {
	const finished = {
		status: "COMPLETED",
		attempt: 0,
		availableAt: at(0),
		startedAt: at(12),
		completedAt: at(312),
	} as const;

	it("measures each phase of a finished job between its own boundaries", () => {
		expect(jobPhases(finished)).toStrictEqual({
			wait: "12 seconds",
			execution: "5 minutes",
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
		).toStrictEqual({ wait: "In progress", execution: "Not started" });
	});

	it("records nothing that depends on an unreadable or reversed boundary", () => {
		expect(jobPhases({ ...finished, startedAt: new Date("not a date") })).toMatchObject({
			wait: "Not recorded",
			execution: "Not recorded",
		});
		expect(jobPhases({ ...finished, completedAt: at(5) }).execution).toBe("Not recorded");
	});
});

describe("firstStartedAt", () => {
	it("keeps the earliest start while every job is on its first attempt", () => {
		expect(
			firstStartedAt([
				{ attempt: 0, startedAt: at(70) },
				{ attempt: 0, startedAt: at(40) },
				{ attempt: 0, startedAt: null },
			]),
		).toStrictEqual(at(40));
	});

	// Attempt 1 is already the second attempt, and its job no longer holds the first one's start.
	it("knows no first start once a job continued, even beside an older start", () => {
		expect(firstStartedAt([{ attempt: 1, startedAt: at(894) }])).toBeUndefined();
		expect(
			firstStartedAt([
				{ attempt: 0, startedAt: at(40) },
				{ attempt: 1, startedAt: at(894) },
			]),
		).toBeUndefined();
	});

	it("knows no start where no job has started or none is kept", () => {
		expect(firstStartedAt([{ attempt: 0, startedAt: null }])).toBeUndefined();
		expect(firstStartedAt([])).toBeUndefined();
	});
});
