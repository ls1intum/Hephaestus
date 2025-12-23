import { describe, expect, it } from "vitest";
import { buildDedupeId } from "@/utils/dedupe";

describe("buildDedupeId", () => {
	it("should include the prefix", () => {
		const id = buildDedupeId("github", new Uint8Array([1, 2, 3]));
		expect(id.startsWith("github-")).toBe(true);
	});

	it("should be deterministic for the same payload", () => {
		const body = new Uint8Array([10, 20, 30, 40]);
		const id1 = buildDedupeId("gitlab", body, "event");
		const id2 = buildDedupeId("gitlab", body, "event");
		expect(id1).toBe(id2);
	});

	it("should change when the extra input changes", () => {
		const body = new Uint8Array([5, 6, 7]);
		const id1 = buildDedupeId("github", body, "push");
		const id2 = buildDedupeId("github", body, "pull_request");
		expect(id1).not.toBe(id2);
	});
});
