import { describe, expect, it } from "vitest";

import {
	feedbackQuery,
	feedbackSearchSchema,
	onePracticeQuery,
	problemsQuery,
	problemsSearchSchema,
	resultsQuery,
	resultsSearchSchema,
	reviewsQuery,
	reviewsSearchSchema,
} from "./review-search";

describe("practice review search", () => {
	it("canonicalizes invalid URL filters", () => {
		const feedback = feedbackSearchSchema.parse({
			state: ["DELIVERED", "made-up"],
			destination: ["ON_WORK", "IN_CHAT"],
			location: "made-up",
			from: "not-a-date",
			page: -4,
		});
		const results = resultsSearchSchema.parse({
			outcome: ["MET", "MET", "unknown"],
			severity: "MAJOR",
			from: "2026-08-10",
			to: "2026-08-01",
		});

		// A filter the toolbar cannot show would be applied with nothing on screen saying so and no way
		// to clear it short of a full reset, so every value the toolbar does not offer goes.
		expect(feedback).toMatchObject({ state: ["DELIVERED"], destination: ["ON_WORK"] });
		expect(feedback.location).toBeUndefined();
		expect(feedback.from).toBeUndefined();
		// The lists have no pages, so a `page` in the URL is dropped.
		expect("page" in feedback).toBe(false);
		expect(results).toMatchObject({ outcome: ["MET"], severity: ["MAJOR"] });
		expect(results.to).toBeUndefined();
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
		const search = reviewsSearchSchema.parse({
			status: ["COMPLETE", "FAILED"],
			from: "2026-07-01",
			to: "2026-07-03",
		});
		const query = reviewsQuery(search, 25);

		// The same half-open rule the other lists use, so a date pasted between the screens selects the
		// same days on each.
		expect(query.from?.getDate()).toBe(1);
		expect(query.from?.getHours()).toBe(0);
		expect(query.to?.getDate()).toBe(4);
		// Both filters travel: the statuses must not be dropped once a range is picked.
		expect(query.status).toStrictEqual(["COMPLETE", "FAILED"]);
	});

	it("drops a status a review cannot have", () => {
		expect(reviewsSearchSchema.parse({ status: ["ON_HOLD", "FAILED"] }).status).toStrictEqual([
			"FAILED",
		]);
	});

	it("drops a reviews window that ends before it starts, rather than querying an empty range", () => {
		expect(reviewsSearchSchema.parse({ from: "2026-08-10", to: "2026-08-01" }).to).toBeUndefined();
		// A bare upper bound is not a window at all, and the endpoint would read it as "everything
		// before this day".
		expect(reviewsSearchSchema.parse({ to: "2026-08-01" }).to).toBeUndefined();
	});

	it("sends the chosen ordering as the parameter the endpoint names, and nothing when it is the default", () => {
		const chosen = resultsSearchSchema.parse({ order: "ACTIONABILITY" });
		const untouched = resultsSearchSchema.parse({});
		// The word another route already owns: a `sort=name` arriving from anywhere else is not an
		// ordering this list has, so it is dropped rather than sent on to the API.
		const foreign = resultsSearchSchema.parse({ order: "name" });

		expect(resultsQuery(chosen, 25).sort).toBe("ACTIONABILITY");
		expect(resultsQuery(untouched, 25).sort).toBeUndefined();
		expect(resultsQuery(foreign, 25).sort).toBeUndefined();
	});

	it("names the one practice a list narrows to, the tracked one first", () => {
		expect(onePracticeQuery({ practiceSlug: ["y"], selectedPractice: "x" })).toBe("x");
		expect(onePracticeQuery({ practiceSlug: ["y"] })).toBe("y");
		expect(onePracticeQuery({ practiceSlug: ["y", "z"] })).toBeUndefined();
		expect(onePracticeQuery({})).toBeUndefined();
	});

	it("reads only a valid item id and UUID record ids", () => {
		const search = problemsSearchSchema.parse({
			itemId: "0",
			developerId: "abc",
			resultId: "not-a-uuid",
			problemId: "0198f5a2-7c4e-7a10-8f3b-2d6c1e9a4b70",
		});

		expect(search.itemId).toBeUndefined();
		expect(search.developerId).toBeUndefined();
		expect(search.resultId).toBeUndefined();
		expect(search.problemId).toBe("0198f5a2-7c4e-7a10-8f3b-2d6c1e9a4b70");
	});

	it("narrows the problems list to one result and its item", () => {
		const resultId = "0198f5a2-7c4e-7a10-8f3b-2d6c1e9a4b70";
		const query = problemsQuery(
			problemsSearchSchema.parse({ resultId, itemId: "42", severity: ["CRITICAL"] }),
			25,
		);

		expect(query).toMatchObject({ resultId, itemId: "42", severity: ["CRITICAL"], size: 25 });
	});
});

describe("result outcome filter", () => {
	it("sends all four outcomes through one filter", () => {
		const outcome = ["MET", "NOT_MET", "DOES_NOT_APPLY", "COULD_NOT_DETERMINE"];
		expect(resultsQuery(resultsSearchSchema.parse({ outcome }), 25).outcome).toStrictEqual(outcome);
	});
});
