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
		expect(OBSERVATION_OUTCOME_PRESENTATION.INCONCLUSIVE.label).not.toBe(
			OBSERVATION_OUTCOME_PRESENTATION.NOT_APPLICABLE.label,
		);
	});

	it("presents every outcome the server can record", () => {
		// The four assessed cells map onto the server's OutcomeVector one for one. Its fifth counter,
		// `notApplicable`, is the union of the last two here: `ObservationOutcome.of` folds
		// INCONCLUSIVE in with NOT_APPLICABLE because neither may move a trend in either direction.
		// The vector is a five-cell collapse of these six, not a five-plus-one split — only surfaces
		// that explain a review, like this one, keep the two silences apart.
		expect(Object.keys(OBSERVATION_OUTCOME_PRESENTATION)).toStrictEqual([
			"PRESENT_GOOD",
			"ABSENT_GOOD",
			"PRESENT_BAD",
			"ABSENT_BAD",
			"NOT_APPLICABLE",
			"INCONCLUSIVE",
		]);
	});
});
