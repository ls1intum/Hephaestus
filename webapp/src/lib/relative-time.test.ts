import { describe, expect, it } from "vitest";
import { formatRelativeTime } from "./relative-time";

describe("formatRelativeTime", () => {
	const now = Date.parse("2026-07-21T12:00:00Z");

	it("returns an empty string for empty or unparseable input", () => {
		expect(formatRelativeTime("", now)).toBe("");
		expect(formatRelativeTime("not-a-date", now)).toBe("");
	});

	it("humanizes a past timestamp", () => {
		expect(formatRelativeTime("2026-07-21T10:00:00Z", now)).toBe("2 hours ago");
		expect(formatRelativeTime("2026-07-20T12:00:00Z", now)).toBe("yesterday");
	});

	it("reports the current instant as now", () => {
		expect(formatRelativeTime("2026-07-21T12:00:00Z", now)).toBe("now");
	});
});
