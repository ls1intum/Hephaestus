import { describe, expect, it } from "vitest";

import { humanizeToken } from "./humanize";

describe("humanizeToken", () => {
	it("reads a screaming-snake constant as a sentence", () => {
		expect(humanizeToken("LOGIN_PROVIDER_CREATED")).toBe("Login provider created");
	});

	it("reads a camelCase field name as a sentence", () => {
		expect(humanizeToken("whatGoodLooksLike")).toBe("What good looks like");
	});

	it("leaves a single word alone but for its capital", () => {
		expect(humanizeToken("criteria")).toBe("Criteria");
	});

	it("returns an empty string unchanged rather than throwing on it", () => {
		expect(humanizeToken("")).toBe("");
	});
});
