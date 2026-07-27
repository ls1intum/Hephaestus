import { describe, expect, it } from "vitest";
import { capState } from "./CapMeter";
import { BUDGET_WARN_PERCENT } from "./usage-utils";

/** Both consoles read this, and its threshold also raises the pace warning and turns the bar amber. */
describe("capState", () => {
	it.each<[string, number | undefined, boolean, boolean, "paused" | "near" | null]>([
		["nothing to say well below the threshold", 24.8, false, true, null],
		["still nothing one point short of it", BUDGET_WARN_PERCENT - 1, false, true, null],
		["a warning exactly at the threshold", BUDGET_WARN_PERCENT, false, true, "near"],
		["a warning past it", 92, false, true, "near"],
		["a pause, which outranks the warning", 92, true, true, "paused"],
		["a pause even at zero spend, because a $0 cap pauses at once", 100, true, true, "paused"],
		["nothing without a cap to compare against", undefined, false, true, null],
		// A closed month is measured against *today's* caps, so it can be over one with nothing paused.
		["nothing on a closed month, however far over", 140, false, false, null],
		["nothing on a closed month, even while paused today", 140, true, false, null],
	])("reports %s", (_name, percent, paused, isCurrentMonth, expected) => {
		expect(capState(percent, paused, isCurrentMonth)).toBe(expected);
	});
});
