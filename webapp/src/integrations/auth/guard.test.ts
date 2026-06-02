import { describe, expect, it } from "vitest";
import { isAppAdmin, safeReturnTo } from "./guard";

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

		it("rejects raw whitespace that could hide an escape", () => {
			expect(safeReturnTo("/ /evil")).toBe("/");
			expect(safeReturnTo("/foo bar")).toBe("/");
		});

		// decode-then-check: an attacker percent-encodes the dangerous bytes so a naive
		// (encoded) same-origin check passes, then a downstream parser decodes them.
		describe("decode-then-check defeats percent-encoded escapes", () => {
			it.each([
				["encoded protocol-relative //evil", "/%2f%2fevil.com"],
				["encoded protocol-relative (mixed case)", "/%2F%2Fevil.com"],
				["double-encoded protocol-relative", "/%252f%252fevil.com"],
				["encoded tab then path", "/%09/evil"],
				["encoded space then path", "/%20/evil"],
				["encoded leading backslash", "/%5cevil.com"],
				["encoded newline", "/foo%0abar"],
				["encoded NUL", "/foo%00bar"],
			])("%s -> /", (_label, input) => {
				expect(safeReturnTo(input)).toBe("/");
			});

			it("rejects a literal userinfo @ host trick", () => {
				expect(safeReturnTo("/@evil")).toBe("/");
				expect(safeReturnTo("/%40evil")).toBe("/");
			});

			it("preserves the original value for a safe path with legitimately-encoded query bytes", () => {
				// `%26` is an encoded ampersand inside a query value — decoding it is only for the
				// safety check; the returned value must stay encoded so the destination is intact.
				expect(safeReturnTo("/search?q=a%26b")).toBe("/search?q=a%26b");
			});

			it("does not loop forever on a decode bomb / malformed encoding", () => {
				// A lone `%` is malformed (decodeURIComponent throws) — must fall back safely, not hang.
				expect(safeReturnTo("/foo%")).toBe("/foo%");
				expect(safeReturnTo("/%2525252f%2525252fevil")).toBe("/");
			});
		});
	});
});

describe("isAppAdmin", () => {
	it("is true when appRole is APP_ADMIN", () => {
		expect(isAppAdmin({ appRole: "APP_ADMIN", roles: [] })).toBe(true);
	});
	it("is true when the admin role is present even if appRole is not APP_ADMIN", () => {
		expect(isAppAdmin({ appRole: "APP_USER", roles: ["user", "admin"] })).toBe(true);
	});
	it("is false for a plain user", () => {
		expect(isAppAdmin({ appRole: "APP_USER", roles: ["user"] })).toBe(false);
	});
	it.each([null, undefined])("is false for %s", (u) => {
		expect(isAppAdmin(u)).toBe(false);
	});
	it("tolerates a missing roles array", () => {
		expect(isAppAdmin({ appRole: "APP_USER" })).toBe(false);
	});
});
