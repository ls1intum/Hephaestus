import assert from "node:assert/strict";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/kits/scm/types.ts";
import {
	leadKeys,
	runBundled,
} from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

const SLUG = "changes-dependencies-deliberately";
const keyOf = await leadKeys(SLUG);

function diffFile(file: string, added: string[], removed: string[] = []): DiffFile {
	return {
		path: file,
		addedLines: new Map(added.map((line, i) => [10 + i, line])),
		removedLines: new Map(removed.map((line, i) => [10 + i, line])),
		hunks: [],
	};
}

void test("an XcodeGen project.yml edit lists its changed package lines without calling the package new", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			[
				"project.yml",
				diffFile(
					"project.yml",
					[
						"  ConfettiSwiftUI:",
						"    url: https://github.com/simibac/ConfettiSwiftUI",
						"    from: 2.0.0",
						"      - package: ConfettiSwiftUI",
					],
					["    from: 1.3.0"],
				),
			],
		]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [keyOf(lead.label), lead.at, lead.facts?.side]),
		[
			["raw-manifest-line", { change: "project.yml", line: 10, side: "OLD" }, "removed"],
			["raw-manifest-line", { change: "project.yml", line: 11, side: "NEW" }, "added"],
			["raw-manifest-line", { change: "project.yml", line: 12, side: "NEW" }, "added"],
		],
	);
	assert.equal(result.facts?.manifestsChanged, 1);
	assert.equal(result.facts.onlyAdded, 0);
	assert.equal(result.facts.unpairedManifestLines, 3);
	assert.doesNotMatch((result.directions ?? []).join("\n"), /No changed line in these manifests/u);
});

void test("internal XcodeGen test-target and scheme wiring offers no dependency line and says the name is no applicability", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			[
				"project.yml",
				diffFile("project.yml", [
					"      testTargets:",
					"        - AppTests",
					"  AppTests:",
					"    type: bundle.piece-test",
					"    dependencies:",
					"      - target: App",
				]),
			],
		]),
	});
	assert.deepEqual(result.leads, []);
	assert.equal(result.facts?.manifestsChanged, 1);
	assert.equal(result.facts.unpairedManifestLines, 0);
	const said = (result.directions ?? []).join("\n");
	assert.match(said, /The name alone is no applicability/u);
	assert.match(
		said,
		/No changed line in these manifests has the dependency shape this script reads/u,
	);
});

void test("a pom.xml version moved away from its artifactId is not a dropped pin; a requirement that loses its pin is", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			[
				"pom.xml",
				diffFile(
					"pom.xml",
					[
						"      <artifactId>guava</artifactId>",
						"      <!-- the last release for Java 8 -->",
						"      <type>jar</type>",
						"      <version>33.0.0-jre</version>",
					],
					["      <artifactId>guava</artifactId>", "      <version>33.0.0-jre</version>"],
				),
			],
			[
				"requirements.txt",
				diffFile("requirements.txt", ["requests"], ["requests==2.31.0", "urllib3==2.2.1"]),
			],
		]),
	});
	assert.equal(result.facts?.unpairedManifestLines, 4);
	assert.equal(result.facts.pinsDropped, 1);
	assert.deepEqual(
		(result.leads ?? [])
			.filter((lead) => "change" in lead.at && lead.at.change === "requirements.txt")
			.map((lead) => [keyOf(lead.label), lead.at, lead.facts]),
		[
			[
				"pin-dropped",
				{ change: "requirements.txt", line: 10, side: "NEW" },
				{ ecosystem: "requirements.txt", name: "requests", oldConstraint: "==2.31.0" },
			],
			[
				"only-removed",
				{ change: "requirements.txt", line: 11, side: "OLD" },
				{ ecosystem: "requirements.txt", name: "urllib3" },
			],
		],
	);
});

void test("raw manifest lines beyond the lead cap are counted but reported as not listed", async () => {
	const sourceFiles = Array.from(
		{ length: 45 },
		(_, i) => `      <artifactId>lib-${i}</artifactId>`,
	);
	const result = await runBundled(SLUG, {
		change: new Map([
			[
				"package.json",
				diffFile("package.json", [
					'    "a": "^1.0.0",',
					'    "b": "^2.0.0",',
					'    "c": "^3.0.0",',
				]),
			],
			["pom.xml", diffFile("pom.xml", sourceFiles)],
		]),
	});
	const raw = (result.leads ?? []).filter((lead) => keyOf(lead.label) === "raw-manifest-line");
	assert.equal(result.leads?.length, 40);
	assert.equal(result.facts?.onlyAdded, 3);
	assert.equal(result.facts.unpairedManifestLines, 45);
	assert.equal(result.facts.rawLinesListed, 37);
	assert.equal(raw.length, 37);
	assert.deepEqual(raw.at(-1)?.at, { change: "pom.xml", line: 46, side: "NEW" });
});

void test("metadata lines shaped like dependencies stay candidates with their constraint delta", async () => {
	const result = await runBundled(SLUG, {
		change: new Map([
			["package.json", diffFile("package.json", ['    "node": ">=22"'], ['    "node": ">=20"'])],
			["Cargo.toml", diffFile("Cargo.toml", ['rust-version = "1.80"'], ['rust-version = "1.78"'])],
		]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts]),
		[
			[
				{ change: "package.json", line: 10, side: "NEW" },
				"bumped",
				{ ecosystem: "package.json", name: "node", oldConstraint: ">=20", newConstraint: ">=22" },
			],
			[
				{ change: "Cargo.toml", line: 10, side: "NEW" },
				"bumped",
				{
					ecosystem: "cargo.toml",
					name: "rust-version",
					oldConstraint: "1.78",
					newConstraint: "1.80",
				},
			],
		],
	);
});

void test("a lockfile elsewhere in the checkout is reported by its path, not as the changed manifest's", async () => {
	const result = await runBundled(SLUG, {
		repo: { "tools/docs/package-lock.json": "{}\n" },
		change: new Map([
			["app/package.json", diffFile("app/package.json", ['    "left-pad": "^1.3.0",'])],
		]),
	});
	assert.match(
		(result.directions ?? []).join("\n"),
		/in the checkout: tools\/docs\/package-lock\.json; touched in this diff: none/u,
	);
});
