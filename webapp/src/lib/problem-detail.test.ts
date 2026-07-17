import { describe, expect, it } from "vitest";
import { isStepUpRequired, problemDetailOf } from "./problem-detail";

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

describe("isStepUpRequired", () => {
	it("detects the server's step-up challenge", () => {
		expect(isStepUpRequired({ status: 403, code: "step_up_required", maxAgeSeconds: 300 })).toBe(
			true,
		);
	});

	it("ignores other coded problems", () => {
		expect(isStepUpRequired({ status: 409, code: "last_admin" })).toBe(false);
	});

	it("is safe on non-objects", () => {
		expect(isStepUpRequired(null)).toBe(false);
		expect(isStepUpRequired(undefined)).toBe(false);
		expect(isStepUpRequired("step_up_required")).toBe(false);
		expect(isStepUpRequired(new Error("Forbidden"))).toBe(false);
	});
});
