import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, readdir, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

import { isMap, isSeq, parseDocument } from "yaml";

const release = "v0.93.0";
const commit = "c".repeat(40);
const repository = "ghcr.io/hephaestus-build/webapp";
const indexDigest = `sha256:${"a".repeat(64)}`;
const platforms = ["amd64", "arm64"];
const root = path.resolve(import.meta.dirname, "..");
const hash = (bytes: string | Uint8Array) => createHash("sha256").update(bytes).digest("hex");

const workflow = parseDocument(await readFile(".github/workflows/release.yml", "utf8"));
const steps = workflow.getIn(["jobs", "publish-release", "steps"]);
assert.ok(isSeq(steps));
const script = [
	"Verify supported-host smoke records",
	"Verify durable assets from a clean environment",
	"Publish immutable release",
	"Promote verified image digests",
]
	.map((name) => {
		const step = steps.items.find((item) => isMap(item) && item.get("name") === name);
		assert.ok(isMap(step));
		const body = step.get("run");
		assert.equal(typeof body, "string");
		assert.ok(typeof body === "string");
		return body
			.replaceAll(/\$\{\{ github.server_url \}\}/gu, "https://github.com")
			.replaceAll(/\$\{\{ github.repository \}\}/gu, "hephaestus-build/Hephaestus");
	})
	.join("\n");
assert.ok(!script.includes("${{"));

// Only remote services are fixtures. The steps run the real lock, checksum, SBOM and policy
// verifiers. Synthetic signatures authenticate fixture bytes; they make no claim about Sigstore.
const transport = String.raw`#!/usr/bin/env node
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const tool = path.basename(process.argv[1]);
const args = process.argv.slice(2);
const store = process.env.ARCHIVE;
const read = name => JSON.parse(fs.readFileSync(path.join(store, name), 'utf8'));
const state = () => read('state');
const option = flag => args[args.indexOf(flag) + 1];
fs.appendFileSync(process.env.CALL_LOG, JSON.stringify([tool, ...args]) + '\n');
if (tool === 'gh') {
  if (args[0] === 'attestation') process.exit(0);
  if (args[0] === 'api') {
    if (process.env.AUTHORITY_UNAVAILABLE === 'true') throw new Error('Release authority unavailable');
    if (!args.includes('--paginate') || !args.includes('--jq') || args.includes('--slurp')) throw new Error('Expected projected paginated release listing');
    const response = read('releases').map(release => ({target_commitish:'main', ...release}));
    const projected = require('node:child_process').spawnSync('jq', ['-c', option('--jq')], {input:JSON.stringify(response), encoding:'utf8'});
    if (projected.status !== 0) throw new Error(projected.stderr);
    process.stdout.write(projected.stdout);
    process.exit(0);
  }
  if (args[1] === 'download') {
    for (const file of fs.readdirSync(store)) {
      if (!['state', 'aliases', 'releases'].includes(file)) fs.copyFileSync(path.join(store, file), path.join(option('--dir'), file));
    }
  } else if (args[1] === 'view') {
    const field = option('--jq');
    if (!['.isDraft', '.isImmutable'].includes(field)) throw new Error('Unexpected release query');
    console.log(state()[field.slice(1)]);
  } else if (args[1] === 'upload') {
    if (!state().isDraft) throw new Error('Immutable assets cannot be overwritten');
    for (const file of args.slice(3, args.indexOf('--clobber'))) fs.copyFileSync(file, path.join(store, path.basename(file)));
  } else if (args[1] === 'edit') {
    if (!state().isDraft) throw new Error('Published releases cannot be edited');
    fs.writeFileSync(path.join(store, 'state'), JSON.stringify({isDraft:false, isImmutable:process.env.PUBLISH_MUTABLE !== 'true'}));
  } else throw new Error('Unexpected gh command');
} else if (tool === 'cosign') {
  if (args[0] === 'verify-blob') {
    const bytes = fs.readFileSync(args.at(-1));
    const signedHash = fs.readFileSync(option('--bundle'), 'utf8');
    if (crypto.createHash('sha256').update(bytes).digest('hex') !== signedHash) throw new Error('Signature mismatch');
  } else if (args[0] === 'verify-attestation') {
    const subject = read('manifest.json').subjects.find(subject => args.at(-1).endsWith(subject.digest));
    const predicate = read('webapp-' + subject.platform.replace('/', '-') + '.spdx.json');
    console.log(JSON.stringify({payload:Buffer.from(JSON.stringify({predicate})).toString('base64')}));
  } else if (args[0] !== 'verify') throw new Error('Unexpected cosign command');
} else if (tool === 'docker') {
  if (args.includes('--raw')) {
    console.log(JSON.stringify({manifests:read('manifest.json').subjects.map(subject => ({digest:subject.digest, platform:{os:'linux', architecture:subject.platform.split('/')[1]}}))}));
  } else if (args.includes('inspect')) {
    const reference = args[3];
    const digest = reference.includes('@') ? reference.split('@')[1] : read('aliases')[reference];
    console.log(JSON.stringify({digest}));
  } else if (args.includes('create')) {
    if (!state().isImmutable) throw new Error('Aliases require immutable publication');
    const aliases = read('aliases');
    for (let i = 0; i < args.length; i++) {
      if (args[i] !== '-t') continue;
      aliases[args[++i]] = args.at(-1).split('@')[1];
      fs.writeFileSync(path.join(store, 'aliases'), JSON.stringify(aliases));
      if (process.env.FAIL_PARTIAL === 'true') process.exit(23);
    }
  } else throw new Error('Unexpected docker command');
} else throw new Error('Unexpected tool');
`;

