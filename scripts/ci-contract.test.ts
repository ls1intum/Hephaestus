import assert from "node:assert/strict";
import { glob, readFile } from "node:fs/promises";
import { describe, test } from "node:test";

import { parseDocument } from "yaml";

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
				new RegExp(`(?:node|npm|pnpm) run ${escapeRegExp(candidate)}(?:\\s|$)`).test(command),
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
				const invocation = line.match(/^\s*(?:run:\s+)?(?:\(cd \.\. && )?pnpm run ([\w:-]+)/)?.[1];
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

	void test("runs server integration independently and reuses the server build for E2E", async () => {
		const source = await readFile(".github/workflows/ci-tests.yml", "utf8");
		const build = job(source, "server-build");
		const integration = job(source, "server-integration");
		const e2e = job(source, "webapp-e2e");
		assert.equal((build.match(/actions\/upload-artifact@/g) ?? []).length, 1);
		assert.match(build, /name: \${{ env\.SERVER_REACTOR_ARTIFACT }}/);
		assert.doesNotMatch(integration, /^ {4}needs:/m);
		assert.doesNotMatch(integration, /actions\/download-artifact@/);
		assert.match(integration, /pnpm run test:server:integration/);
		assert.match(e2e, /needs: server-build/);
		assert.equal((e2e.match(/actions\/download-artifact@/g) ?? []).length, 1);
		assert.match(e2e, /name: \${{ env\.SERVER_REACTOR_ARTIFACT }}/);
	});

	void test("builds Storybook once and gives its TurboSnap stats to Chromatic", async () => {
		const storybook = job(
			await readFile(".github/workflows/ci-tests.yml", "utf8"),
			"webapp-storybook",
		);
		assert.equal((storybook.match(/pnpm --filter webapp run build-storybook/g) ?? []).length, 1);
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

	void test("separates pre-merge validation from post-merge artifact production", async () => {
		const source = await readFile(".github/workflows/cicd.yml", "utf8");
		for (const event of ["workflow_dispatch", "push", "pull_request", "merge_group"]) {
			assert.match(source, new RegExp(`^ {2}${event}:`, "m"));
		}
		assert.match(
			job(source, "detect-changes"),
			/version-bump: \${{ steps\.version_bump\.outputs\.changed }}/,
		);
		for (const name of ["workflow-lint", "zizmor", "Quality", "Security", "Test", "Compose"]) {
			assert.match(job(source, name), /needs: \[detect-changes\]/);
			assert.match(
				job(source, name),
				/github\.event_name != 'push'.*needs\.detect-changes\.outputs\.version-bump == 'true'/s,
			);
		}
		assert.doesNotMatch(job(source, "Docker"), /version-bump/);
		assert.match(job(source, "all-ci-passed"), /needs: \[[^\]]*Compose[^\]]*Docker\]/);

		const compose = await readFile(".github/workflows/ci-compose-validate.yml", "utf8");
		assert.match(compose, /on:\n {2}workflow_call:\n/);
		assert.match(job(source, "Compose"), /uses: \.\/\.github\/workflows\/ci-compose-validate\.yml/);
	});

	void test("builds pre-merge candidates on one architecture and final images on both", async () => {
		const source = await readFile(".github/workflows/cicd.yml", "utf8");
		const caller = job(source, "Docker");
		for (const secret of ["SENTRY_AUTH_TOKEN", "SENTRY_ORG", "SENTRY_PROJECT"]) {
			assert.match(
				caller,
				new RegExp(`${secret}: \\$\\{\\{ github\\.event_name == 'push' && secrets\\.${secret}`),
			);
		}

		const docker = await readFile(".github/workflows/ci-docker-build.yml", "utf8");
		for (const name of [
			"webapp-build",
			"application-server-build",
			"agent-pi-build",
			"postgres-build",
		]) {
			const image = job(docker, name);
			assert.match(
				image,
				/single-arch: \${{ github\.event_name == 'pull_request' \|\| github\.event_name == 'merge_group' }}/,
			);
			assert.doesNotMatch(image, /^\s+tags:/m);
		}
		const inherited = job(docker, "tag-unchanged-images");
		assert.match(inherited, /HEAD_SHA/);
		assert.match(inherited, /pr-\$PR_NUMBER/);
		assert.match(inherited, /run-\$RUN_ID-\$RUN_ATTEMPT/);

		const reusable = await readFile(".github/workflows/reusable-docker-build.yml", "utf8");
		for (const tag of [
			/github\.event_name == 'push' && github\.ref_name/,
			/github\.event_name == 'push' && github\.sha/,
			/github\.event_name == 'push' && format\('ci-\{0\}', github\.run_number\)/,
			/github\.event_name == 'pull_request' && github\.event\.pull_request\.head\.sha/,
			/github\.event_name == 'pull_request' && format\('pr-\{0\}', github\.event\.number\)/,
			/run-\${{ github\.run_id }}-\${{ github\.run_attempt }}/,
		]) {
			assert.match(reusable, tag);
		}
		for (const secret of ["SENTRY_AUTH_TOKEN", "SENTRY_ORG", "SENTRY_PROJECT"]) {
			assert.match(reusable, new RegExp(`github\\.event_name == 'push'.*secrets\\.${secret}`));
		}
		assert.match(
			reusable,
			/inputs\.single-arch.*linux\/amd64.*ubuntu-24\.04.*linux\/arm64.*ubuntu-24\.04-arm/,
		);
	});

	void test("enforces the release vulnerability policy where images are built", async () => {
		const reusable = await readFile(".github/workflows/reusable-docker-build.yml", "utf8");
		const scan = job(reusable, "scan");
		// Blocking, and after the push: it needs both build paths, and a skipped `merge`
		// (single-architecture builds) must not skip it.
		assert.match(scan, /needs: \[build, merge\]/);
		assert.match(scan, /!cancelled\(\)/);
		assert.match(scan, /needs\.build\.result == 'success'/);
		// The same script and the same policy file the release and the rescan call. A second
		// copy of the policy is the failure this gate exists to prevent.
		assert.match(
			scan,
			/node scripts\/check-release-vulnerabilities\.ts[\s\S]*security\/vulnerability-policy\.json/,
		);
		assert.match(scan, /run-\${{ github\.run_id }}-\${{ github\.run_attempt }}/);
		assert.match(scan, /linux\/amd64/);
		assert.doesNotMatch(scan, /linux\/arm64/);
		// A gate that cannot say what it rejected is not finished.
		assert.match(scan, /uses: actions\/upload-artifact@/);
		assert.match(scan, /uses: \.\/\.github\/actions\/download-trivy-db/);

		let callSites = 0;
		for (const [file, source] of await workflowSources()) {
			for (const call of source.match(
				/node scripts\/check-release-vulnerabilities\.ts[\s\S]*?\.policy\.json"/g,
			) ?? []) {
				callSites += 1;
				assert.match(
					call,
					/(?<![\w./-])security\/vulnerability-policy\.json/,
					`${file} must evaluate the one release vulnerability policy`,
				);
			}
		}
		// The blocking build gate and the scheduled release rescan; the release path goes through
		// verify-release-evidence.ts and the main rescan through scan-main-images.ts, both of which
		// reach the same evaluator without a workflow-level call site.
		assert.equal(callSites, 2);
	});

	void test("keeps one release vulnerability policy behind every scan", async () => {
		// Every path that evaluates the policy — the build gate, the release, the release rescan and
		// the main rescan — must name this one file. A second copy is the failure the whole effort
		// removes, and it would be invisible: two policies both pass until they disagree.
		const files = [
			...(await Array.fromAsync(glob(".github/workflows/*.{yml,yaml}"))),
			...(await Array.fromAsync(glob("scripts/*.ts"))),
		];
		for (const [file, source] of await readSources(files)) {
			if (file.endsWith(".test.ts")) continue;
			for (const reference of source.match(/[\w./-]*vulnerability-polic[\w-]*\.json/g) ?? [])
				assert.ok(
					// The evidence bundle carries a copy so a release can be re-audited against the
					// policy it was cut under; release.yml is asserted below to copy, not author, it.
					["security/vulnerability-policy.json", "evidence/vulnerability-policy.json"].includes(
						reference,
					) || reference === "vulnerability-policy.json",
					`${file} must evaluate the one release vulnerability policy, not ${reference}`,
				);
		}
		assert.match(
			await readFile(".github/workflows/release.yml", "utf8"),
			/cp security\/vulnerability-policy\.json evidence\/vulnerability-policy\.json/,
		);
		// One committed policy, so "the same policy" is a fact rather than a convention.
		assert.deepEqual(await Array.fromAsync(glob("security/*vulnerability*.json")), [
			"security/vulnerability-policy.json",
		]);
	});

	void test("rescans main's images weekly and reports drift to an issue, not a status", async () => {
		const source = await readFile(".github/workflows/rescan-main-images.yml", "utf8");
		// Weekly, matching the supported-release rescan: the remedy for drift is a rebuild of main,
		// which the next merge performs anyway, so a nightly run would report the same finding six
		// more times before anything could have changed.
		assert.match(source, /^ {4}- cron: "\d+ \d+ \* \* 1"$/m);
		assert.match(source, /workflow_dispatch:/);
		assert.match(source, /group: rescan-main-images/);
		// Writing an issue is the whole point; nothing else here needs a write.
		assert.match(source, /issues: write/);
		assert.doesNotMatch(source, /contents: write|packages: write|id-token: write/);
		// The scan and the reporting are tested TypeScript, not inline bash: the upsert in
		// particular has to not open a duplicate every week, which no eyeball review establishes.
		assert.match(source, /run: node scripts\/scan-main-images\.ts reports/);
		assert.match(source, /run: node scripts\/report-vulnerability-drift\.ts reports/);
		assert.match(source, /uses: \.\/\.github\/actions\/download-trivy-db/);
		assert.match(source, /uses: actions\/upload-artifact@/);
		// Nothing may turn a finding into a red status: no `continue-on-error` fig leaf, and no
		// second evaluator inline.
		assert.doesNotMatch(source, /continue-on-error/);
		assert.doesNotMatch(source, /trivy image/);
	});

	void test("fetches the Trivy database through one action", async () => {
		const action = await readFile(".github/actions/download-trivy-db/action.yml", "utf8");
		// GHCR answers TOOMANYREQUESTS often enough that a gate needs the mirror; stating it once
		// is why this action exists at all.
		assert.match(action, /public\.ecr\.aws\/aquasecurity\/trivy-db/);
		assert.match(action, /--download-db-only/);
		for (const [file, source] of await workflowSources())
			assert.doesNotMatch(
				source,
				/--download-db-only/,
				`${file} must download the Trivy database through .github/actions/download-trivy-db`,
			);
		// Everything that signs or publishes a scan result asserts the database is not stale.
		for (const file of [
			".github/workflows/release.yml",
			".github/workflows/rescan-release-images.yml",
			".github/workflows/rescan-main-images.yml",
		]) {
			const source = await readFile(file, "utf8");
			assert.match(
				source,
				/uses: \.\/\.github\/actions\/download-trivy-db\n\s+with:\n\s+max-age-hours: "24"/,
				`${file} must refuse a stale Trivy database`,
			);
		}
	});

	void test("blocks dependency regressions across the release trust boundary", async () => {
		const workflow = parseDocument(
			await readFile(".github/workflows/ci-security-scan.yml", "utf8"),
		);
		const jobPath = ["jobs", "dependency-review"];
		assert.equal(
			workflow.getIn([...jobPath, "if"]),
			"inputs.should_skip != 'true' && github.event_name == 'pull_request'",
		);
		assert.equal(workflow.getIn([...jobPath, "steps", 0, "with", "fail-on-severity"]), "high");
		assert.equal(
			workflow.getIn([...jobPath, "steps", 0, "with", "fail-on-scopes"]),
			"runtime, development, unknown",
		);
	});

	void test("publishes repository posture outside the pull-request path", async () => {
		const workflow = parseDocument(await readFile(".github/workflows/scorecard.yml", "utf8"));
		assert.equal(workflow.hasIn(["on", "pull_request"]), false);
		for (const event of ["branch_protection_rule", "schedule", "push"])
			assert.equal(workflow.hasIn(["on", event]), true);
		assert.equal(workflow.getIn(["jobs", "analysis", "permissions", "id-token"]), "write");
		assert.equal(workflow.getIn(["jobs", "analysis", "permissions", "security-events"]), "write");
		assert.equal(
			workflow.getIn(["jobs", "analysis", "steps", 0, "with", "persist-credentials"]),
			false,
		);
		assert.equal(workflow.getIn(["jobs", "analysis", "steps", 1, "with", "publish_results"]), true);
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

	void test("centralises dependency installation and browser setup", async () => {
		const sources = await workflowSources();
		for (const [file, source] of sources) {
			assert.doesNotMatch(
				source,
				/pnpm install --frozen-lockfile/,
				`${file} must install through setup-node-pnpm`,
			);
			const setupCalls = source.match(/uses: \.\/\.github\/actions\/setup-node-pnpm/g) ?? [];
			const explicitModes =
				source.match(
					/uses: \.\/\.github\/actions\/setup-node-pnpm\n\s+with:\n\s+install: "(?:none|frozen|hardened)"/g,
				) ?? [];
			assert.equal(
				explicitModes.length,
				setupCalls.length,
				`${file} must select an explicit setup-node-pnpm install mode`,
			);
		}
		const tests = await readFile(".github/workflows/ci-tests.yml", "utf8");
		assert.equal((tests.match(/uses: \.\/\.github\/actions\/setup-browsers/g) ?? []).length, 2);
		assert.doesNotMatch(tests, /playwright install chromium/);
	});

	void test("runs release evidence verification through tested TypeScript", async () => {
		const release = await readFile(".github/workflows/release.yml", "utf8");
		const rescan = await readFile(".github/workflows/rescan-release-images.yml", "utf8");
		assert.match(
			release,
			/SOURCE_TAG: run-\${{ github\.event\.workflow_run\.id }}-\${{ github\.event\.workflow_run\.run_attempt }}/,
		);
		assert.match(release, /imagetools inspect "\$FULL_IMAGE:\$SOURCE_TAG"/);
		assert.doesNotMatch(release, /imagetools inspect "\$FULL_IMAGE:\$SHA"/);
		assert.match(rescan, /node scripts\/verify-release-evidence\.ts release-evidence/);
		assert.doesNotMatch(rescan, /node scripts\/check-release-sbom\.ts/);
		assert.equal((release.match(/node scripts\/verify-release-evidence\.ts/g) ?? []).length, 3);
		assert.doesNotMatch(release, /node scripts\/check-release-(?:sbom|vulnerabilities)\.ts/);
	});

	void test("creates the draft release only once the evidence gate has passed", async () => {
		const source = await readFile(".github/workflows/release.yml", "utf8");
		const decide = job(source, "release");
		const gate = job(source, "tag-images");
		// Release images are promoted by digest and never rebuilt, so a draft cut before the gate
		// can never pass at that commit — and it blocks the next version too, because the
		// precondition below requires the previous version to be published.
		assert.doesNotMatch(decide, /gh release create/);
		assert.match(decide, /is not a published release/);
		assert.match(decide, /is not a stable published release/);
		const created = gate.indexOf('gh release create "$TAG_NAME"');
		const gated = gate.lastIndexOf("node scripts/verify-release-evidence.ts");
		const uploaded = gate.indexOf('gh release upload "$TAG_NAME"');
		assert.ok(gated >= 0, "tag-images must run the evidence verifier");
		assert.ok(created > gated, "the draft must be created after the evidence gate");
		assert.ok(uploaded > created, "release assets need a draft to upload to");
		// A re-run resumes the draft, so uploads must replace assets instead of failing on names.
		for (const upload of source.matchAll(/gh release upload[\s\S]*?\n\n/g)) {
			assert.match(upload[0], /--clobber/);
		}
	});

	void test("exempts a verified revert from the changeset freeze rules", async () => {
		const source = await readFile(".github/workflows/verify-changesets.yml", "utf8");
		const detect = source.indexOf("- name: Detect a verified revert");
		const guard = source.indexOf("- name: Check release-note presence");
		assert.ok(detect >= 0 && guard > detect, "the revert check must precede the freeze guard");
		assert.match(source, /run: node scripts\/verify-revert\.ts "\$BASE_SHA" HEAD/);
		assert.match(source, /steps\.revert\.outputs\.verified-revert != 'true'/);
		// The exemption is structural: a title or branch name is attacker-chosen and never read.
		assert.doesNotMatch(source, /pull_request\.title|github\.head_ref/);
	});

	void test("never invokes a repository-local action before checkout", async () => {
		for (const [file, source] of await workflowSources()) {
			for (const jobSource of source.split(/^ {2}(?=[A-Za-z][\w-]*:\s*$)/m).slice(1)) {
				const firstLocalAction = jobSource.indexOf("uses: ./.github/actions/");
				if (firstLocalAction < 0) continue;
				const checkout = jobSource.indexOf("uses: actions/checkout@");
				assert.ok(
					checkout >= 0 && checkout < firstLocalAction,
					`${file} invokes a local action before checking it out`,
				);
			}
		}
	});
});

void test("CI and package scripts enter the Vite+ task graph", async () => {
	const manifest: unknown = JSON.parse(await readFile("package.json", "utf8"));
	assert.ok(
		manifest &&
			typeof manifest === "object" &&
			"scripts" in manifest &&
			"devDependencies" in manifest,
	);
	const scripts: unknown = manifest.scripts;
	const devDependencies: unknown = manifest.devDependencies;
	assert.ok(scripts && typeof scripts === "object");
	assert.ok(devDependencies && typeof devDependencies === "object");
	assert.ok("check" in scripts && "verify" in scripts);
	assert.equal(scripts.check, "vp run quality");
	assert.equal(scripts.verify, "vp run verification");
	assert.equal("npm-run-all2" in devDependencies, false);

	const graph = await readFile("vite.config.ts", "utf8");
	for (const entry of [
		"quality",
		"verification",
		"gate:server",
		"gate:env",
		"gate:lint-contract",
	]) {
		assert.match(graph, new RegExp(`(?:^|\\s)"?${escapeRegExp(entry)}"?:`));
	}
	for (const entry of [
		"gate:package-manager",
		"gate:java-nullness",
		"gate:server",
		"gate:lint-contract",
		"gate:agents",
		"gate:agent-tests",
		"gate:preview-stack",
		"gate:env",
		"gate:contracts",
		"gate:instructions",
		"gate:docs",
		"gate:webapp-tests",
		"gate:webapp-build",
		"gate:load-syntax",
		"gate:verify:storybook-tests",
		"gate:verify:webapp-build",
		"gate:verify:storybook-build",
		"gate:verify:docs-build",
		"gate:verify:server",
	])
		assert.match(graph, new RegExp(`"${escapeRegExp(entry)}": uncached\\(`));

	for (const [file, source] of await workflowSources()) {
		assert.doesNotMatch(
			source,
			/^\s*(?:run:\s*)?(?:pnpm exec )?(?:vite|vitest|oxlint|oxfmt)(?:\s|$)/m,
			`${file} bypasses the pinned Vite+ interface`,
		);
	}
});
