import { describe, expect, it } from "vitest";

import { limitCodeFor, limitWithText, reviewedByProblem } from "./reviewed-by";

describe("limitCodeFor", () => {
	it("derives a stable code from the text, so retyping it does not change how the practice is reviewed", () => {
		expect(limitCodeFor("Runtime behavior not observed")).toBe("RUNTIME_BEHAVIOR_NOT_OBSERVED");
		expect(limitCodeFor("Runtime behavior not observed")).toBe(
			limitCodeFor("Runtime behavior not observed"),
		);
	});

	it("falls back to a hash where the text cannot form a code", () => {
		expect(limitCodeFor("")).toMatch(/^LIMIT_[0-9A-F]{8}$/u);
		expect(limitCodeFor("ü")).toMatch(/^LIMIT_[0-9A-F]{8}$/u);
	});
});

describe("reviewedByProblem", () => {
	const draft = { by: "HEPHAESTUS" as const, humanReason: limitWithText(""), limits: [] };

	it("needs a reason only from people", () => {
		expect(reviewedByProblem(draft)).toBeUndefined();
		expect(reviewedByProblem({ ...draft, by: "NOBODY" })).toBeUndefined();
		expect(reviewedByProblem({ ...draft, by: "PEOPLE" })?.focusId).toBe("practice-human-reason");
		expect(
			reviewedByProblem({
				...draft,
				by: "PEOPLE",
				humanReason: limitWithText("Decided in a meeting."),
			}),
		).toBeUndefined();
	});

	it("refuses a blank or repeated limit while Hephaestus reviews the practice", () => {
		expect(reviewedByProblem({ ...draft, limits: [limitWithText(" ")] })?.focusId).toBe(
			"practice-limit-description-0",
		);
		const limit = limitWithText("Runtime behavior not observed");
		expect(reviewedByProblem({ ...draft, limits: [limit, limit] })?.message).toBe(
			"Describe each limit once.",
		);
		expect(
			reviewedByProblem({ ...draft, by: "NOBODY", limits: [limitWithText(" ")] }),
		).toBeUndefined();
	});
});
