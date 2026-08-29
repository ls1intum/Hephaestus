import assert from "node:assert/strict";
import { glob, readFile } from "node:fs/promises";
import { describe, test } from "node:test";
import { load } from "js-yaml";
import picomatch from "picomatch";

function record(value: unknown, label: string): Record<string, unknown> {
	assert.ok(value && typeof value === "object" && !Array.isArray(value), `${label} must be a map`);
	return Object.fromEntries(Object.entries(value));
}

function strings(value: unknown, label: string): string[] {
	assert.ok(
		Array.isArray(value) && value.every((item) => typeof item === "string"),
		`${label} must be strings`,
	);
	return value;
}

function array(value: unknown, label: string): unknown[] {
	assert.ok(Array.isArray(value), `${label} must be a list`);
	return value;
}

function selected(patterns: string[], path: string): boolean {
	let match = false;
	for (const pattern of patterns) {
		const excluded = pattern.startsWith("!");
		if (picomatch.isMatch(path, excluded ? pattern.slice(1) : pattern, { dot: true })) {
			match = !excluded;
		}
	}
	return match;
}

async function workflowSources(): Promise<Map<string, string>> {
	const files = (await Array.fromAsync(glob(".github/workflows/*.{yml,yaml}"))).toSorted();
	return readSources(files);
}

async function readSources(files: string[]): Promise<Map<string, string>> {
	return new Map(
		await Promise.all(files.map(async (file) => [file, await readFile(file, "utf8")] as const)),
	);
}

