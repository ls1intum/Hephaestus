import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";
import { promisify } from "node:util";

import {
	discoverJavaSourcePaths,
	isHandwrittenJavaSource,
	nullnessPolicyViolations,
} from "./check-java-nullness.ts";

const execFileAsync = promisify(execFile);
const REPO_ROOT = resolve(import.meta.dirname, "..");
const GIT_ENV = {
	...process.env,
	GIT_DIR: undefined,
	GIT_INDEX_FILE: undefined,
	GIT_WORK_TREE: undefined,
};

const source = (content: string) => [{ path: "Example.java", content }];

await test("accepts unrelated suppressions", () => {
	assert.deepEqual(
		nullnessPolicyViolations(source('@SuppressWarnings({ "unchecked", "deprecation" })')),
		[],
	);
});

await test("rejects direct and namespaced NullAway suppressions", () => {
	for (const warning of ["NullAway", "NullAway.Init", "NullAway.Optional"]) {
		assert.deepEqual(nullnessPolicyViolations(source(`@SuppressWarnings("${warning}")`)), [
			"Example.java",
		]);
	}
	assert.deepEqual(nullnessPolicyViolations(source('@java.lang.SuppressWarnings("NullAway")')), [
		"Example.java",
	]);
});

await test("rejects suppression arrays, concatenation, and Unicode escapes", () => {
	const examples = [
		'@SuppressWarnings({ "unchecked", "NullAway" })',
		'@SuppressWarnings("Null" + "Away")',
		'@SuppressWarnings("Null\\u0041way")',
	];
	for (const example of examples) {
		assert.deepEqual(nullnessPolicyViolations(source(example)), ["Example.java"]);
	}
});

await test("rejects suppression names hidden behind constants", () => {
	assert.deepEqual(nullnessPolicyViolations(source("@SuppressWarnings(NULL_AWAY)")), [
		"Example.java",
	]);
});

await test("ignores the warning name outside SuppressWarnings", () => {
	assert.deepEqual(nullnessPolicyViolations(source('String checker = "NullAway";')), []);
});

await test("matches only handwritten application Java sources", () => {
	assert.equal(
		isHandwrittenJavaSource("server/application/src/main/java/example/Application.java"),
		true,
	);
	assert.equal(
		isHandwrittenJavaSource("server/application/src/test/java/example/ApplicationTest.java"),
		true,
	);
	for (const path of [
		"server/generated-clients/src/main/java/example/Client.java",
		"server/application/src/main/resources/example.java",
		"server/src/main/java/example/Legacy.java",
		"server/application/src/main/java/example/Application.kt",
	]) {
		assert.equal(isHandwrittenJavaSource(path), false, path);
	}
});

await test("discovers Application.java in the real checkout", async () => {
	const paths = await discoverJavaSourcePaths(REPO_ROOT);
	assert.ok(
		paths.includes("server/application/src/main/java/de/tum/cit/aet/hephaestus/Application.java"),
	);
});

await test("fails closed when repository discovery finds no Java sources", async () => {
	const root = await mkdtemp(join(tmpdir(), "java-nullness-"));
	try {
		await execFileAsync("git", ["init", "--quiet"], { cwd: root, env: GIT_ENV });
		await assert.rejects(discoverJavaSourcePaths(root), /No handwritten Java sources found/);
	} finally {
		await rm(root, { recursive: true, force: true });
	}
});