async function fixture(directory: string) {
	const archive = path.join(directory, "archive");
	const bin = path.join(directory, "bin");
	const smoke = path.join(directory, "host-smoke");
	for (const folder of [archive, bin, smoke]) {
		await mkdir(folder);
	}
	await symlink(path.join(root, "scripts"), path.join(directory, "scripts"), "dir");
	const json = async (name: string, value: unknown) =>
		writeFile(path.join(archive, name), JSON.stringify(value));
	for (const tool of ["gh", "cosign", "docker"]) {
		await writeFile(path.join(bin, tool), transport, { mode: 0o755 });
	}
	const environment = {
		...process.env,
		PATH: `${bin}${path.delimiter}${process.env.PATH ?? ""}`,
		ARCHIVE: archive,
		CALL_LOG: path.join(directory, "calls"),
		RUNNER_TEMP: directory,
		GITHUB_REPOSITORY: "hephaestus-build/Hephaestus",
		TAG_NAME: release,
		RELEASE_SHA: commit,
		VERSION: release.slice(1),
		SERIES: "0.93",
	};
	const subjects = platforms.map((architecture, index) => ({
		image: "webapp",
		repository,
		provenance: "first-party",
		indexDigest,
		digest: `sha256:${String(index + 1).repeat(64)}`,
		platform: `linux/${architecture}`,
	}));
	await json("manifest.json", { schemaVersion: 1, release, commit, subjects });
	await json("release-images.json", { schemaVersion: 1, images: ["webapp"], upstream: [] });
	await json("vulnerability-policy.json", {
		schemaVersion: 3,
		componentInventories: [],
		goAdvisories: [],
		exceptions: [],
	});
	await json(`release-${release}.json`, {
		schemaVersion: 1,
		release,
		commit,
		images: [
			{
				image: "webapp",
				repository,
				provenance: "first-party",
				indexDigest,
				platforms: Object.fromEntries(
					subjects.map((subject) => [subject.platform, subject.digest]),
				),
			},
		],
	});
	for (const subject of subjects) {
		const prefix = `webapp-${subject.platform.replace("/", "-")}`;
		const reference = `${repository}@${subject.digest}`;
		const artifact = {
			name: "example",
			version: "1.0.0",
			type: "npm",
			purl: "pkg:npm/example@1.0.0",
			locations: [{ path: "/app/package.json" }],
			licenses: [{ value: "MIT" }],
		};
		await json(`${prefix}.syft.json`, {
			source: {
				type: "image",
				name: repository,
				version: subject.digest,
				metadata: {
					userInput: reference,
					manifestDigest: subject.digest,
					mediaType: "application/vnd.oci.image.manifest.v1+json",
					os: "linux",
					architecture: subject.platform.split("/")[1],
					repoDigests: [reference],
				},
			},
			artifacts: [artifact],
		});
		await json(`${prefix}.spdx.json`, {
			spdxVersion: "SPDX-2.3",
			dataLicense: "CC0-1.0",
			documentNamespace: "https://example.invalid/sbom",
			packages: [
				{ name: repository, versionInfo: subject.digest, primaryPackagePurpose: "CONTAINER" },
				{
					name: artifact.name,
					versionInfo: artifact.version,
					externalRefs: [{ referenceType: "purl", referenceLocator: artifact.purl }],
				},
			],
		});
		await json(`${prefix}.cdx.json`, {
			bomFormat: "CycloneDX",
			specVersion: "1.7",
			metadata: { component: { type: "container", name: repository, version: subject.digest } },
			components: [artifact],
		});
		for (const kind of ["trivy", "license"]) {
			await json(`${prefix}.${kind}.json`, { ArtifactName: reference, Results: [] });
		}
	}
	const validation = spawnSync(
		process.execPath,
		[path.join(root, "scripts/verify-release-evidence.ts"), archive, "--write-validation"],
		{ encoding: "utf8", env: environment },
	);
	assert.equal(validation.status, 0, validation.stderr);
	const files = await readdir(archive);
	const checksums = await Promise.all(
		files.map(async (file) => `${hash(await readFile(path.join(archive, file)))}  ${file}\n`),
	);
	await writeFile(path.join(archive, "SHA256SUMS"), checksums.join(""));
	for (const file of ["SHA256SUMS", `release-${release}.json`]) {
		await writeFile(
			path.join(archive, `${file}.sigstore.json`),
			hash(await readFile(path.join(archive, file))),
		);
	}
	for (const architecture of platforms) {
		await writeFile(
			path.join(smoke, `host-smoke-${architecture}.json`),
			JSON.stringify({
				schemaVersion: 1,
				release,
				commit,
				testedAt: "2026-10-15T10:00:00Z",
				host: { architecture, os: "Ubuntu 24.04", dockerEngine: "28.0.0", dockerCompose: "2.24.4" },
				services: [{ service: "webapp", state: "running", health: "healthy", exitCode: 0 }],
			}),
		);
	}
	await json("state", { isDraft: true, isImmutable: false });
	await json("aliases", {});
	await json("releases", [
		{ tag_name: release, draft: false, prerelease: false },
		{ tag_name: "v0.9.0", draft: false, prerelease: false },
		{ tag_name: "v99.0.0", draft: false, prerelease: true },
		{ tag_name: "v100.0.0", draft: true, prerelease: false },
	]);
	const run = async (overrides: Record<string, string> = {}) => {
		await rm(path.join(directory, "evidence"), { recursive: true, force: true });
		await rm(path.join(directory, "release-lock.env"), { force: true });
		await writeFile(environment.CALL_LOG, "");
		const result = spawnSync("bash", ["--noprofile", "--norc", "-e", "-c", script], {
			cwd: directory,
			encoding: "utf8",
			env: { ...environment, ...overrides },
		});
		assert.equal(result.error, undefined);
		return {
			status: result.status,
			output: result.stdout + result.stderr,
			calls: await readFile(environment.CALL_LOG, "utf8"),
		};
	};
	const assets = async () => {
		const assetFiles = await readdir(archive);
		return Object.fromEntries(
			await Promise.all(
				assetFiles
					.filter((file) => !["state", "aliases", "releases"].includes(file))
					.map(async (file) => [file, hash(await readFile(path.join(archive, file)))] as const),
			),
		);
	};
	return { archive, smoke, json, run, assets };
}

