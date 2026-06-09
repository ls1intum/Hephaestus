import { describe, expect, it } from "vitest";
import { deriveDesignations, shouldSendKey } from "./utils";

describe("deriveDesignations", () => {
	it("returns an empty map when settings are undefined", () => {
		expect(deriveDesignations(undefined).size).toBe(0);
	});

	it("returns an empty map when nothing is bound", () => {
		const map = deriveDesignations({ practiceConfigId: undefined, mentorConfigId: undefined });
		expect(map.size).toBe(0);
	});

	it("marks a practice-only binding", () => {
		const map = deriveDesignations({ practiceConfigId: 1, mentorConfigId: undefined });
		expect(map.get(1)).toBe("practice");
		expect(map.size).toBe(1);
	});

	it("marks a mentor-only binding", () => {
		const map = deriveDesignations({ practiceConfigId: undefined, mentorConfigId: 2 });
		expect(map.get(2)).toBe("mentor");
		expect(map.size).toBe(1);
	});

	it("marks distinct practice and mentor bindings separately", () => {
		const map = deriveDesignations({ practiceConfigId: 1, mentorConfigId: 2 });
		expect(map.get(1)).toBe("practice");
		expect(map.get(2)).toBe("mentor");
		expect(map.size).toBe(2);
	});

	it("collapses a shared binding to 'both'", () => {
		const map = deriveDesignations({ practiceConfigId: 5, mentorConfigId: 5 });
		expect(map.get(5)).toBe("both");
		expect(map.size).toBe(1);
	});
});

describe("shouldSendKey", () => {
	it("never sends in PROXY mode", () => {
		expect(shouldSendKey({ mode: "PROXY", input: "sk-123" })).toBe(false);
	});

	it("sends a non-empty input for direct modes", () => {
		expect(shouldSendKey({ mode: "API_KEY", input: "sk-abc" })).toBe(true);
		expect(shouldSendKey({ mode: "OAUTH", input: "token" })).toBe(true);
	});

	it("does not send a blank/whitespace input (keep current)", () => {
		expect(shouldSendKey({ mode: "API_KEY", input: "" })).toBe(false);
		expect(shouldSendKey({ mode: "API_KEY", input: "   " })).toBe(false);
	});
});
