import { Folder, Package, Rocket } from "lucide-react";
import { describe, expect, it } from "vitest";
import { areaSeed, getAreaVisual, PILL, SEEDED_AREA_SLUGS } from "./areaVisuals";

/**
 * The 11 seeded practice-area slugs (mirror of default-catalog.json). If a slug is renamed in the
 * catalogue without updating the seed map, the area silently loses its icon/colour — this guard fails
 * loudly instead.
 */
const EXPECTED_SLUGS = [
	"review-ready-work",
	"acting-on-review-feedback",
	"actionable-issue-authoring",
	"constructive-code-review",
	"testing-discipline",
	"code-craftsmanship",
	"robust-error-handling",
	"secure-by-default-changes",
	"decisions-and-documentation",
	"delivery-and-version-control-discipline",
	"issue-traceability-and-lifecycle",
];

const BLOCKING_SLUGS = ["robust-error-handling", "secure-by-default-changes", "testing-discipline"];

describe("areaVisuals", () => {
	it("has a seed for every seeded area slug", () => {
		expect([...SEEDED_AREA_SLUGS].sort()).toEqual([...EXPECTED_SLUGS].sort());
	});

	it("resolves every seeded slug to a curated (non-fallback) icon and a real colour pill", () => {
		for (const slug of SEEDED_AREA_SLUGS) {
			const visual = getAreaVisual(slug);
			expect(visual.Icon).not.toBe(Folder);
			expect(visual.pill).toMatch(/^bg-/);
		}
	});

	it("marks exactly the three correctness/security areas as blocking-eligible", () => {
		const blocking = SEEDED_AREA_SLUGS.filter((slug) => areaSeed(slug).blocking);
		expect(blocking.sort()).toEqual([...BLOCKING_SLUGS].sort());
	});

	it("lets an admin-set icon and colour override the seeded default", () => {
		const visual = getAreaVisual(
			"review-ready-work",
			"Packaging work for review",
			"Rocket",
			"fuchsia",
		);
		expect(visual.Icon).toBe(Rocket);
		expect(visual.pill).toBe(PILL.fuchsia);
	});

	it("ignores an unknown icon name / colour key and falls back to the seed", () => {
		const visual = getAreaVisual(
			"review-ready-work",
			"Packaging work for review",
			"NotAnIcon",
			"chartreuse",
		);
		expect(visual.Icon).toBe(Package); // the seeded icon for review-ready-work
		expect(visual.pill).toBe(PILL.sky);
	});

	it("derives an icon from keywords for an unknown admin-created slug, never marking it blocking", () => {
		const security = getAreaVisual("security-hardening", "Security hardening");
		expect(security.Icon).not.toBe(Folder);
		expect(areaSeed("security-hardening", "Security hardening").blocking).toBe(false);
	});

	it("falls back to a neutral folder for a slug with no keyword match", () => {
		const visual = getAreaVisual("custom-team-area", "Custom team area");
		expect(visual.Icon).toBe(Folder);
	});
});
