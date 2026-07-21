const REPO_URL = "https://github.com/ls1intum/Hephaestus";
const SEMVER = /^\d+\.\d+\.\d+$/;

export type EnvironmentTone = "staging" | "preview" | "local";

export type HeaderBadge =
	| { kind: "release"; label: string; href: string; tooltip: string; ariaLabel: string }
	| { kind: "environment"; label: string; tone: EnvironmentTone };

function toneFor(environmentName: string): EnvironmentTone {
	const name = environmentName.toLowerCase();
	if (name === "staging") return "staging";
	if (name === "preview") return "preview";
	return "local";
}

/**
 * Production shows the release version linked to its notes; every other
 * environment shows its name, keeping the commit SHA out of the header.
 */
export function resolveHeaderBadge(
	version: string,
	environmentName: string,
	isProduction: boolean,
): HeaderBadge {
	if (isProduction && SEMVER.test(version)) {
		return {
			kind: "release",
			label: `v${version}`,
			href: `${REPO_URL}/releases/tag/v${version}`,
			tooltip: "View release notes",
			ariaLabel: `View release v${version}`,
		};
	}
	return { kind: "environment", label: environmentName, tone: toneFor(environmentName) };
}
