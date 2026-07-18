import { describe, expect, it } from "vitest";
import { problemDetailOf, problemStatusOf } from "./problem-detail";

// `problemDetailOf` turns whatever the generated client throws into a human-readable string
// for toasts/inline errors. Precedence (detail -> title -> legacy error -> message) is the
// contract the UI relies on, so it must not silently drift.
describe("problemDetailOf", () => {
	it("prefers RFC 9457 `detail` over everything else", () => {
		expect(
			problemDetailOf({
				type: "about:blank",
				title: "Bad Request",
				status: 400,
				detail: "Issuer is not reachable",
				error: "legacy error",
				message: "some message",
			}),
		).toBe("Issuer is not reachable");
	});

	it("falls back to `title` when `detail` is absent", () => {
		expect(problemDetailOf({ title: "Bad Request", error: "legacy", message: "msg" })).toBe(
			"Bad Request",
		);
	});

	it("falls back to the legacy `{ error }` shape when title/detail are absent", () => {
		expect(problemDetailOf({ error: "Could not validate", message: "msg" })).toBe(
			"Could not validate",
		);
	});

	it("falls back to `message` last among object keys", () => {
		expect(problemDetailOf({ message: "boom" })).toBe("boom");
	});

	it("returns a plain string error as-is", () => {
		expect(problemDetailOf("network down")).toBe("network down");
	});

	it("reads Error.message when the thrown value is an Error", () => {
		expect(problemDetailOf(new Error("kaboom"))).toBe("kaboom");
	});

	it("ignores blank/whitespace-only string fields and continues the precedence chain", () => {
		expect(problemDetailOf({ detail: "   ", title: "Real Title" })).toBe("Real Title");
	});

	it("falls back to a generic message for unhandled shapes", () => {
		expect(problemDetailOf(null)).toBe("An unexpected error occurred. Please try again.");
		expect(problemDetailOf(undefined)).toBe("An unexpected error occurred. Please try again.");
		expect(problemDetailOf({ status: 500 })).toBe(
			"An unexpected error occurred. Please try again.",
		);
		expect(problemDetailOf(42)).toBe("An unexpected error occurred. Please try again.");
	});
});

// `problemStatusOf` decides whether the UI offers a way out at all — a retryable 503 vs a 403 that no
// button can fix. `undefined` is meaningful (no HTTP answer) and must never be coerced to a number.
describe("problemStatusOf", () => {
	it("reads `status` from the RFC 9457 body the client throws", () => {
		expect(problemStatusOf({ type: "about:blank", status: 403, detail: "Forbidden" })).toBe(403);
	});

	it("falls back to `response.status` for shapes carrying the raw Response", () => {
		expect(problemStatusOf({ response: { status: 503 } })).toBe(503);
	});

	it("prefers the body status over the response status", () => {
		expect(problemStatusOf({ status: 409, response: { status: 200 } })).toBe(409);
	});

	it("returns undefined when the request never got an HTTP answer", () => {
		// A network failure is not a status the server chose; conflating it with one would let the UI
		// claim the server said something it never said.
		expect(problemStatusOf(new TypeError("Failed to fetch"))).toBeUndefined();
		expect(problemStatusOf(null)).toBeUndefined();
		expect(problemStatusOf(undefined)).toBeUndefined();
		expect(problemStatusOf("network down")).toBeUndefined();
	});

	it("ignores non-integer status values rather than passing them on", () => {
		expect(problemStatusOf({ status: "403" })).toBeUndefined();
		expect(problemStatusOf({ status: Number.NaN })).toBeUndefined();
		expect(problemStatusOf({ status: 403.5 })).toBeUndefined();
	});
});
