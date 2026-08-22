import { describe, expect, it } from "vitest";
import { firstNonBlank, hasText } from "./text";

describe("hasText", () => {
	it("rejects absence and the blank string alike", () => {
		expect(hasText(undefined)).toBe(false);
		expect(hasText(null)).toBe(false);
		expect(hasText("")).toBe(false);
	});

	it("accepts any string with a character in it, including whitespace", () => {
		expect(hasText("a")).toBe(true);
		expect(hasText(" ")).toBe(true);
	});
});

describe("firstNonBlank", () => {
	it("skips past blank and absent sources to the first that carries text", () => {
		expect(firstNonBlank(undefined, "", null, "Ada")).toBe("Ada");
	});

	it("reports undefined when every source is blank, so the caller supplies the fallback", () => {
		expect(firstNonBlank(undefined, "", null)).toBeUndefined();
		expect(firstNonBlank()).toBeUndefined();
	});
});
