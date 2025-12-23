import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import { verifyGitHubSignature, verifyGitLabToken } from "@/crypto/verify";

describe("verifyGitHubSignature", () => {
	const secret = "test-secret";
	const payload = Buffer.from('{"test": "data"}');

	it("should verify valid SHA-256 signature", () => {
		// Compute the expected HMAC-SHA256 signature
		const mac = createHmac("sha256", secret);
		mac.update(payload);
		const expectedSignature = `sha256=${mac.digest("hex")}`;

		expect(verifyGitHubSignature(expectedSignature, secret, payload)).toBe(
			true,
		);
	});

	it("should reject invalid SHA-256 signature", () => {
		const invalidSignature = "sha256=invalid1234567890";
		expect(verifyGitHubSignature(invalidSignature, secret, payload)).toBe(
			false,
		);
	});

	it("should verify valid SHA-1 signature", () => {
		const mac = createHmac("sha1", secret);
		mac.update(payload);
		const expectedSignature = `sha1=${mac.digest("hex")}`;

		expect(verifyGitHubSignature(expectedSignature, secret, payload)).toBe(
			true,
		);
	});

	it("should reject empty signature", () => {
		expect(verifyGitHubSignature(null, secret, payload)).toBe(false);
		expect(verifyGitHubSignature("", secret, payload)).toBe(false);
	});

	it("should reject empty secret", () => {
		expect(verifyGitHubSignature("sha256=abc", "", payload)).toBe(false);
	});

	it("should reject unknown algorithm prefix", () => {
		expect(verifyGitHubSignature("md5=abc123", secret, payload)).toBe(false);
	});
});

describe("verifyGitLabToken", () => {
	const secret = "gitlab-secret";

	it("should verify matching token", () => {
		expect(verifyGitLabToken(secret, secret)).toBe(true);
	});

	it("should reject non-matching token", () => {
		expect(verifyGitLabToken("wrong-token", secret)).toBe(false);
	});

	it("should reject empty token", () => {
		expect(verifyGitLabToken(null, secret)).toBe(false);
		expect(verifyGitLabToken("", secret)).toBe(false);
	});

	it("should reject empty secret", () => {
		expect(verifyGitLabToken("token", "")).toBe(false);
	});

	it("should reject different length tokens (timing-safe)", () => {
		expect(verifyGitLabToken("short", "much-longer-secret")).toBe(false);
	});
});
