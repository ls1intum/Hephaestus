import { describe, expect, it } from "vitest";

import {
	knownReasonSentence,
	withheldSentence,
} from "@/components/admin/practice-scans/scan-display";

import { reviewReasonSentence } from "./review-status-defs";

describe("legacy withholding reasons", () => {
	it("describes retained ambiguity without selecting a result", () => {
		expect(reviewReasonSentence("LEGACY_AMBIGUOUS_RESULTS")).toBe(
			"This review keeps several historical results for the same practice and work without choosing one as its answer.",
		);
		expect(withheldSentence("LEGACY_AMBIGUOUS_RESULTS")).toBe(
			reviewReasonSentence("LEGACY_AMBIGUOUS_RESULTS"),
		);
	});
	it("describes an unbound historical reuse through the same registry", () => {
		expect(withheldSentence("LEGACY_REUSE_UNBOUND")).toBe(
			"This review referred to an earlier result that could not be identified exactly.",
		);
	});
	it("keeps the generic fallback for an unknown reason", () => {
		expect(knownReasonSentence("UNKNOWN_REASON")).toBeUndefined();
		expect(withheldSentence("UNKNOWN_REASON")).toBe(
			"Withheld for a recorded reason that this page cannot describe yet.",
		);
	});
});
