import { describe, expect, it } from "vitest";

import { jobPhases } from "./job-utils";

const NOW = new Date("2026-05-20T12:00:00Z").getTime();

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
