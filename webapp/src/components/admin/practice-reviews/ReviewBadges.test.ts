import { describe, expect, it } from "vitest";

import { resultOutcomeDef, resultSeverity } from "./ReviewBadges";

describe("result severity", () => {
	it.each(["MET", "NOT_APPLICABLE", "UNDETERMINED"] as const)(
		"does not show severity for %s",
		(outcome) => {
			expect(resultSeverity({ outcome, severity: "CRITICAL" })).toBeUndefined();
		},
	);
	it("shows the severity of a not-met result", () => {
		expect(resultSeverity({ outcome: "NOT_MET", severity: "CRITICAL" })?.label).toBe("Critical");
	});
});

describe("result outcome", () => {
	it("counts the problems of a not met result", () => {
		expect(resultOutcomeDef("NOT_MET", 3).label).toBe("Not met · 3 problems");
		expect(resultOutcomeDef("NOT_MET", 1).label).toBe("Not met · 1 problem");
	});
	it("says the outcome alone where no problem is counted", () => {
		expect(resultOutcomeDef("NOT_MET", undefined).label).toBe("Not met");
		expect(resultOutcomeDef("UNDETERMINED", 0).label).toBe("Could not determine");
	});
});
