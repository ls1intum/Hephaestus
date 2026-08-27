import { Folder, Package, Rocket, ShieldCheck } from "lucide-react";
import { describe, expect, it } from "vitest";
import { getGroupVisual, ICON_NAMES, iconLabel, iconSearchText, PILL } from "./group-visuals";

describe("groupVisuals", () => {
	it("lets an admin-set icon and color override the seeded default", () => {
		const visual = getGroupVisual(
			"review-ready-work",
			"Packaging work for review",
			"Rocket",
			"fuchsia",
		);
		expect(visual.Icon).toBe(Rocket);
		expect(visual.pill).toBe(PILL.fuchsia);
	});

	it("ignores an unknown icon name or color key and falls back to the seed", () => {
		const visual = getGroupVisual(
			"review-ready-work",
			"Packaging work for review",
			"NotAnIcon",
			"chartreuse",
		);
		expect(visual.Icon).toBe(Package);
		expect(visual.pill).toBe(PILL.sky);
	});

	it("derives an icon from keywords for an unknown admin-created slug", () => {
		const security = getGroupVisual("security-hardening", "Security hardening");
		expect(security.Icon).toBe(ShieldCheck);
		expect(security.pill).toBe(PILL.red);
	});

	it("falls back to a neutral folder for a slug with no keyword match", () => {
		const visual = getGroupVisual("custom-team-group", "Custom team group");
		expect(visual.Icon).toBe(Folder);
	});

	it.each([
		["GitBranch", "git branch"],
		["ShieldX", "shield x"],
		["Code2", "code2"],
		["MessageSquareReply", "message square reply"],
	])("splits the PascalCase icon name %s into searchable %s", (name, expected) => {
		expect(iconSearchText(name)).toBe(expected);
	});

	it("turns icon identifiers into human labels", () => {
		expect(iconLabel("MessageSquareReply")).toBe("Message square reply");
	});

	it("matches icons by a word fragment of their split name", () => {
		const hits = ICON_NAMES.filter((n) => iconSearchText(n).includes("git"));
		expect(hits).toStrictEqual(expect.arrayContaining(["GitBranch", "GitPullRequest", "GitMerge"]));
		expect(hits).not.toContain("ShieldX");
	});
});
