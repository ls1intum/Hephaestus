import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmod, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

import { isMap, isSeq, parseDocument } from "yaml";

import { asArray, asRecord } from "./lib/json.ts";

void test("image provenance can persist storage records through every reusable-workflow caller", async () => {
	const callers = {
		"cicd.yml": ["application-server-image", "Docker"],
		"ci-docker-build.yml": ["webapp-build", "agent-pi-build", "postgres-build"],
		"reusable-docker-build.yml": ["build", "merge"],
	};
	for (const [file, jobs] of Object.entries(callers)) {
		const workflow = parseDocument(await readFile(`.github/workflows/${file}`, "utf8"));
		const permissions = workflow.get("permissions");
		assert.ok(isMap(permissions));
		assert.deepEqual(permissions.toJSON(), {});
		for (const job of jobs) {
			for (const permission of ["id-token", "attestations", "artifact-metadata"]) {
				assert.equal(
					workflow.getIn(["jobs", job, "permissions", permission]),
					"write",
					`${file}: ${job}: ${permission}`,
				);
			}
		}
		if (file !== "reusable-docker-build.yml") {
			continue;
		}
		for (const job of jobs) {
			const steps = workflow.getIn(["jobs", job, "steps"]);
			assert.ok(isSeq(steps));
			const attestation = asArray(steps.toJSON(), "steps")
				.map((step) => asRecord(step, "step"))
				.find((step) => step.name === "Generate build provenance attestation");
			assert.ok(attestation);
			assert.match(String(attestation.uses), /^actions\/attest@[a-f0-9]{40}$/u);
			const inputs = asRecord(attestation.with, "attestation inputs");
			assert.equal(inputs["push-to-registry"], true);
			assert.notEqual(inputs["create-storage-record"], false);
			assert.match(String(inputs["subject-digest"]), /outputs\.(?:manifest-digest|digest)/u);
		}
	}
});

void test("artifact checks have no registry publishing permissions", async () => {
	const workflow = parseDocument(await readFile(".github/workflows/cicd.yml", "utf8"));
	const permissions = workflow.getIn(["jobs", "Build", "permissions"]);
	assert.ok(isMap(permissions));
	assert.deepEqual(permissions.toJSON(), { contents: "read", checks: "write" });
});

const repository = "hephaestus-build/Hephaestus";
const owner = "hephaestus-build";
const digest = `sha256:${"a".repeat(64)}`;
const image = `ghcr.io/${owner}/webapp`;
const immutable = `${image}@${digest}`;
const verificationSites = [
	{ file: "reusable-docker-build.yml", job: "build", step: "Sign and verify image" },
	{ file: "reusable-docker-build.yml", job: "merge", step: "Verify signature + attestation" },
	{
		file: "ci-docker-build.yml",
		job: "tag-unchanged-images",
		step: "Verify and tag unchanged images",
	},
];

/** The status the script must end with when a fixture tool rejects the image, by failing tool. */
const exitCodes = { none: 0, signature: 7, attestation: 9 };

