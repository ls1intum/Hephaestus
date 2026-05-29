import { describe, expect, it } from "vitest";
import { safeReturnTo } from "./guard";

// `safeReturnTo` is the single open-redirect defense for the post-login `?returnTo` param.
// A regression here is a security bug (open redirect / XSS via javascript: URLs), so the
// accept/reject matrix below is exhaustive on the interesting branches.
describe("safeReturnTo", () => {
	describe("accepts same-origin absolute paths", () => {
		it.each([
			"/",
			"/dashboard",
			"/w/acme/overview",
			"/a/b?x=1&y=2",
			"/path#frag",
			"/with-dash_x",
		])("returns %s unchanged", (input) => {
			expect(safeReturnTo(input)).toBe(input);
		});
	});

	describe("falls back to / for unsafe or absent values", () => {
		it.each([
			["empty string", ""],
			["undefined", undefined],
			["protocol-relative //evil", "//evil.com"],
			["protocol-relative ///evil", "///evil.com"],
			["absolute https URL", "https://evil.com"],
			["absolute http URL", "http://evil.com/path"],
			["backslash escape /\\evil", "/\\evil.com"],
			["scheme after slash /javascript:", "/javascript:alert(1)"],
			["scheme after slashes //javascript:", "//javascript:alert(1)"],
			["bare relative (no leading slash)", "dashboard"],
			["javascript scheme", "javascript:alert(1)"],
			["data scheme", "data:text/html,evil"],
		])("%s -> /", (_label, input) => {
			expect(safeReturnTo(input as string | undefined)).toBe("/");
		});

		it("rejects embedded control characters (NUL, newline, tab, CR, DEL)", () => {
			expect(safeReturnTo("/foo\x00bar")).toBe("/");
			expect(safeReturnTo("/foo\nbar")).toBe("/");
			expect(safeReturnTo("/foo\tbar")).toBe("/");
			expect(safeReturnTo("/foo\rbar")).toBe("/");
			expect(safeReturnTo("/foo\x7fbar")).toBe("/");
		});
	});
});
