import assert from "node:assert/strict";
import { glob, readFile } from "node:fs/promises";
import { describe, test } from "node:test";

function job(source: string, name: string): string {
	const match = source.match(
		new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [A-Za-z][\\w-]*:\\s*$|(?![\\s\\S]))`, "m"),
	);
	assert.ok(match, `Missing ${name} job`);
	return match[0];
}

function pathFilter(source: string, name: string): string {
	const match = source.match(
		new RegExp(`^            ${name}:\\n([\\s\\S]*?)(?=^            [\\w-]+:\\s*$)`, "m"),
	);
	assert.ok(match, `Missing ${name} path filter`);
	return match[0];
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
		const source = await readFile(".github/workflows/ci-tests.yml", "utf8");
		const build = job(source, "server-build");
		const integration = job(source, "server-integration");
		const e2e = job(source, "webapp-e2e");
		assert.equal((build.match(/actions\/upload-artifact@/g) ?? []).length, 1);
		for (const consumer of [integration, e2e]) {
			assert.match(consumer, /needs: server-build/);
			assert.equal((consumer.match(/actions\/download-artifact@/g) ?? []).length, 1);
			assert.match(consumer, /name: \${{ env\.SERVER_REACTOR_ARTIFACT }}/);
		}
		assert.match(integration, /\.\/mvnw -pl application -am initialize surefire:test/);
	});

	void test("builds Storybook once and gives its TurboSnap stats to Chromatic", async () => {
		const storybook = job(
			await readFile(".github/workflows/ci-tests.yml", "utf8"),
			"webapp-storybook",
		);
		assert.equal((storybook.match(/bun run --filter webapp build-storybook/g) ?? []).length, 1);
		assert.match(storybook, /test -s webapp\/storybook-static\/preview-stats\.json/);
		assert.match(storybook, /storybookBuildDir: storybook-static/);
		assert.match(storybook, /onlyChanged: true/);
		assert.match(storybook, /surge \.\/webapp\/storybook-static/);
	});

	void test("routes tooling-only changes away from server infrastructure", async () => {
		const source = await readFile(".github/workflows/cicd.yml", "utf8");
		const detection = job(source, "detect-changes");
		const tooling = pathFilter(detection, "tooling");
		for (const pattern of ["docs/**", ".vscode/settings.json", "**/AGENTS.md", "**/CLAUDE.md"]) {
			assert.match(tooling, new RegExp(`- '${escapeRegExp(pattern)}'`));
		}
		assert.doesNotMatch(pathFilter(detection, "application-server"), /- 'docs\/\*\*'/);
		assert.match(pathFilter(detection, "postgres-image"), /- 'docker\/postgres\/\*\*'/);
		assert.match(pathFilter(detection, "webapp-image"), /- 'patches\/\*\*'/);
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