// These are Linux publishing jobs. Execute their actual shell bodies with tool-boundary fixtures
// so different quoting or function control flow cannot silently weaken one verification site.
for (const site of verificationSites) {
	const failures =
		site.job === "tag-unchanged-images"
			? exitCodes
			: {
					...exitCodes,
					...(site.job === "build" ? { signing: 6 } : {}),
					publication: 11,
					changedDigest: 1,
					emptyTags: 4,
					invalidMetadata: 5,
				};
	for (const [failure, exitCode] of Object.entries(failures)) {
		void test(
			`${site.job}: ${failure === "none" ? "success" : `${failure} rejection`} preserves immutable-image verification`,
			{ skip: process.platform === "win32" },
			async (t) => {
				const workflow = parseDocument(await readFile(`.github/workflows/${site.file}`, "utf8"));
				const steps = workflow.getIn(["jobs", site.job, "steps"]);
				assert.ok(isSeq(steps));
				const step: unknown = steps.items.find(
					(item) => isMap(item) && item.get("name") === site.step,
				);
				assert.ok(isMap(step));
				if (site.job === "tag-unchanged-images") {
					assert.equal(step.getIn(["env", "REPOSITORY"]), `\${{ github.repository }}`);
					assert.equal(step.getIn(["env", "REPOSITORY_OWNER"]), `\${{ github.repository_owner }}`);
					assert.equal(step.getIn(["env", "ALIAS_BASE"]), `\${{ inputs.alias_base }}`);
				} else {
					const source = site.job === "build" ? "single-digest" : "digest";
					assert.equal(
						step.getIn(["env", "IMAGE_REF"]),
						`\${{ inputs.registry }}/\${{ inputs.image-name }}@\${{ steps.${source}.outputs.manifest-digest }}`,
					);
				}
				const publication: unknown = steps.items.find(
					(item) => isMap(item) && item.get("name") === "Publish verified image tags",
				);
				if (site.job !== "tag-unchanged-images") {
					assert.ok(isMap(publication));
					assert.ok(steps.items.indexOf(publication) > steps.items.indexOf(step));
					assert.equal(publication.getIn(["env", "IMAGE_REF"]), step.getIn(["env", "IMAGE_REF"]));
				}
				const script = (
					String(step.get("run")) + (isMap(publication) ? String(publication.get("run")) : "")
				)
					.replaceAll(`\${{ github.repository }}`, repository)
					.replaceAll(`\${{ github.repository_owner }}`, owner);
				assert.ok(!script.includes(`\${{`));
				const directory = await mkdtemp(path.join(tmpdir(), "image-verification-"));
				t.after(async () => rm(directory, { recursive: true, force: true }));
				const log = path.join(directory, "calls");
				await writeFile(log, "");
				for (const tool of ["cosign", "gh", "docker"]) {
					const file = path.join(directory, tool);
					await writeFile(
						file,
						`#!/usr/bin/env node
const fs = require('node:fs');
const tool = require('node:path').basename(process.argv[1]);
const args = process.argv.slice(2);
fs.appendFileSync(process.env.CALL_LOG, JSON.stringify([tool, ...args]) + '\\n');
if (tool === 'cosign' && args[0] === 'sign' && process.env.FAILURE === 'signing') process.exit(6);
if (tool === 'cosign' && args[0] === 'verify' && process.env.FAILURE === 'signature') process.exit(7);
if (tool === 'gh' && process.env.FAILURE === 'attestation') process.exit(9);
if (tool === 'docker' && args.includes('create')) {
  if (process.env.FAILURE === 'publication') process.exit(11);
  const index = args.indexOf('--metadata-file');
  if (index !== -1) fs.writeFileSync(args[index + 1], JSON.stringify({
    'containerimage.descriptor': { digest: process.env.FAILURE === 'changedDigest' ? 'sha256:' + 'd'.repeat(64) : process.env.DIGEST }
  }));
}
if (tool === 'docker' && args.includes('inspect')) process.stdout.write(process.env.DIGEST);
`,
					);
					await chmod(file, 0o755);
				}
				const result = spawnSync("bash", ["-e", "-c", script], {
					encoding: "utf8",
					env: {
						...process.env,
						PATH: `${directory}${path.delimiter}${process.env.PATH ?? ""}`,
						CALL_LOG: log,
						FAILURE: failure,
						DIGEST: digest,
						RUNNER_TEMP: directory,
						DOCKER_METADATA_OUTPUT_JSON:
							failure === "invalidMetadata"
								? "{"
								: JSON.stringify({
										tags: failure === "emptyTags" ? [] : [`${image}:commit`, `${image}:ci`],
									}),
						IMAGE_REF: immutable,
						REPOSITORY: repository,
						REPOSITORY_OWNER: owner,
						ALIAS_BASE: "b".repeat(40),
						HEAD_SHA: "c".repeat(40),
						PR_NUMBER: "42",
						WEBAPP_CHANGED: "false",
						APPLICATION_SERVER_CHANGED: "true",
						AGENT_IMAGES_CHANGED: "true",
						POSTGRES_IMAGE_CHANGED: "true",
					},
				});
				const recorded = await readFile(log, "utf8");
				const calls = recorded.trim().split("\n");
				if (failure === "signing") {
					assert.equal(step.get("continue-on-error"), undefined);
					assert.deepEqual(calls, [JSON.stringify(["cosign", "sign", "--yes", immutable])]);
					assert.equal(result.status, exitCode, result.stderr);
					return;
				}
				const expected = [];
				if (site.job === "build") {
					expected.push(["cosign", "sign", "--yes", immutable]);
				}
				if (site.job === "tag-unchanged-images") {
					expected.push([
						"docker",
						"buildx",
						"imagetools",
						"inspect",
						"--format",
						"{{.Manifest.Digest}}",
						`${image}:${"b".repeat(40)}`,
					]);
				}
				expected.push([
					"cosign",
					"verify",
					immutable,
					"--certificate-identity-regexp",
					`^https://github\\.com/${repository}/\\.github/workflows/reusable-docker-build\\.yml@`,
					"--certificate-oidc-issuer",
					"https://token.actions.githubusercontent.com",
					"--certificate-github-workflow-repository",
					repository,
				]);
				if (failure !== "signature") {
					expected.push([
						"gh",
						"attestation",
						"verify",
						`oci://${immutable}`,
						"--bundle-from-oci",
						"--owner",
						owner,
					]);
				}
				if (
					site.job !== "tag-unchanged-images" &&
					failure !== "signature" &&
					failure !== "attestation" &&
					failure !== "emptyTags" &&
					failure !== "invalidMetadata"
				) {
					expected.push([
						"docker",
						"buildx",
						"imagetools",
						"create",
						"--prefer-index=false",
						"--tag",
						`${image}:commit`,
						"--tag",
						`${image}:ci`,
						"--metadata-file",
						path.join(directory, "published-image.json"),
						immutable,
					]);
				}
				if (failure === "none" && site.job === "tag-unchanged-images") {
					expected.push([
						"docker",
						"buildx",
						"imagetools",
						"create",
						"--tag",
						`${image}:${"c".repeat(40)}`,
						"--tag",
						`${image}:pr-42`,
						immutable,
					]);
				}
				assert.deepEqual(
					calls,
					expected.map((args) => JSON.stringify(args)),
				);
				assert.equal(result.status, exitCode, result.stderr);
			},
		);
	}
}

