import { render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/environment", () => ({
	default: { legal: { profile: "" } },
}));

import { LEGAL_PAGE_TITLES, type resolveLegalContent } from "@/lib/legal";
import { LegalPage } from "./LegalPage";

// Operator-supplied Markdown is untrusted. These fixtures inject every
// attack primitive we promised to strip: raw HTML, javascript: hrefs,
// data: images, and event handlers. If any reach the DOM, the guard has
// regressed.
const HOSTILE_MARKDOWN = `# Hostile

<script>window.__xss_executed__ = true;</script>

<iframe srcdoc="<script>parent.__xss_frame__=1</script>"></iframe>

<img src="x" onerror="window.__xss_onerror__=1">

[click me](javascript:alert(1))

[autolink](<javascript:alert(3)>)

[ref-style][ref]

[ref]: javascript:alert(2)

[spoof title](https://tum.de "evil-tooltip")

[ok link](https://tum.de)

![bad](data:image/png;base64,AAA)

![ok](https://tum.de/logo.png "spoof-img-title")
`;

function makeResolver(markdown: string): typeof resolveLegalContent {
	return async () => ({
		markdown,
		source: "profile",
		profile: "tumaet",
	});
}

describe("LegalPage — XSS guardrail", () => {
	it("strips raw HTML, javascript: hrefs, and data: images from operator markdown", async () => {
		const { container } = render(
			<LegalPage
				page="imprint"
				title={LEGAL_PAGE_TITLES.imprint}
				resolver={makeResolver(HOSTILE_MARKDOWN)}
			/>,
		);

		const article = await waitFor(() => {
			const el = container.querySelector("article");
			if (!el) throw new Error("article not rendered yet");
			return el;
		});

		expect(article.querySelector("script")).toBeNull();
		expect(article.querySelector("iframe")).toBeNull();

		for (const el of article.querySelectorAll("*")) {
			for (const attr of el.getAttributeNames()) {
				expect(attr.toLowerCase().startsWith("on")).toBe(false);
			}
		}

		const anchors = Array.from(article.querySelectorAll("a"));
		expect(anchors.length).toBeGreaterThan(0);
		for (const a of anchors) {
			const href = a.getAttribute("href") ?? "";
			expect(/^(?:https?:|mailto:|tel:|#|\/)/i.test(href)).toBe(true);
		}

		const images = Array.from(article.querySelectorAll("img"));
		for (const img of images) {
			const src = img.getAttribute("src") ?? "";
			expect(src.startsWith("data:")).toBe(false);
			expect(/^(?:https?:|\/)/i.test(src)).toBe(true);
		}

		// Markdown titles (`[x](u "title")`, `![x](u "title")`) are a UI-spoof
		// primitive from an untrusted author. SafeAnchor/SafeImage drop them.
		for (const el of article.querySelectorAll("a, img")) {
			expect(el.hasAttribute("title")).toBe(false);
		}

		expect((window as unknown as { __xss_executed__?: true }).__xss_executed__).toBeUndefined();
		expect((window as unknown as { __xss_frame__?: true }).__xss_frame__).toBeUndefined();
		expect((window as unknown as { __xss_onerror__?: true }).__xss_onerror__).toBeUndefined();
	});

	it("external links get noopener/noreferrer + target=_blank", async () => {
		const { container } = render(
			<LegalPage
				page="imprint"
				title={LEGAL_PAGE_TITLES.imprint}
				resolver={makeResolver("[ext](https://tum.de) · [int](/privacy)")}
			/>,
		);
		const article = await waitFor(() => {
			const el = container.querySelector("article");
			if (!el) throw new Error("not rendered");
			return el;
		});
		const [ext, int] = Array.from(article.querySelectorAll("a"));
		expect(ext.getAttribute("target")).toBe("_blank");
		expect(ext.getAttribute("rel")).toBe("noopener noreferrer");
		expect(int.getAttribute("target")).toBeNull();
		expect(int.getAttribute("rel")).toBeNull();
	});

	it("renders the disclaimer banner when resolver returns disclaimer source", async () => {
		const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
		const resolver: typeof resolveLegalContent = async () => ({
			markdown: "# fallback",
			source: "disclaimer",
			profile: "",
		});
		const { findByTestId } = render(
			<LegalPage page="imprint" title={LEGAL_PAGE_TITLES.imprint} resolver={resolver} />,
		);
		await findByTestId("legal-disclaimer-banner");
		expect(warn).toHaveBeenCalled();
		warn.mockRestore();
	});
});
