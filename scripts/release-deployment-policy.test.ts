import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { releaseSignerIdentity, releaseSignerRepository } from "./lib/release-signer.ts";

const release = readFileSync(".github/workflows/release.yml", "utf8");
const deployment = readFileSync(".github/workflows/deploy-locked-compose.yml", "utf8");
const upgradeDrill = readFileSync("scripts/release-upgrade-test.ts", "utf8");

await test("release promotion deploys the complete topology by signed tag", () => {
	assert.match(release, /image-tag: \$\{\{ needs\.release\.outputs\.tag_name \}\}/);
	assert.doesNotMatch(release, /'image-tag': '\$\{\{ needs\.release\.outputs\.version \}\}'/);
	for (const stack of ["proxy", "core", "app"])
		assert.match(deployment, new RegExp(`render ${stack} docker/compose\\.${stack}\\.yaml`));
});

await test("deployment uses Compose metadata, waits for readiness, and preserves rollback images", () => {
	assert.match(deployment, /config --variables --format json/);
	assert.match(deployment, /--wait --wait-timeout 600/);
	assert.doesNotMatch(deployment, /docker image prune/);
});

await test("release publication requires the seeded upgrade gate", () => {
	const upgrade = readFileSync(".github/workflows/release-upgrade.yml", "utf8");

	assert.match(
		release,
		/publish-release:\n\s+needs: \[release, tag-images, upgrade-test, supported-host-smoke\]/,
	);
	assert.match(
		release,
		/candidate-application-image: .*@\$\{\{ needs\.tag-images\.outputs\.application-server-digest \}\}/,
	);
	assert.match(
		release,
		/postgres-image: .*@\$\{\{ needs\.tag-images\.outputs\.postgres-digest \}\}/,
	);
	assert.match(release, /candidate-source-sha: \$\{\{ needs\.release\.outputs\.sha \}\}/);
	assert.match(upgrade, /schedule:/);
	assert.match(
		upgrade,
		/ref: \$\{\{ inputs\.candidate-source-sha \|\| inputs\.candidate-sha \|\| github\.sha \}\}/,
	);
	const upgradeGate =
		release.match(/ {2}upgrade-test:[\s\S]*?\n {2}supported-host-smoke:/)?.[0] ?? "";
	assert.doesNotMatch(upgradeGate, /secrets: inherit/);
});

await test("release upgrade runs the server topology without optional infrastructure", () => {
	assert.match(upgradeDrill, /"HEPHAESTUS_RUNTIME_WORKER_ENABLED=false"/);
	assert.match(upgradeDrill, /"HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED=false"/);
	assert.match(upgradeDrill, /"HEPHAESTUS_SYNC_NATS_ENABLED=false"/);
	assert.doesNotMatch(upgradeDrill, /"NATS_ENABLED=false"/);
});

await test("signing and verification identity derives from the run context", () => {
	const rescan = readFileSync(".github/workflows/rescan-release-images.yml", "utf8");
	const prepareLock = readFileSync("scripts/prepare-release-lock.ts", "utf8");
	const derivedIdentity =
		/\$\{\{ github\.server_url \}\}\/\$\{\{ github\.repository \}\}\/\.github\/workflows\/release\.yml@refs\/heads\/main/;

	assert.match(release, derivedIdentity);
	assert.match(rescan, derivedIdentity);
	assert.match(prepareLock, /releaseSignerIdentity\(process\.env\)/);
	assert.match(prepareLock, /releaseSignerRepository\(process\.env\)/);
	// No verify step may pin a repository slug: after a repository transfer, the run's
	// own identity is the one the release workflow signs with (issue #1599).
	for (const contents of [release, deployment, rescan, prepareLock])
		assert.doesNotMatch(contents, /certificate-identity[^\n]*\n?[^\n]*ls1intum\/Hephaestus/);
	// Deploys verify locks that may predate a repository transfer, so the expected
	// signer stays overridable while defaulting to the run's own repository.
	assert.match(
		deployment,
		/EXPECTED_SIGNER_REPOSITORY: \$\{\{ inputs\.expected-signer-repository \|\| github\.repository \}\}/,
	);
	assert.match(
		deployment,
		/"\$\{SERVER_URL\}\/\$\{EXPECTED_SIGNER_REPOSITORY\}\/\.github\/workflows\/release\.yml@refs\/heads\/main"/,
	);
});

await test("derived signer identity matches today's literals in the canonical repository", () => {
	const canonicalRun = {
		CI: "true",
		GITHUB_SERVER_URL: "https://github.com",
		GITHUB_REPOSITORY: "ls1intum/Hephaestus",
	};
	assert.equal(releaseSignerRepository(canonicalRun), "ls1intum/Hephaestus");
	assert.equal(
		releaseSignerIdentity(canonicalRun),
		"https://github.com/ls1intum/Hephaestus/.github/workflows/release.yml@refs/heads/main",
	);
	// The documented operator flow (docs/admin/install.mdx) runs outside CI and keeps
	// the canonical fallback…
	assert.equal(
		releaseSignerIdentity({}),
		"https://github.com/ls1intum/Hephaestus/.github/workflows/release.yml@refs/heads/main",
	);
	// …but CI must never silently verify against a repository it is not running in.
	assert.throws(() => releaseSignerRepository({ CI: "true" }), /GITHUB_REPOSITORY/);
});

await test("release publication requires native smoke tests for every supported host", () => {
	const smokeGate =
		release.match(/ {2}supported-host-smoke:[\s\S]*?\n {2}publish-release:/)?.[0] ?? "";

	assert.match(smokeGate, /architecture: amd64\n\s+runner: ubuntu-24\.04/);
	assert.match(smokeGate, /architecture: arm64\n\s+runner: ubuntu-24\.04-arm/);
	assert.match(smokeGate, /up -d --wait --wait-timeout 600/);
	assert.match(smokeGate, /prepare-release-lock\.ts/);
	// Draft releases are visible only to tokens with push access; a read-only token cannot
	// download the still-draft release lock and would fail every smoke run.
	assert.match(smokeGate, /contents: write/);
	assert.match(release, /gh release upload "\$TAG_NAME" host-smoke\/\*\.json/);
});
