import { describe, expect, it } from "vitest";
import { resolveHeaderBadge } from "./version";

// The header shows the release version in production (linked to its notes) and an
// environment pill everywhere else — a commit SHA never belongs in the header.
describe("resolveHeaderBadge", () => {
	it("shows a production semver as a release linking to its GitHub tag", () => {
		const badge = resolveHeaderBadge("0.73.2", "Production", true);
		expect(badge).toEqual({
			kind: "release",
			label: "v0.73.2",
			href: "https://github.com/ls1intum/Hephaestus/releases/tag/v0.73.2",
			tooltip: "View release notes",
			ariaLabel: "View release v0.73.2",
		});
	});

	it("shows an environment pill for staging, never the version", () => {
		const badge = resolveHeaderBadge("c46f9f8", "Staging", false);
		expect(badge).toEqual({ kind: "environment", label: "Staging", tone: "staging" });
	});

	it.each([
		["Preview", "preview"],
		["Local", "local"],
	] as const)("tones the %s pill as %s", (name, tone) => {
		expect(resolveHeaderBadge("anything", name, false)).toEqual({
			kind: "environment",
			label: name,
			tone,
		});
	});

	it("falls back to an environment pill if production somehow lacks a semver", () => {
		expect(resolveHeaderBadge("nightly", "Production", true).kind).toBe("environment");
	});
});
