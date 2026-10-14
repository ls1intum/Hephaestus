import { describe, expect, it } from "vitest";

import { type StatusDef, statusValues } from "@/components/common/status-def";

import {
	baselineDef,
	comparisonCanShowFixes,
	METHOD_CHANGED_SENTENCE,
	NOW_MET_DEF,
	PROBLEM_BASELINE_STATES,
	REMOVAL_GUARD_DEFS,
	SCAN_BASELINE_DEFS,
	SCAN_COMPARISON_DEFS,
} from "./scan-baseline-defs";

describe("scan baseline vocabulary", () => {
	it.each([
		["baseline", SCAN_BASELINE_DEFS],
		["comparison", SCAN_COMPARISON_DEFS],
		["removal guard", REMOVAL_GUARD_DEFS],
	])("gives every %s value its own icon", (_name, defs: Record<string, StatusDef>) => {
		const icons = Object.values(defs).map((def) => def.icon);
		expect(new Set(icons).size).toBe(icons.length);
	});

	it("names the four problem comparisons in the order a filter offers them", () => {
		expect(statusValues(SCAN_BASELINE_DEFS)).toStrictEqual([...PROBLEM_BASELINE_STATES]);
		expect(PROBLEM_BASELINE_STATES.map((state) => SCAN_BASELINE_DEFS[state].label)).toStrictEqual([
			"New",
			"Unchanged",
			"Updated",
			"Absent",
		]);
	});

	it("reads a proved repair as Now met and any other absence as Absent", () => {
		expect(baselineDef("ABSENT", "REPAIRED")).toBe(NOW_MET_DEF);
		expect(baselineDef("ABSENT", "REPAIRED").label).toBe("Now met");
		expect(baselineDef("ABSENT", null).label).toBe("Absent");
		expect(baselineDef("UNCHANGED", "REPAIRED").label).toBe("Unchanged");
	});

	it("says a method change cannot show fixes, in the exact words", () => {
		expect(SCAN_COMPARISON_DEFS.METHOD_CHANGED.description).toBe(
			"The review method changed. This scan cannot show which problems were fixed.",
		);
		expect(METHOD_CHANGED_SENTENCE).toBe(SCAN_COMPARISON_DEFS.METHOD_CHANGED.description);
		expect(comparisonCanShowFixes("METHOD_CHANGED")).toBe(false);
		expect(comparisonCanShowFixes("STANDARD_CHANGED")).toBe(false);
		expect(comparisonCanShowFixes("INCOMPLETE")).toBe(false);
		expect(comparisonCanShowFixes("FIRST_SCAN")).toBe(false);
		expect(comparisonCanShowFixes("COMPARABLE")).toBe(true);
	});
});
