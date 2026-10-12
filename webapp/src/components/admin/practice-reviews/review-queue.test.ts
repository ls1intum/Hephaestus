import { describe, expect, it } from "vitest";

import type { PracticeReviewQueue } from "@/api/types.gen";

import {
	oldestWaitSeconds,
	perHourText,
	queueActive,
	queueItemState,
	queueTotal,
	waitsLong,
} from "./review-queue";

const AS_OF = new Date("2026-10-07T12:00:00Z");

const queue = (patch: Partial<PracticeReviewQueue> = {}): PracticeReviewQueue => ({
	asOf: AS_OF,
	running: 0,
	activeWorkers: 0,
	waiting: 0,
	heldForBudget: 0,
	head: [],
	finishedLastHour: 0,
	finishedLastDay: 0,
	finishedPerHourLastDay: 0,
	...patch,
});

describe("review queue", () => {
	it("counts a budget hold in the queue but polls only for what waits or runs", () => {
		expect(queueTotal(queue({ running: 2, waiting: 5, heldForBudget: 1 }))).toBe(8);
		expect(queueActive(queue())).toBe(false);
		// A budget hold alone waits for the monthly cap: nothing moves, so nothing is re-read.
		expect(queueActive(queue({ heldForBudget: 1 }))).toBe(false);
		expect(queueActive(queue({ waiting: 1 }))).toBe(true);
	});

	it("flags a wait from half an hour, the overview's long-wait mark", () => {
		const since = (minutes: number) => new Date(AS_OF.getTime() - minutes * 60_000);
		expect(oldestWaitSeconds(queue())).toBeUndefined();
		expect(waitsLong(queue({ waiting: 1, oldestWaitingSince: since(29) }))).toBe(false);
		expect(waitsLong(queue({ waiting: 1, oldestWaitingSince: since(30) }))).toBe(true);
		// A wait read on a clock behind the server's is no wait, never a negative one.
		expect(oldestWaitSeconds(queue({ oldestWaitingSince: new Date(AS_OF.getTime() + 5000) }))).toBe(
			0,
		);
	});

	it("tells a hold from a wait", () => {
		const item = {
			id: "r",
			target: { type: "scm.pull_request", title: "Work" },
			since: AS_OF,
		} as const;
		expect(queueItemState({ ...item, status: "RUNNING", heldForBudget: false })).toBe("running");
		expect(queueItemState({ ...item, status: "QUEUED", heldForBudget: false })).toBe("waiting");
		expect(queueItemState({ ...item, status: "QUEUED", heldForBudget: true })).toBe("held");
	});

	it("says a rate in whole numbers from ten and to one decimal below", () => {
		expect(perHourText(12.4)).toBe("12");
		expect(perHourText(4)).toBe("4");
		expect(perHourText(0.125)).toBe("0.1");
		expect(perHourText(2.55)).toBe("2.6");
		// One review in 24 h is 0.04 an hour: it finished, so the rate is not "0".
		expect(perHourText(1 / 24)).toBe("less than 0.1");
		expect(perHourText(0)).toBe("0");
	});
});
