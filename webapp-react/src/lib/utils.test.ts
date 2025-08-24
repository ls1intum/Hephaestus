import { describe, expect, it } from "vitest";
import { cn, sanitizeText } from "./utils";

describe("utils", () => {
	it("cn merges class names and deduplicates conflicts", () => {
		expect(cn("p-2", "p-2", "text-sm")).toContain("p-2");
		expect(cn("text-sm", false && "text-lg")).toContain("text-sm");
	});

	it("sanitizeText removes special function call marker", () => {
		const input = "Hello <has_function_call>world";
		expect(sanitizeText(input)).toBe("Hello world");
	});
});