function escapeRegExp(value: string): string {
	return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

void describe("CI contract", () => {
	void test("references every top-level check leg from a workflow", async () => {
		const packageJson: unknown = JSON.parse(await readFile("package.json", "utf8"));
		assert.ok(packageJson && typeof packageJson === "object" && "scripts" in packageJson);
		const rawScripts: unknown = packageJson.scripts;
		assert.ok(rawScripts && typeof rawScripts === "object");
		const scripts: Record<string, string> = {};
		for (const [name, command] of Object.entries(rawScripts)) {
			if (typeof command !== "string")
				throw new TypeError(`package.json#scripts.${name} must be a string`);
			scripts[name] = command;
		}
		const checkScript = scripts.check;
		assert.ok(checkScript);
		const checkLegs = checkScript.split(/\s+/).filter((token) => scripts[token]);
		const reachable = new Set<string>();
		const dependencies = (name: string): string[] => {
			const command = scripts[name];
			if (!command) return [];
			const viaRun = Object.keys(scripts).filter((candidate) =>
				new RegExp(`(?:bun|npm|pnpm) run ${escapeRegExp(candidate)}(?:\\s|$)`).test(command),
			);
			// run-s chains package scripts by name, so every named token is an edge.
			const viaRunS = /(?:^|[;&|]\s*)run-s(?:\s|$)/.test(command)
				? command.split(/\s+/).filter((token) => scripts[token])
				: [];
			return [...new Set([...viaRun, ...viaRunS])];
		};
		const visit = (name: string): void => {
			if (reachable.has(name)) return;
			reachable.add(name);
			for (const dependency of dependencies(name)) visit(dependency);
		};
		// A workflow that runs a script file directly on Node covers every package script built on it.
		const scriptsByFile = new Map<string, string[]>();
		for (const [name, command] of Object.entries(scripts)) {
			for (const file of command.match(/scripts\/[\w./-]+\.ts/g) ?? []) {
				scriptsByFile.set(file, [...(scriptsByFile.get(file) ?? []), name]);
			}
		}
		for (const source of (await workflowSources()).values()) {
			for (const line of source.split("\n")) {
				const invocation = line.match(
					/^\s*(?:run:\s+)?(?:\(cd \.\. && )?bun(?: --bun)? run ([\w:-]+)/,
				)?.[1];
				if (invocation && scripts[invocation]) visit(invocation);
				const file = line.match(/(?:^|\s)node (?:--test )?(?:\.\.\/)?(scripts\/[\w./-]+\.ts)/)?.[1];
				for (const name of file ? (scriptsByFile.get(file) ?? []) : []) visit(name);
			}
		}

		assert.deepEqual(
			checkLegs.filter((leg) => !reachable.has(leg)),
			[],
			"Every package.json#check leg must be covered by at least one workflow",
		);
	});

	void test("passes only cache types accepted by setup-caches", async () => {
		const action = await readFile(".github/actions/setup-caches/action.yml", "utf8");
		assert.match(action, /steps:\s+- name: Validate cache type/);
		const validation = action.match(/case "\$CACHE_TYPE" in\s+([^\n]+)\) ;;/);
		assert.ok(validation, "setup-caches must explicitly validate cache-type");
		assert.match(action, /\*\) [^\n]*exit 1 ;;\s+esac/);
		const acceptedValues = validation[1];
		assert.ok(acceptedValues);
		const accepted = new Set(acceptedValues.split("|"));
		const implementation = action.replace(validation[0], "");
		for (const cacheType of accepted) {
			assert.ok(
				implementation.includes(`'${cacheType}'`) ||
					implementation.includes(`"${cacheType}"`) ||
					(cacheType.startsWith("application-server-") &&
						implementation.includes("startsWith(inputs.cache-type, 'application-server-')")),
				`setup-caches accepts ${cacheType} but no step handles it`,
			);
		}
		const rejected: string[] = [];

		for (const [file, source] of await workflowSources()) {
			for (const match of source.matchAll(/^\s+cache-type:\s+(.+)$/gm)) {
				const rawValue = match[1];
				assert.ok(rawValue);
				const value = rawValue.trim();
				if (!value.startsWith("${{")) {
					if (!accepted.has(value)) rejected.push(`${file}: ${value}`);
					continue;
				}
				const key = value.match(/matrix\.([\w-]+)/)?.[1];
				assert.ok(key, `${file}: cache-type expression must resolve from a matrix key`);
				const values = [...source.matchAll(new RegExp(`^\\s+- ${key}: (.+)$`, "gm"))].map(
					(item) => {
						const matrixValue = item[1];
						assert.ok(matrixValue);
						return matrixValue.trim();
					},
				);
				assert.notEqual(values.length, 0, `${file}: matrix.${key} has no static values`);
				for (const matrixValue of values) {
					if (!accepted.has(matrixValue)) rejected.push(`${file}: ${matrixValue}`);
				}
			}
		}
		assert.deepEqual(rejected, [], "Workflows may only request recognised cache types");
	});

	void test("shares one server build with integration and E2E", async () => {
		const workflow = record(
			load(await readFile(".github/workflows/ci-tests.yml", "utf8")),
			"test workflow",
		);
		const jobs = record(workflow.jobs, "test jobs");
		const build = record(jobs["server-build"], "server-build");
		const integration = record(jobs["server-integration"], "server-integration");
		const e2e = record(jobs["webapp-e2e"], "webapp-e2e");
		assert.equal(integration.needs, "server-build");
		assert.equal(e2e.needs, "server-build");

		const buildSteps = array(build.steps, "server-build steps").map((step) =>
			record(step, "server-build step"),
		);
		const integrationSteps = array(integration.steps, "server-integration steps").map((step) =>
			record(step, "server-integration step"),
		);
		const e2eSteps = array(e2e.steps, "webapp-e2e steps").map((step) =>
			record(step, "webapp-e2e step"),
		);
		const uploads = buildSteps.filter((step) => String(step.uses).includes("upload-artifact"));
		const downloads = [...integrationSteps, ...e2eSteps].filter((step) =>
			String(step.uses).includes("download-artifact"),
		);
		assert.equal(uploads.length, 1);
		assert.equal(downloads.length, 2);
		const artifactName = record(uploads[0]?.with, "artifact upload inputs").name;
		for (const download of downloads) {
			assert.equal(record(download.with, "artifact download inputs").name, artifactName);
		}
		const testCommand = integrationSteps
			.map((step) => step.run)
			.find((run) => typeof run === "string");
		assert.match(String(testCommand), /\.\/mvnw .* initialize surefire:test/);
	});

	void test("builds Storybook once and gives its TurboSnap stats to Chromatic", async () => {
		const workflow = record(
			load(await readFile(".github/workflows/ci-tests.yml", "utf8")),
			"test workflow",
		);
		const jobs = record(workflow.jobs, "test jobs");
		const storybook = record(jobs["webapp-storybook"], "webapp-storybook");
		const steps = array(storybook.steps, "webapp-storybook steps").map((step) =>
			record(step, "webapp-storybook step"),
		);
		const builds = steps.filter(
			(step) => typeof step.run === "string" && step.run.includes("build-storybook"),
		);
		assert.equal(builds.length, 1);
		assert.match(String(builds[0]?.run), /test -s webapp\/storybook-static\/preview-stats\.json/);
		const chromatic = steps.find(
			(step) => typeof step.uses === "string" && step.uses.startsWith("chromaui/action@"),
		);
		const chromaticInputs = record(chromatic?.with, "Chromatic inputs");
		assert.equal(chromaticInputs.storybookBuildDir, "storybook-static");
		assert.equal(chromaticInputs.onlyChanged, true);
		const surge = steps.find(
			(step) => typeof step.run === "string" && step.run.includes("/surge "),
		);
		assert.match(String(surge?.run), /\.\/webapp\/storybook-static/);
	});

	void test("routes tooling-only changes away from server infrastructure", async () => {
		const workflow = record(load(await readFile(".github/workflows/cicd.yml", "utf8")), "CI");
		const jobs = record(workflow.jobs, "CI jobs");
		const detection = record(jobs["detect-changes"], "detect-changes");
		const steps = array(detection.steps, "detect-changes steps").map((step) =>
			record(step, "detect-changes step"),
		);
		const filter = steps.find((step) => step.id === "filter");
		const filterSource = record(filter?.with, "path-filter inputs").filters;
		if (typeof filterSource !== "string") throw new TypeError("path filters must be YAML");
		const filters = record(load(filterSource), "path filters");
		const tooling = strings(filters.tooling, "tooling filter");
		const server = strings(filters["application-server"], "application-server filter");
		const postgres = strings(filters["postgres-image"], "postgres-image filter");
		const webappImage = strings(filters["webapp-image"], "webapp-image filter");

		for (const path of ["docs/guide.mdx", ".vscode/settings.json", "server/AGENTS.md"]) {
			assert.ok(selected(tooling, path), `${path} must run tooling checks`);
			assert.ok(!selected(server, path), `${path} must not run Maven`);
			assert.ok(!selected(postgres, path), `${path} must not run PostgreSQL validation`);
		}
		assert.ok(selected(postgres, "docker/postgres/Dockerfile"));
		assert.ok(selected(webappImage, "patches/storybook.patch"));
	});

	void test("pins every external action to a full commit SHA with a version comment", async () => {
		const invalid: string[] = [];
		const files = await Array.fromAsync(glob(".github/{actions,workflows}/**/*.{yml,yaml}"));
		for (const [file, source] of await readSources(files)) {
			for (const [index, line] of source.split("\n").entries()) {
				const uses = line.match(/^\s+(?:- )?uses:\s+(\S+)(.*)$/);
				if (!uses) continue;
				const reference = uses[1];
				const comment = uses[2];
				assert.ok(reference && comment !== undefined);
				if (reference.startsWith("./")) continue;
				if (!/@[0-9a-f]{40}$/.test(reference) || !/^ # v\S+(?:\s.*)?$/.test(comment)) {
					invalid.push(`${file}:${index + 1}: ${reference}`);
				}
			}
		}
		assert.deepEqual(invalid, [], "External uses must match @<40 lowercase hex> # v...");
	});
});
