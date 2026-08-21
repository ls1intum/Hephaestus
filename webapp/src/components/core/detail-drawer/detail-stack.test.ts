import { describe, expect, it } from "vitest";
import {
	DETAIL_STACK_MAX_DEPTH,
	type DetailStackEntry,
	encodeDetailStack,
	parseDetailStack,
} from "./detail-stack";

const KINDS = ["area", "practice"] as const;

describe("parseDetailStack", () => {
	it("reads kind and id, splitting at the first colon so an id may contain one", () => {
		expect(parseDetailStack(["practice:scm:pull-request"], KINDS)).toEqual([
			{ kind: "practice", id: "scm:pull-request" },
		]);
	});

	it("accepts the single-value form a hand-written URL produces", () => {
		expect(parseDetailStack(["area:code-review"], KINDS)).toEqual([
			{ kind: "area", id: "code-review" },
		]);
	});

	it("drops kinds the surface cannot render, rather than passing them on", () => {
		expect(parseDetailStack(["practice:a", "sabotage:b", "area:c"], KINDS)).toEqual([
			{ kind: "practice", id: "a" },
			{ kind: "area", id: "c" },
		]);
	});

	it.each([[":x"], ["x:"], ["nocolon"], [""]])("drops the malformed entry %j", (value) => {
		expect(parseDetailStack([value], KINDS)).toEqual([]);
	});

	it("drops a repeat, because the same thing twice is never a stack", () => {
		expect(parseDetailStack(["practice:a", "practice:a"], KINDS)).toEqual([
			{ kind: "practice", id: "a" },
		]);
	});

	it("caps depth so a hand-written URL cannot mount an unbounded number of drawers", () => {
		const long = Array.from({ length: 50 }, (_, index) => `practice:p${index}`);
		expect(parseDetailStack(long, KINDS)).toHaveLength(DETAIL_STACK_MAX_DEPTH);
	});

	it("reads an absent param as a closed stack", () => {
		expect(parseDetailStack(undefined, KINDS)).toEqual([]);
	});
});

describe("encodeDetailStack", () => {
	it("round-trips through parseDetailStack", () => {
		const stack: DetailStackEntry[] = [
			{ kind: "area", id: "code-review" },
			{ kind: "practice", id: "describe-what-and-why" },
		];
		expect(parseDetailStack(encodeDetailStack(stack), KINDS)).toEqual(stack);
	});

	it("omits the param entirely when nothing is open, so a closed stack leaves no URL trace", () => {
		expect(encodeDetailStack([])).toBeUndefined();
	});
});
