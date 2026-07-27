import { describe, expect, it } from "vitest";
import {
	formatCapUsd as jobUtilsFormatCapUsd,
	formatCostUsd as jobUtilsFormatCostUsd,
	formatRateUsd as jobUtilsFormatRateUsd,
} from "@/components/admin/ai/job-utils";
import { formatCapUsd, formatCostUsd, formatRateUsd } from "./money";

describe("formatCostUsd", () => {
	it.each([
		[0, "$0"],
		[0.004, "<$0.01"],
		[0.005, "$0.01"],
		[43, "$43.00"],
		[undefined, "—"],
	])("renders %s as %s", (value, expected) => {
		expect(formatCostUsd(value)).toBe(expected);
	});
});

describe("formatCapUsd", () => {
	it("drops the cents a round cap does not have, and keeps the ones it does", () => {
		expect(formatCapUsd(50)).toBe("$50");
		expect(formatCapUsd(49.5)).toBe("$49.50");
	});
});

describe("formatRateUsd", () => {
	it("keeps the digits the provider published rather than clamping to cents", () => {
		// The distinction this module exists for: a rate this small is a real published price, and
		// `formatCostUsd` would render it "<$0.01" — which is not a number an admin can check.
		expect(formatRateUsd(0.075)).toBe("$0.075");
		expect(formatRateUsd(0.003)).toBe("$0.003");
		expect(formatCostUsd(0.003)).toBe("<$0.01");
	});
});

/**
 * Scaffolding, and it should not outlive this branch.
 *
 * `components/admin/ai/job-utils.tsx` still declares its own copy of these three, and every AI
 * surface imports that copy. The move to `lib/` was made from `lib/llm-pricing.ts` outward — that
 * module composing a price label out of a `components/` formatter is the dependency inversion this
 * file's subject exists to remove — but `job-utils.tsx` was not in scope to edit, so for now two
 * declarations of one policy are live.
 *
 * Delete this block the moment `job-utils.tsx` becomes
 * `export { formatCapUsd, formatCostUsd, formatRateUsd } from "@/lib/money";` — until then it is the
 * only thing that would notice one copy's rounding being changed and the other's left alone.
 */
describe("the surviving copy in job-utils", () => {
	it.each([
		undefined,
		0,
		0.003,
		0.005,
		0.075,
		43,
		49.5,
		50,
		1234.5,
	])("agrees with this module on %s", (value) => {
		expect(jobUtilsFormatCostUsd(value)).toBe(formatCostUsd(value));
		expect(jobUtilsFormatCapUsd(value)).toBe(formatCapUsd(value));
		expect(jobUtilsFormatRateUsd(value)).toBe(formatRateUsd(value));
	});
});
