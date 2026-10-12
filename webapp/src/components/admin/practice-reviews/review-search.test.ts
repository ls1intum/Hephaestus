import { describe, expect, it } from "vitest";

import {
	feedbackQuery,
	feedbackSearchSchema,
	observationsQuery,
	observationsSearchSchema,
	onePracticeQuery,
	runsQuery,
	runsSearchSchema,
} from "./review-search";

describe("practice review search", () => {
	it("canonicalizes invalid URL filters", () => {
		const feedback = feedbackSearchSchema.parse({
			deliveryState: ["DELIVERED", "made-up"],
			location: ["GITLAB", "SLACK", "IN_CHAT", "made-up"],
			from: "not-a-date",
			page: -4,
		});
		const observations = observationsSearchSchema.parse({
			outcome: ["MET", "MET", "unknown"],
			severity: "MAJOR",
			from: "2026-08-10",
			to: "2026-08-01",
		});

		expect(feedback).toMatchObject({ deliveryState: ["DELIVERED"] });
		// A filter the toolbar cannot show would be applied with nothing on screen saying so and no way
		// to clear it short of a full reset, so a channel and `made-up` go while every location the
		// toolbar offers stays.
		expect(feedback.location).toStrictEqual(["GITLAB", "SLACK"]);
		expect(feedback.from).toBeUndefined();
		// The lists have no pages, so a `page` in the URL is dropped.
		expect("page" in feedback).toBe(false);
		expect(observations).toMatchObject({ outcome: ["MET"], severity: ["MAJOR"] });
		expect(observations.to).toBeUndefined();
		expect(feedbackSearchSchema.parse({ to: "2026-07-03" }).to).toBeUndefined();
	});

	it("uses an inclusive day start and exclusive next-day end", () => {
		const search = feedbackSearchSchema.parse({ from: "2026-07-01", to: "2026-07-03" });
		const query = feedbackQuery(search, 25);

		expect(query.from).toBeInstanceOf(Date);
		expect(query.from?.getDate()).toBe(1);
		expect(query.from?.getHours()).toBe(0);
		expect(query.to).toBeInstanceOf(Date);
		expect(query.to?.getDate()).toBe(4);
		expect(query.to?.getHours()).toBe(0);
	});

	it("gives the reviews list the same day window as its sibling lists, alongside the status", () => {
		const search = runsSearchSchema.parse({
			status: ["COMPLETED", "FAILED"],
			from: "2026-07-01",
			to: "2026-07-03",
		});
		const query = runsQuery(search, 25);

		// The same half-open rule the other two lists use, so a date pasted between the three screens
		// selects the same days on each.
		expect(query.from?.getDate()).toBe(1);
		expect(query.from?.getHours()).toBe(0);
		expect(query.to?.getDate()).toBe(4);
		// Both filters travel: the statuses must not be dropped once a range is picked, which is the
		// composition the endpoint applies and the list promises.
		expect(query.status).toStrictEqual(["COMPLETED", "FAILED"]);
	});

	it("sends On hold as the server's onHold beside the statuses a review has", () => {
		const held = runsQuery(runsSearchSchema.parse({ status: ["ON_HOLD"] }), 25);
		expect(held.status).toBeUndefined();
		expect(held.onHold).toBe(true);
		const both = runsQuery(runsSearchSchema.parse({ status: ["FAILED", "ON_HOLD"] }), 25);
		expect(both.status).toStrictEqual(["FAILED"]);
		expect(both.onHold).toBe(true);
		expect(runsQuery(runsSearchSchema.parse({ status: ["FAILED"] }), 25).onHold).toBeUndefined();
	});

	it("sends Waiting as the server's waiting, so the queue's Waiting figure opens what it counts", () => {
		const waiting = runsQuery(runsSearchSchema.parse({ status: ["WAITING"] }), 25);
		expect(waiting.status).toBeUndefined();
		expect(waiting.waiting).toBe(true);
		expect(waiting.onHold).toBeUndefined();
		const queued = runsQuery(runsSearchSchema.parse({ status: ["QUEUED"] }), 25);
		expect(queued.status).toStrictEqual(["QUEUED"]);
		expect(queued.waiting).toBeUndefined();
	});

	it("drops a reviews window that ends before it starts, rather than querying an empty range", () => {
		expect(runsSearchSchema.parse({ from: "2026-08-10", to: "2026-08-01" }).to).toBeUndefined();
		// A bare upper bound is not a window at all, and the endpoint would read it as "everything
		// before this day".
		expect(runsSearchSchema.parse({ to: "2026-08-01" }).to).toBeUndefined();
	});

	it("sends the chosen ordering as the parameter the endpoint names, and nothing when it is the default", () => {
		const chosen = observationsSearchSchema.parse({ order: "ACTIONABILITY" });
		const untouched = observationsSearchSchema.parse({});
		// The word another route already owns: a `sort=name` arriving from anywhere else is not an
		// ordering this list has, so it is dropped rather than sent on to the API.
		const foreign = observationsSearchSchema.parse({ order: "name" });

		expect(observationsQuery(chosen, 25).sort).toBe("ACTIONABILITY");
		expect(observationsQuery(untouched, 25).sort).toBeUndefined();
		expect(observationsQuery(foreign, 25).sort).toBeUndefined();
	});

	it("names the one practice a list narrows to, the tracked one first", () => {
		expect(onePracticeQuery({ practiceSlug: ["y"], selectedPractice: "x" })).toBe("x");
		expect(onePracticeQuery({ practiceSlug: ["y"] })).toBe("y");
		expect(onePracticeQuery({ practiceSlug: ["y", "z"] })).toBeUndefined();
		expect(onePracticeQuery({})).toBeUndefined();
	});

	it("does not send an artifact id without its type", () => {
		const query = feedbackQuery(feedbackSearchSchema.parse({ artifactId: "42" }), 25);
		expect(query.artifactId).toBeUndefined();
	});
});

describe("observation outcome filter", () => {
	it("sends all four outcomes through one filter", () => {
		const outcome = ["MET", "NOT_MET", "NOT_APPLICABLE", "UNDETERMINED"];
		expect(
			observationsQuery(observationsSearchSchema.parse({ outcome }), 25).outcome,
		).toStrictEqual(outcome);
	});
});
