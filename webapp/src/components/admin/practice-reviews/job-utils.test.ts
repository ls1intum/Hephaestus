import { describe, expect, it } from "vitest";

import { holdReasonCopy, isResultProcessingRetryable, jobPhases, jobWait } from "./job-utils";

const NOW = new Date("2026-05-20T12:00:00Z").getTime();
const SOON = new Date("2026-05-20T12:05:00Z");
const EARLIER = new Date("2026-05-20T11:55:00Z");

describe("jobWait", () => {
	it("reports a hold whenever the server names a reason", () => {
		expect(
			jobWait({ status: "QUEUED", holdReason: "BUDGET", availableAt: SOON }, NOW),
		).toStrictEqual({
			kind: "hold",
			reason: "BUDGET",
		});
	});

	it("still reports a hold once the parked instant has lapsed", () => {
		expect(
			jobWait({ status: "QUEUED", holdReason: "BUDGET", availableAt: EARLIER }, NOW),
		).toStrictEqual({
			kind: "hold",
			reason: "BUDGET",
		});
	});

	it("reports a backoff for a queued run whose next attempt is still ahead", () => {
		expect(
			jobWait({ status: "QUEUED", holdReason: undefined, availableAt: SOON }, NOW),
		).toStrictEqual({
			kind: "backoff",
		});
	});

	it("reports nothing for a queued run that is already claimable", () => {
		expect(
			jobWait({ status: "QUEUED", holdReason: undefined, availableAt: EARLIER }, NOW),
		).toBeNull();
	});

	it("reports nothing once the run has left the queue, whatever its timestamps say", () => {
		expect(
			jobWait({ status: "RUNNING", holdReason: undefined, availableAt: SOON }, NOW),
		).toBeNull();
		expect(
			jobWait({ status: "COMPLETED", holdReason: "BUDGET", availableAt: SOON }, NOW),
		).toBeNull();
	});
});

describe("jobPhases", () => {
	const at = (seconds: number) => new Date(NOW + seconds * 1000);
	const finished = {
		status: "COMPLETED",
		retryCount: 0,
		createdAt: at(0),
		startedAt: at(12),
		executionStartedAt: at(42),
		completedAt: at(342),
	} as const;

	it("measures each phase of a finished run between its own boundaries", () => {
		expect(jobPhases(finished)).toStrictEqual({
			wait: "12 seconds",
			preparation: "30 seconds",
			execution: "5 minutes",
			retried: false,
		});
	});

	it("keeps an exact zero rather than calling it unknown", () => {
		expect(jobPhases({ ...finished, startedAt: at(0), executionStartedAt: at(0) })).toMatchObject({
			wait: "0 seconds",
			preparation: "0 seconds",
		});
	});

	it("keeps a positive subsecond span distinct from an exact zero", () => {
		expect(jobPhases({ ...finished, startedAt: at(0.5) }).wait).toBe("Under 1 s");
	});

	it("names the live phase instead of a figure", () => {
		const running = { ...finished, status: "RUNNING", completedAt: undefined } as const;
		expect(jobPhases({ ...running, executionStartedAt: undefined })).toMatchObject({
			preparation: "In progress",
			execution: "Not started",
		});
		expect(jobPhases(running)).toMatchObject({
			preparation: "30 seconds",
			execution: "In progress",
		});
		expect(
			jobPhases({
				...finished,
				status: "QUEUED",
				startedAt: undefined,
				executionStartedAt: undefined,
				completedAt: undefined,
			}),
		).toStrictEqual({
			wait: "In progress",
			preparation: "Not started",
			execution: "Not started",
			retried: false,
		});
	});

	it("leaves a terminal run without an execution boundary unknown, never zero", () => {
		expect(jobPhases({ ...finished, executionStartedAt: undefined })).toMatchObject({
			wait: "12 seconds",
			preparation: "Not recorded",
			execution: "Not recorded",
		});
	});

	it("records nothing that depends on an unreadable or reversed boundary", () => {
		for (const status of ["RUNNING", "COMPLETED"] as const) {
			for (const executionStartedAt of [new Date("not a date"), at(5)]) {
				expect(jobPhases({ ...finished, status, executionStartedAt })).toMatchObject({
					preparation: "Not recorded",
					execution: "Not recorded",
				});
			}
		}
		expect(jobPhases({ ...finished, completedAt: at(40) }).execution).toBe("Not recorded");
	});

	it("marks a retried run's wait as reaching back to the original request", () => {
		expect(jobPhases({ ...finished, retryCount: 2 })).toMatchObject({
			wait: "12 seconds",
			retried: true,
		});
	});
});

describe("holdReasonCopy", () => {
	it("names the reason it knows in words an operator can act on", () => {
		expect(holdReasonCopy("BUDGET").label).toBe("AI budget pause");
		expect(holdReasonCopy("BUDGET").detail).toMatch(/continues on its own/u);
	});

	it("reads a reason it has never seen as a plain hold, never as its constant", () => {
		expect(holdReasonCopy("MODEL_UNAVAILABLE").label).toBe("On hold");
		expect(holdReasonCopy("MODEL_UNAVAILABLE").detail).toMatch(/continues on its own/u);
	});

	it("never suggests a held run failed", () => {
		for (const reason of ["BUDGET", "MODEL_UNAVAILABLE"]) {
			expect(holdReasonCopy(reason).detail).toMatch(/waiting, not failed/u);
		}
	});
});

describe("result-processing retry", () => {
	it.each([
		["COMPLETED", "FAILED", true],
		["COMPLETED", "PENDING", false],
		["COMPLETED", "DELIVERED", false],
		["RUNNING", "FAILED", false],
		["FAILED", "FAILED", false],
	] as const)("%s / %s is retryable: %s", (status, deliveryStatus, retryable) => {
		expect(isResultProcessingRetryable({ status, deliveryStatus })).toBe(retryable);
	});
});
