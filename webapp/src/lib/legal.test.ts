import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	isSafeLegalHref,
	isSafeLegalImageSrc,
	isValidLegalProfile,
	resolveLegalContent,
} from "./legal";

const TUMAET_PROFILE_MARKERS = [
	"Technical University of Munich",
	"85748 Garching",
	"Prof. Dr. Stephan Krusche",
	"ls1.admin@in.tum.de",
];

describe("isValidLegalProfile", () => {
	it("accepts lowercase alphanumerics, dashes, underscores", () => {
		expect(isValidLegalProfile("tumaet")).toBe(true);
		expect(isValidLegalProfile("my-fork_01")).toBe(true);
		expect(isValidLegalProfile("a")).toBe(true);
	});

	it("rejects path-traversal, whitespace, uppercase, and exotic characters", () => {
		expect(isValidLegalProfile("")).toBe(false);
		expect(isValidLegalProfile("..")).toBe(false);
		expect(isValidLegalProfile("../admin")).toBe(false);
		expect(isValidLegalProfile("tumaet/../admin")).toBe(false);
		expect(isValidLegalProfile("a b")).toBe(false);
		expect(isValidLegalProfile("-tumaet")).toBe(false);
		expect(isValidLegalProfile("TUMAET")).toBe(false);
		expect(isValidLegalProfile("a".repeat(33))).toBe(false);
	});
});

describe("isSafeLegalHref / isSafeLegalImageSrc", () => {
	it("allows http(s), mailto, tel, fragment, and absolute paths", () => {
		expect(isSafeLegalHref("https://tum.de")).toBe(true);
		expect(isSafeLegalHref("http://example.org")).toBe(true);
		expect(isSafeLegalHref("mailto:dpo@tum.de")).toBe(true);
		expect(isSafeLegalHref("tel:+4989")).toBe(true);
		expect(isSafeLegalHref("#section")).toBe(true);
		expect(isSafeLegalHref("/privacy")).toBe(true);
	});

	it("rejects javascript:, data:, vbscript:, and unknown schemes", () => {
		expect(isSafeLegalHref("javascript:alert(1)")).toBe(false);
		expect(isSafeLegalHref(" javascript:alert(1)")).toBe(false);
		expect(isSafeLegalHref("data:text/html,<script>alert(1)</script>")).toBe(false);
		expect(isSafeLegalHref("vbscript:msgbox(1)")).toBe(false);
		expect(isSafeLegalHref("file:///etc/passwd")).toBe(false);
		expect(isSafeLegalHref(null)).toBe(false);
		expect(isSafeLegalHref(undefined)).toBe(false);
	});

	it("images must be http(s) or an absolute path; data-URIs are rejected", () => {
		expect(isSafeLegalImageSrc("https://tum.de/logo.png")).toBe(true);
		expect(isSafeLegalImageSrc("/logo.png")).toBe(true);
		expect(isSafeLegalImageSrc("data:image/png;base64,AAA")).toBe(false);
		expect(isSafeLegalImageSrc("javascript:alert(1)")).toBe(false);
	});
});

describe("resolveLegalContent", () => {
	const originalFetch = globalThis.fetch;

	beforeEach(() => {
		globalThis.fetch = vi.fn();
	});

	afterEach(() => {
		globalThis.fetch = originalFetch;
	});

	function mockResponses(
		urlMatcher: (url: string) => { status: number; body?: string; contentType?: string },
	) {
		(globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(
			async (input: RequestInfo | URL) => {
				const url = typeof input === "string" ? input : input.toString();
				const { status, body = "", contentType = "text/markdown" } = urlMatcher(url);
				return {
					ok: status >= 200 && status < 300,
					status,
					headers: {
						get: (name: string) => (name.toLowerCase() === "content-type" ? contentType : null),
					},
					text: async () => body,
				} as unknown as Response;
			},
		);
	}

	it("prefers the override when present", async () => {
		mockResponses((url) => {
			if (url === "/legal-overrides/privacy.md") return { status: 200, body: "# override privacy" };
			return { status: 404 };
		});
		const resolved = await resolveLegalContent("privacy", { profile: "tumaet" });
		expect(resolved.source).toBe("override");
		expect(resolved.markdown).toContain("override privacy");
	});

	it("falls through to the profile when no override is mounted", async () => {
		mockResponses((url) => {
			if (url.startsWith("/legal-overrides/")) return { status: 404 };
			if (url === "/legal/profiles/tumaet/privacy.md")
				return { status: 200, body: "# profile privacy" };
			return { status: 404 };
		});
		const resolved = await resolveLegalContent("privacy", { profile: "tumaet" });
		expect(resolved.source).toBe("profile");
	});

	it("falls through to disclaimer when the profile has no file", async () => {
		mockResponses((url) => {
			if (url.startsWith("/legal-overrides/")) return { status: 404 };
			if (url.startsWith("/legal/profiles/")) return { status: 404 };
			if (url === "/legal/_disclaimer/imprint.md") return { status: 200, body: "# fallback" };
			return { status: 404 };
		});
		const resolved = await resolveLegalContent("imprint", { profile: "unknown-profile" });
		expect(resolved.source).toBe("disclaimer");
	});

	it("rejects SPA-fallback HTML responses so the cascade continues", async () => {
		mockResponses((url) => {
			if (url === "/legal-overrides/privacy.md")
				return { status: 200, body: "<!doctype html><html></html>", contentType: "text/html" };
			if (url === "/legal/profiles/tumaet/privacy.md")
				return { status: 200, body: "# real tumaet privacy" };
			return { status: 404 };
		});
		const resolved = await resolveLegalContent("privacy", { profile: "tumaet" });
		expect(resolved.source).toBe("profile");
	});

	it("re-throws AbortError so teardown is distinguishable from network failure", async () => {
		(globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(async () => {
			throw new DOMException("aborted", "AbortError");
		});
		await expect(resolveLegalContent("privacy", { profile: "tumaet" })).rejects.toThrow(/aborted/);
	});

	it("invalid profile values fall through to the disclaimer without constructing profile URLs", async () => {
		const urls: string[] = [];
		mockResponses((url) => {
			urls.push(url);
			if (url.startsWith("/legal-overrides/")) return { status: 404 };
			if (url === "/legal/_disclaimer/privacy.md") return { status: 200, body: "# fallback" };
			return { status: 404 };
		});
		const resolved = await resolveLegalContent("privacy", { profile: "../etc" });
		expect(resolved.source).toBe("disclaimer");
		expect(resolved.profile).toBe("");
		expect(urls.some((u) => u.startsWith("/legal/profiles/"))).toBe(false);
	});

	it("non-tumaet profiles must not leak TUM canonical identity markers", async () => {
		const collected: string[] = [];
		mockResponses((url) => {
			collected.push(url);
			if (url.startsWith("/legal-overrides/")) return { status: 404 };
			if (url.startsWith("/legal/profiles/"))
				return { status: 200, body: "<!doctype html>", contentType: "text/html" };
			if (url === "/legal/_disclaimer/privacy.md")
				return { status: 200, body: "# Privacy statement not configured" };
			if (url === "/legal/_disclaimer/imprint.md")
				return { status: 200, body: "# Imprint not configured" };
			return { status: 404 };
		});
		for (const page of ["privacy", "imprint"] as const) {
			const resolved = await resolveLegalContent(page, { profile: "" });
			expect(resolved.source).toBe("disclaimer");
			for (const marker of TUMAET_PROFILE_MARKERS) {
				expect(resolved.markdown).not.toContain(marker);
			}
		}
		expect(collected.some((u) => u.startsWith("/legal-overrides/"))).toBe(true);
	});
});
