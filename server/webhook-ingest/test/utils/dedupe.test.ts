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

	describe("collision resistance", () => {
		it("should produce different hashes for similar payloads", () => {
			const body1 = new Uint8Array([1, 2, 3, 4]);
			const body2 = new Uint8Array([1, 2, 3, 5]); // One byte different

			const id1 = buildDedupeId("github", body1, "push");
			const id2 = buildDedupeId("github", body2, "push");

			expect(id1).not.toBe(id2);
		});

		it("should produce different hashes for different prefixes", () => {
			const body = new Uint8Array([1, 2, 3]);
			const id1 = buildDedupeId("github", body, "push");
			const id2 = buildDedupeId("gitlab", body, "push");

			expect(id1).not.toBe(id2);
		});

		it("should handle empty payload", () => {
			const body = new Uint8Array([]);
			const id = buildDedupeId("github", body, "push");

			expect(id.startsWith("github-")).toBe(true);
			expect(id.length).toBeGreaterThan(10);
		});

		it("should handle large payloads", () => {
			const body = new Uint8Array(1024 * 1024); // 1MB
			for (let i = 0; i < body.length; i++) {
				body[i] = i % 256;
			}

			const id = buildDedupeId("github", body, "push");

			expect(id.startsWith("github-")).toBe(true);
			// Hash should be fixed length regardless of input size
			expect(id.length).toBeLessThan(100);
		});

		it("should handle undefined extra", () => {
			const body = new Uint8Array([1, 2, 3]);
			const id = buildDedupeId("github", body);

			expect(id.startsWith("github-")).toBe(true);
		});

		it("should handle empty string extra", () => {
			const body = new Uint8Array([1, 2, 3]);
			const id = buildDedupeId("github", body, "");

			expect(id.startsWith("github-")).toBe(true);
		});

		it("should produce consistent 32-char hash (128 bits)", () => {
			const body = new Uint8Array([1, 2, 3, 4, 5]);
			const id = buildDedupeId("github", body, "push");

			// Format: prefix-hash where hash is 32 hex chars
			const hash = id.replace("github-", "");
			expect(hash).toMatch(/^[a-f0-9]{32}$/);
		});
	});
});
