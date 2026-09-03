import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

import { isRecord, parseJson } from "./lib/json.ts";

const parsedConfig: unknown = parseJson(await readFile("renovate.json", "utf8"));
assert.ok(isRecord(parsedConfig));
const config = parsedConfig;
assert.ok(Array.isArray(config.extends));
assert.ok(config.extends.every((entry) => typeof entry === "string"));
const extensions = config.extends;

void test("Renovate creates bounded update PRs without a manual dispatch queue", () => {
	assert.ok(extensions.includes("config:best-practices"));
	assert.ok(!extensions.includes(":dependencyDashboardApproval"));
	assert.deepEqual(config.schedule, ["before 7am every weekday"]);
	assert.equal(config.prHourlyLimit, 2);
	assert.equal(config.prConcurrentLimit, 5);
	assert.equal(config.branchConcurrentLimit, 10);
	assert.ok(
		Array.isArray(config.packageRules) &&
			config.packageRules.some(
				(rule) =>
					isRecord(rule) &&
					rule.dependencyDashboardApproval === true &&
					Array.isArray(rule.matchUpdateTypes) &&
					rule.matchUpdateTypes.includes("major"),
			),
	);
});

void test("every pin of one toolchain version moves in a single pull request", () => {
	// Renovate merges every matching packageRule in order, so the last one to set a key wins.
	assert.ok(Array.isArray(config.packageRules));
	const rules = config.packageRules.filter(isRecord);
	const ungrouped = rules.findLastIndex((rule) => rule.groupName === null);
	assert.ok(ungrouped >= 0, "a rule must ungroup majors for the toolchain rules to reinstate it");
	for (const [depName, groupName] of [
		["node", "Node.js toolchain"],
		["pnpm", "pnpm toolchain"],
		["ghcr.io/pnpm/pnpm", "pnpm toolchain"],
		["@earendil-works/pi-coding-agent", "Pi SDK"],
	]) {
		const index = rules.findLastIndex(
			(rule) => Array.isArray(rule.matchDepNames) && rule.matchDepNames.includes(depName),
		);
		assert.equal(rules[index]?.groupName, groupName, `${depName} must be grouped as ${groupName}`);
		assert.equal(rules[index]?.matchUpdateTypes, undefined, `${depName} groups every update type`);
		assert.ok(index > ungrouped, `${depName} must be grouped after majors are ungrouped`);
	}
});

void test("vulnerability remediation bypasses normal update latency", () => {
	assert.equal(config.osvVulnerabilityAlerts, true);
	assert.equal(config.dependencyDashboardOSVVulnerabilitySummary, "unresolved");
	assert.deepEqual(config.vulnerabilityAlerts, {
		enabled: true,
		minimumReleaseAge: null,
		dependencyDashboardApproval: false,
		automerge: false,
		groupName: null,
		labels: ["security", "dependencies"],
	});
});

void test("every custom manager extracts a dependency from its real source", async () => {
	const sources = new Map<string, string[]>([
		["Track Dockerfile ARG version pins", ["docker/agents/pi/Dockerfile", "webapp/Dockerfile"]],
		[
			"Track release security tool versions",
			[".github/actions/setup-release-security-tools/action.yml"],
		],
		["Track the Zizmor CLI version", [".github/workflows/cicd.yml"]],
		["Track the pack CLI version", [".github/workflows/reusable-docker-build.yml"]],
		["Track the buildpacks run image", [".github/workflows/ci-build.yml"]],
		[
			"Track the builder and buildpack images in the project descriptor",
			["server/application/project.toml"],
		],
		["Track the OpenAPI Generator CLI distribution", ["openapitools.json"]],
		["Track the Node.js pin in devEngines", ["package.json"]],
		["Track the pnpm pin in devEngines", ["package.json"]],
		[
			"Track the Pi SDK pin in the live agent tests",
			[
				"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/mentor/live/MentorLiveLlmTest.java",
				"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/mentor/live/MentorSandboxStressTest.java",
				"server/application/src/test/java/de/tum/cit/aet/hephaestus/agent/practice/live/PracticeRunnerLiveLlmTest.java",
			],
		],
		["Track release image tags and digests", ["security/release-images.json"]],
	]);
	assert.ok(Array.isArray(config.customManagers));
	assert.ok(config.customManagers.every(isRecord));
	assert.deepEqual(
		new Set(config.customManagers.map((manager) => manager.description)),
		new Set(sources.keys()),
	);
	for (const manager of config.customManagers) {
		if (typeof manager.description !== "string") throw new TypeError("manager description");
		const description = manager.description;
		assert.ok(Array.isArray(manager.matchStrings));
		const files = sources.get(description);
		assert.ok(files);
		const contents = await Promise.all(files.map((file) => readFile(file, "utf8")));
		for (const pattern of manager.matchStrings) {
			if (typeof pattern !== "string") throw new TypeError("manager match pattern");
			assert.ok(
				contents.some((content) => new RegExp(pattern, "m").test(content)),
				`${description} no longer matches its source`,
			);
		}
	}
});