void test("registry-only merge and alias jobs remain checkout-free and signing keeps its workflow identity", async () => {
	for (const site of verificationSites.filter((candidate) => candidate.job !== "build")) {
		const workflow = parseDocument(await readFile(`.github/workflows/${site.file}`, "utf8"));
		const steps = workflow.getIn(["jobs", site.job, "steps"]);
		assert.ok(isSeq(steps));
		for (const step of steps.items) {
			if (isMap(step)) {
				assert.doesNotMatch(String(step.get("uses")), /actions\/checkout@|^\.\//u);
			}
		}
	}
	const workflow = parseDocument(
		await readFile(".github/workflows/reusable-docker-build.yml", "utf8"),
	);
	for (const job of ["build", "merge"]) {
		const steps = workflow.getIn(["jobs", job, "steps"]);
		assert.ok(isSeq(steps));
		const sign: unknown = steps.items.find(
			(item) =>
				isMap(item) &&
				item.get("name") === (job === "build" ? "Sign and verify image" : "Sign image with cosign"),
		);
		assert.ok(isMap(sign));
		assert.match(
			String(sign.get("run")),
			job === "merge" ? /cosign sign --yes --recursive/u : /cosign sign --yes/u,
		);
		if (job === "build") {
			assert.doesNotMatch(String(sign.get("run")), /--recursive/u);
		}
	}
});

for (const source of ["dockerfile", "buildpacks", "merged"] as const) {
	for (const captured of [digest, "invalid", null]) {
		void test(`${source}: ${captured === digest ? "valid" : "invalid or missing"} publication subject`, async (t) => {
			const workflow = parseDocument(
				await readFile(".github/workflows/reusable-docker-build.yml", "utf8"),
			);
			const job = source === "merged" ? "merge" : "build";
			const name =
				source === "merged" ? "Capture manifest digest" : "Capture single-architecture digest";
			const steps = workflow.getIn(["jobs", job, "steps"]);
			assert.ok(isSeq(steps));
			const capture: unknown = steps.items.find((item) => isMap(item) && item.get("name") === name);
			assert.ok(isMap(capture));
			const directory = await mkdtemp(path.join(tmpdir(), "publication-subject-"));
			t.after(async () => rm(directory, { recursive: true, force: true }));
			const output = path.join(directory, "output");
			await writeFile(output, "");
			const descriptor = { "containerimage.descriptor": { digest: captured } };
			await writeFile(
				path.join(directory, source === "merged" ? "merged-image.json" : "single-image.json"),
				JSON.stringify(descriptor),
			);
			const result = spawnSync("bash", ["-e", "-c", String(capture.get("run"))], {
				encoding: "utf8",
				env: {
					...process.env,
					RUNNER_TEMP: directory,
					GITHUB_OUTPUT: output,
					DOCKERFILE_DIGEST: captured ?? "",
					USE_BUILDPACKS: String(source === "buildpacks"),
				},
			});
			assert.equal(result.status === 0, captured === digest, result.stderr);
			assert.equal(
				await readFile(output, "utf8"),
				captured === digest ? `manifest-digest=${digest}\n` : "",
			);
		});
	}
}

void test("canonical tags are withheld until verification for both image builders", async () => {
	const workflow = parseDocument(
		await readFile(".github/workflows/reusable-docker-build.yml", "utf8"),
	);
	for (const job of ["build", "merge"]) {
		const steps = workflow.getIn(["jobs", job, "steps"]);
		assert.ok(isSeq(steps));
		const values = asArray(steps.toJSON(), "steps").map((step) => asRecord(step, "step"));
		const publication = values.findIndex((step) => step.name === "Publish verified image tags");
		const verification = values.findIndex(
			(step) =>
				step.name ===
				(job === "build" ? "Sign and verify image" : "Verify signature + attestation"),
		);
		assert.ok(publication > verification && verification !== -1);
		for (const step of values.slice(0, publication)) {
			if (step.name === "Build and push (Dockerfile)") {
				const inputs = asRecord(step.with, "build inputs");
				assert.equal(
					inputs.tags,
					`\${{ !inputs.publish && inputs.single-arch && steps.meta.outputs.tags || '' }}`,
				);
				assert.match(String(inputs.outputs), /inputs\.publish && format/u);
				assert.match(String(inputs.outputs), /push-by-digest=true/u);
			}
			if (
				step.name === "Prepare single-architecture buildpack index" ||
				step.name === "Create manifest and push"
			) {
				const env = asRecord(step.env, "staging environment");
				assert.match(String(env.STAGING_IMAGE), /github\.run_id.*github\.run_attempt/u);
				assert.doesNotMatch(String(step.run), /DOCKER_METADATA_OUTPUT_JSON/u);
			}
		}
	}
});

const armLeaf = `sha256:${"b".repeat(64)}`;
const buildIndex = `sha256:${"c".repeat(64)}`;
const mergedIndex = `sha256:${"d".repeat(64)}`;
const platformLeaf = (architecture: string, selected: string) => ({
	platform: { os: "linux", architecture },
	digest: selected,
});
const finalLeaves = [platformLeaf("arm64", armLeaf), platformLeaf("amd64", digest)];
for (const scenario of [
	{ name: "Dockerfile index and Buildpacks leaf match the final index", final: finalLeaves },
	{
		name: "changed final leaf",
		final: [platformLeaf("amd64", buildIndex), platformLeaf("arm64", armLeaf)],
		refused: true,
	},
	{ name: "missing final platform", final: [platformLeaf("amd64", digest)], refused: true },
	{
		name: "duplicate final platform",
		final: [...finalLeaves, platformLeaf("amd64", digest)],
		refused: true,
	},
	{
		name: "unexpected real platform",
		final: [...finalLeaves, platformLeaf("386", digest)],
		refused: true,
	},
	{
		name: "source index missing its platform",
		final: finalLeaves,
		source: [platformLeaf("arm64", digest)],
		refused: true,
		beforeCreate: true,
	},
	{
		name: "source index duplicates its platform",
		final: finalLeaves,
		source: [platformLeaf("amd64", digest), platformLeaf("amd64", digest)],
		refused: true,
		beforeCreate: true,
	},
	{
		name: "source leaf has malformed digest",
		final: finalLeaves,
		source: [platformLeaf("amd64", "bad")],
		refused: true,
		beforeCreate: true,
	},
	{
		name: "missing build artifact",
		final: finalLeaves,
		artifact: "missing",
		refused: true,
		beforeCreate: true,
	},
	{
		name: "multiple build digest files",
		final: finalLeaves,
		artifact: "multiple",
		refused: true,
		beforeCreate: true,
	},
	{
		name: "malformed build digest filename",
		final: finalLeaves,
		artifact: "malformed",
		refused: true,
		beforeCreate: true,
	},
	{
		name: "extra artifact directory",
		final: finalLeaves,
		artifact: "extra",
		refused: true,
		beforeCreate: true,
	},
] as const) {
	void test(
		`manifest leaf custody: ${scenario.name}`,
		{ skip: process.platform === "win32" },
		async (t) => {
			const workflow = parseDocument(
				await readFile(".github/workflows/reusable-docker-build.yml", "utf8"),
			);
			const steps = workflow.getIn(["jobs", "merge", "steps"]);
			assert.ok(isSeq(steps));
			const create = steps.items.find(
				(step) => isMap(step) && step.get("name") === "Create manifest and push",
			);
			assert.ok(isMap(create));
			const directory = await mkdtemp(path.join(tmpdir(), "manifest-leaf-custody-"));
			t.after(async () => rm(directory, { recursive: true, force: true }));
			const artifactRoot = path.join(directory, "digests");
			await mkdir(artifactRoot);
			for (const [arch, sourceDigest] of [
				["amd64", buildIndex],
				["arm64", armLeaf],
			] as const) {
				const artifactDirectory = path.join(
					artifactRoot,
					`digests-hephaestus-build-webapp-linux-${arch}`,
				);
				await mkdir(artifactDirectory);
				if ("artifact" in scenario && scenario.artifact === "missing" && arch === "amd64") {
					continue;
				}
				const file =
					"artifact" in scenario && scenario.artifact === "malformed" && arch === "amd64"
						? "bad"
						: sourceDigest.slice(7);
				await writeFile(path.join(artifactDirectory, file), "");
				if ("artifact" in scenario && scenario.artifact === "multiple" && arch === "amd64") {
					await writeFile(path.join(artifactDirectory, digest.slice(7)), "");
				}
			}
			if ("artifact" in scenario && scenario.artifact === "extra") {
				await mkdir(path.join(artifactRoot, "unexpected"));
			}
			const docker = path.join(directory, "docker");
			await writeFile(
				docker,
				String.raw`#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync(process.env.CALL_LOG, JSON.stringify(args) + '\n');
if (args[2] === 'create') {
  const at = args.indexOf('--metadata-file');
  fs.writeFileSync(args[at + 1], JSON.stringify({'containerimage.descriptor': {digest: process.env.MERGED_DIGEST}}));
  process.exit(0);
}
const ref = args[3];
process.stdout.write(ref.endsWith(process.env.MERGED_DIGEST) ? process.env.FINAL_RAW : ref.endsWith(process.env.BUILD_INDEX) ? process.env.SOURCE_RAW : JSON.stringify({schemaVersion: 2, config: {}, layers: []}));
`,
			);
			await chmod(docker, 0o755);
			const callLog = path.join(directory, "calls");
			await writeFile(callLog, "");
			const result = spawnSync("bash", ["-e", "-c", String(create.get("run"))], {
				cwd: artifactRoot,
				encoding: "utf8",
				env: {
					...process.env,
					PATH: `${directory}${path.delimiter}${process.env.PATH ?? ""}`,
					RUNNER_TEMP: directory,
					INPUTS_REGISTRY: "ghcr.io",
					INPUTS_IMAGE_NAME: "hephaestus-build/webapp",
					STAGING_IMAGE: `${image}:ci-test-index`,
					CALL_LOG: callLog,
					BUILD_INDEX: buildIndex,
					MERGED_DIGEST: mergedIndex,
					SOURCE_RAW: JSON.stringify({
						manifests:
							"source" in scenario
								? scenario.source
								: [platformLeaf("amd64", digest), platformLeaf("unknown", buildIndex)],
					}),
					FINAL_RAW: JSON.stringify({
						manifests: [
							...scenario.final,
							{ platform: { os: "unknown", architecture: "unknown" }, digest: buildIndex },
						],
					}),
				},
			});
			assert.equal(result.status === 0, !("refused" in scenario), result.stderr);
			const callText = await readFile(callLog, "utf8");
			const calls = callText
				.trim()
				.split("\n")
				.filter(Boolean)
				.map((line) => asArray(JSON.parse(line), "Docker arguments"));
			const created = calls.find((args) => args[2] === "create");
			assert.equal(created === undefined, "beforeCreate" in scenario);
			if (created !== undefined) {
				assert.deepEqual(created.slice(-2), [`${image}@${buildIndex}`, `${image}@${armLeaf}`]);
				assert.deepEqual(calls.at(-1), [
					"buildx",
					"imagetools",
					"inspect",
					`${image}@${mergedIndex}`,
					"--raw",
				]);
			}
			assert.ok(
				steps.items.indexOf(create) <
					steps.items.findIndex(
						(step) => isMap(step) && step.get("name") === "Generate build provenance attestation",
					),
			);
		},
	);
}
