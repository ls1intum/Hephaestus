import { describe, expect, it } from "vitest";
import { OBSERVATION_OUTCOME_PRESENTATION, observationOutcome } from "./observation-outcome";

describe("observation outcome contract", () => {
	it("derives the five matrix cells the server outcome vector counts", () => {
		expect(observationOutcome({ presence: "PRESENT", assessment: "GOOD" })).toBe("PRESENT_GOOD");
		expect(observationOutcome({ presence: "ABSENT", assessment: "GOOD" })).toBe("ABSENT_GOOD");
		expect(observationOutcome({ presence: "PRESENT", assessment: "BAD" })).toBe("PRESENT_BAD");
		expect(observationOutcome({ presence: "ABSENT", assessment: "BAD" })).toBe("ABSENT_BAD");
		expect(observationOutcome({ presence: "NOT_APPLICABLE" })).toBe("NOT_APPLICABLE");
	});

	it("keeps an inconclusive verdict apart from work that offered no opportunity", () => {
		// The server sends no assessment for either, but they are different answers: NOT_APPLICABLE means
		// the practice did not apply to this work, INCONCLUSIVE means it did and the reviewer could not
		// tell. Collapsing them would report "not assessed" for a practice that WAS looked at.
		expect(observationOutcome({ presence: "INCONCLUSIVE" })).toBe("INCONCLUSIVE");
		expect(OBSERVATION_OUTCOME_PRESENTATION.INCONCLUSIVE.trendPolarity).toBeNull();
	});

	it("presents every outcome, including the one the outcome vector does not count", () => {
		// The first five mirror the server's OutcomeVector 1:1. INCONCLUSIVE is display-only — it is shown
		// to the learner but contributes no cell to the trend, which is why it carries no polarity.
		expect(Object.keys(OBSERVATION_OUTCOME_PRESENTATION)).toEqual([
			"PRESENT_GOOD",
			"ABSENT_GOOD",
			"PRESENT_BAD",
			"ABSENT_BAD",
			"NOT_APPLICABLE",
			"INCONCLUSIVE",
		]);
	});
});
