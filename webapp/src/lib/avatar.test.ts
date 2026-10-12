import { describe, expect, it } from "vitest";

import { getInitials } from "./avatar";

describe("getInitials", () => {
	it("takes the first letters of the first and last words of a name", () => {
		expect(getInitials("Ada Lovelace")).toBe("AL");
		expect(getInitials("Jane van der Doe")).toBe("JD");
	});

	it("skips words that start with a digit", () => {
		expect(getInitials("Synthetic developer 05")).toBe("SD");
		expect(getInitials(undefined, "synthetic-developer-01")).toBe("SD");
	});

	it("splits a login on hyphens, underscores and dots", () => {
		expect(getInitials(undefined, "jane_doe")).toBe("JD");
		expect(getInitials(undefined, "jane.doe")).toBe("JD");
	});

	it("takes two letters of a single word", () => {
		expect(getInitials("Alice")).toBe("AL");
		expect(getInitials(undefined, "octocat")).toBe("OC");
		expect(getInitials(undefined, "r2d2")).toBe("RD");
	});

	it("falls back to the login when the name has no letters", () => {
		expect(getInitials("42", "alice")).toBe("AL");
	});

	it("keeps something when nothing starts with a letter", () => {
		expect(getInitials(undefined, "123")).toBe("12");
		expect(getInitials()).toBe("?");
	});
});
