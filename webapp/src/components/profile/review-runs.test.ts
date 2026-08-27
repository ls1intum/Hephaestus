import { describe, expect, it } from "vitest";
import type { PracticeGroupReviewObservation } from "@/api/types.gen";
import type { FeedbackUsefulness } from "./review-runs";

describe("review-run types", () => {
	it("uses the response values exposed by the server contract", () => {
		const usefulness: FeedbackUsefulness = "HELPFUL";
		const observation = {
			observationId: "00000000-0000-0000-0000-000000000001",
			practiceSlug: "records-decisions",
			practiceName: "Record significant decisions",
			title: "The workspace trade-off is documented",
			presence: "PRESENT",
			assessment: "GOOD",
			feedbackUsefulness: usefulness,
			feedbackResolution: "ADDRESSED",
		} satisfies PracticeGroupReviewObservation;

		expect(observation.feedbackUsefulness).toBe("HELPFUL");
		expect(observation.feedbackResolution).toBe("ADDRESSED");
	});
});
