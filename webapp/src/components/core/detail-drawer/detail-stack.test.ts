import { describe, expect, it } from "vitest";
import { detailStackSchema, encodeDetailStack, parseDetailStack } from "./detail-stack";

const KINDS = ["area", "practice"] as const;
const parse = (detail: unknown) => detailStackSchema(KINDS).parse({ detail }).detail;

describe("detailStackSchema", () => {
	it("coerces the single-value form a hand-written URL produces", () => {
		expect(parse("area:code-review")).toEqual(["area:code-review"]);
	});

	it("keeps an id containing a colon, splitting only at the first one", () => {
		expect(parseDetailStack(parse("practice:scm:pull-request"))).toEqual([
			{ kind: "practice", id: "scm:pull-request" },
		]);
	});

	it("drops kinds the surface cannot render, rather than passing them on", () => {
		expect(parse(["practice:a", "sabotage:b", "area:c"])).toEqual(["practice:a", "area:c"]);
	});

	it.each([[":x"], ["x:"], ["nocolon"], [""]])("drops the malformed entry %j", (value) => {
		expect(parse([value])).toEqual([]);
	});

	it("drops a repeat, because the same thing twice is never a stack", () => {
		expect(parse(["practice:a", "practice:a"])).toEqual(["practice:a"]);
	});

	it("caps depth so a hand-written URL cannot mount an unbounded number of drawers", () => {
		const long = Array.from({ length: 50 }, (_, index) => `practice:p${index}`);
		expect(parse(long)).toHaveLength(4);
	});

	it("reads an absent param as a closed stack", () => {
		expect(parseDetailStack(parse(undefined))).toEqual([]);
	});
});

describe("encodeDetailStack", () => {
	it("writes the wire form the schema reads back", () => {
		const stack = [
			{ kind: "area", id: "code-review" },
			{ kind: "practice", id: "describe-what-and-why" },
		];
		expect(encodeDetailStack(stack)).toEqual([
			"area:code-review",
			"practice:describe-what-and-why",
		]);
		expect(parseDetailStack(parse(encodeDetailStack(stack)))).toEqual(stack);
	});

	it("omits the param entirely when nothing is open, so a closed stack leaves no URL trace", () => {
		expect(encodeDetailStack([])).toBeUndefined();
	});
});