const authorityRefusal = /Only the current stable release/u;
const refusalReasons = new Map([
	["wrong-commit", /qualified source commit/u],
	["tampered-policy", /sha256sum failed/u],
	["missing-sbom", /sha256sum failed/u],
	["authority-unavailable", /Release authority unavailable/u],
	["newer-patch", authorityRefusal],
	["newer-minor", authorityRefusal],
	["authority-empty", authorityRefusal],
	["authority-inconsistent", authorityRefusal],
	["authority-malformed", /boolean draft and prerelease flags/u],
	["authority-malformed-draft", /boolean draft and prerelease flags/u],
	["version-mismatch", /Release version does not match verified evidence/u],
]);

for (const scenario of [
	"draft",
	"retry",
	"tampered-smoke",
	"missing-smoke",
	"tampered-policy",
	"missing-sbom",
	"wrong-commit",
	"mutable-publication",
	"newer-patch",
	"newer-minor",
	"authority-unavailable",
	"authority-empty",
	"authority-inconsistent",
	"authority-malformed",
	"authority-malformed-draft",
	"version-mismatch",
]) {
	void test(
		`release publication: ${scenario}`,
		{ skip: process.platform === "win32" },
		async (t) => {
			const directory = await mkdtemp(path.join(tmpdir(), "release-publication-"));
			t.after(async () => rm(directory, { recursive: true, force: true }));
			const { archive, smoke, json, run, assets } = await fixture(directory);
			if (scenario === "draft") {
				const result = await run();
				assert.equal(result.status, 0, result.output);
				assert.match(result.calls, /"upload"/u);
				assert.match(result.calls, /"edit"/u);
				assert.deepEqual(
					await readFile(path.join(archive, "host-smoke-arm64.json")),
					await readFile(path.join(smoke, "host-smoke-arm64.json")),
				);
				assert.equal(
					await readFile(path.join(archive, "state"), "utf8"),
					JSON.stringify({ isDraft: false, isImmutable: true }),
				);
				return;
			}
			if (scenario === "mutable-publication") {
				const result = await run({ PUBLISH_MUTABLE: "true" });
				assert.notEqual(result.status, 0);
				assert.match(result.output, /published release is not immutable/u);
				assert.doesNotMatch(result.calls, /"create"/u);
				return;
			}
			const first = await run({ FAIL_PARTIAL: "true" });
			assert.equal(first.status, 23, first.output);
			assert.match(first.calls, /"upload"/u);
			assert.match(first.calls, /"edit"/u);
			const archivedSmoke = path.join(archive, "host-smoke-amd64.json");
			assert.deepEqual(
				await readFile(archivedSmoke),
				await readFile(path.join(smoke, "host-smoke-amd64.json")),
			);
			if (scenario === "tampered-smoke") {
				await writeFile(archivedSmoke, "{}");
			}
			if (scenario === "missing-smoke") {
				await rm(archivedSmoke);
			}
			if (scenario === "tampered-policy") {
				await json("vulnerability-policy.json", {});
			}
			if (scenario === "missing-sbom") {
				await rm(path.join(archive, "webapp-linux-arm64.spdx.json"));
			}
			if (scenario === "wrong-commit") {
				const lock = path.join(archive, `release-${release}.json`);
				const contents = await readFile(lock, "utf8");
				await writeFile(lock, contents.replace(commit, "d".repeat(40)));
				await writeFile(`${lock}.sigstore.json`, hash(await readFile(lock)));
			}
			if (scenario === "newer-patch" || scenario === "newer-minor") {
				const newer = scenario === "newer-patch" ? "v0.93.1" : "v0.100.0";
				await json("releases", [
					{ tag_name: release, draft: false, prerelease: false, created_at: "2026-10-15" },
					{ tag_name: newer, draft: false, prerelease: false, created_at: "2026-10-01" },
				]);
				await json("aliases", {
					[`${repository}:latest`]: `sha256:${"f".repeat(64)}`,
					[`${repository}:0.93`]: `sha256:${"f".repeat(64)}`,
				});
			}
			if (scenario === "authority-empty") {
				await json("releases", []);
			}
			if (scenario === "authority-inconsistent") {
				await json("releases", [{ tag_name: release, draft: false, prerelease: true }]);
			}
			if (scenario === "authority-malformed") {
				await json("releases", [
					{ tag_name: release, draft: false, prerelease: false },
					{ tag_name: "v0.93.1", draft: false },
				]);
			}
			if (scenario === "authority-malformed-draft") {
				await json("releases", [
					{ tag_name: release, draft: false, prerelease: false },
					{ tag_name: "v0.93.1", prerelease: false },
				]);
			}
			if (scenario === "version-mismatch") {
				await json("releases", [{ tag_name: "v0.93.1", draft: false, prerelease: false }]);
			}
			const before = await assets();
			const aliasesBefore = await readFile(path.join(archive, "aliases"), "utf8");
			const retry = await run(
				scenario === "version-mismatch"
					? { VERSION: "0.93.1" }
					: { AUTHORITY_UNAVAILABLE: String(scenario === "authority-unavailable") },
			);
			assert.equal(retry.status === 0, scenario === "retry", retry.output);
			assert.match(retry.calls, /"verify-blob"/u);
			assert.doesNotMatch(retry.calls, /"upload"|"edit"/u);
			if (scenario === "retry") {
				assert.match(retry.calls, /"verify-attestation"/u);
				assert.equal(
					await readFile(path.join(archive, "aliases"), "utf8"),
					JSON.stringify({
						[`${repository}:0.93.0`]: indexDigest,
						[`${repository}:0.93`]: indexDigest,
						[`${repository}:latest`]: indexDigest,
					}),
				);
			} else {
				assert.doesNotMatch(retry.calls, /"create"/u);
				assert.equal(await readFile(path.join(archive, "aliases"), "utf8"), aliasesBefore);
				const reason = refusalReasons.get(scenario) ?? /host smoke record differs or is missing/u;
				assert.match(retry.output, reason);
			}
			assert.deepEqual(await assets(), before, "retry must keep published assets unchanged");
		},
	);
}
