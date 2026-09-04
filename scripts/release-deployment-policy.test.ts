import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { releaseSignerIdentity, releaseSignerRepository } from "./lib/release-signer.ts";
import { SMOKE_HOSTNAME } from "./prepare-host-smoke-env.ts";

const release = readFileSync(".github/workflows/release.yml", "utf8");
const deployment = readFileSync(".github/workflows/deploy-locked-compose.yml", "utf8");
const promotion = readFileSync(".github/workflows/promote.yml", "utf8");
const reconciler = readFileSync("scripts/reconcile-deployment.ts", "utf8");
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

await test("a deployment starts the broker before the stacks that talk to it", () => {
	const configured = /^\s+STACKS: (.+)$/m.exec(deployment)?.[1];
	assert.ok(configured, "the deploy job must declare the stacks and their order in STACKS");
	// Every stack list the expression can yield: one per environment shape it selects between.
	const orders = [...configured.matchAll(/&& '([a-z ]+)' \|\| '([a-z ]+)'/g)].flatMap((match) => [
		String(match[1]).split(" "),
		String(match[2]).split(" "),
	]);
	assert.ok(orders.length > 0);
	for (const stacks of orders) {
		// The application server runs the Liquibase migration the webhook runtime in core reads.
		assert.ok(
			stacks.indexOf("app") < stacks.indexOf("core"),
			`${stacks.join(" ")} starts the webhook runtime before the migration that feeds it`,
		);
		// The edge comes last, so it never routes to a stack that is still starting.
		if (stacks.includes("proxy")) assert.equal(stacks.at(-1), "proxy");
	}

	const script = deployment.slice(deployment.indexOf("envs: STACKS"));
	// The broker is recreated before any stack, and only where this environment runs core, so an
	// environment that leaves core out never deploys a broker it did not render.
	const broker = script.search(/\*" core "\*\)/);
	const stacks = script.indexOf("for stack in $STACKS");
	assert.ok(
		broker >= 0,
		"the broker must be guarded on core being one of this deployment's stacks",
	);
	assert.ok(stacks > broker, "the broker must be recreated before any stack starts");
	assert.match(script.slice(broker, stacks), /--force-recreate nats-server/);
});

const upgrade = readFileSync(".github/workflows/release-upgrade.yml", "utf8");

await test("release publication requires the seeded upgrade gate", () => {
	assert.match(
		release,
		/publish-release:\n\s+needs: \[release, tag-images, upgrade-test, supported-host-smoke\]/,
	);
	assert.match(release, /previous-version: \$\{\{ needs\.release\.outputs\.previous_version \}\}/);
	assert.match(upgrade, /INPUT_PREVIOUS_VERSION: \$\{\{ inputs\.previous-version \}\}/);
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

await test("verification identity is the release's own: run context now, the map for history", () => {
	const rescan = readFileSync(".github/workflows/rescan-release-images.yml", "utf8");
	const prepareLock = readFileSync("scripts/prepare-release-lock.ts", "utf8");
	const derivedIdentity =
		/\$\{\{ github\.server_url \}\}\/\$\{\{ github\.repository \}\}\/\.github\/workflows\/release\.yml@refs\/heads\/main/;

	assert.match(release, derivedIdentity);
	assert.match(rescan, /resolve-release-identity\.ts.*certificate-identity/);
	assert.match(deployment, /resolve-release-identity\.ts.*certificate-identity/);
	assert.match(prepareLock, /releaseCertificateIdentity\(release, process\.env\)/);
	assert.match(prepareLock, /releaseRepository\(release, process\.env\)/);
	for (const contents of [release, deployment, rescan, prepareLock]) {
		assert.doesNotMatch(contents, /certificate-identity[^\n]*\n?[^\n]*ls1intum\/Hephaestus/);
		assert.doesNotMatch(
			contents,
			/certificate-identity[^\n]*\n?[^\n]*hephaestus-build\/Hephaestus/,
		);
	}
	assert.match(
		deployment,
		/EXPECTED_SIGNER_REPOSITORY: \$\{\{ inputs\.expected-signer-repository \}\}/,
	);
	assert.match(
		deployment,
		/"\$\{SERVER_URL\}\/\$\{EXPECTED_SIGNER_REPOSITORY\}\/\.github\/workflows\/release\.yml@refs\/heads\/main"/,
	);
});

await test("promotion accepts only immutable releases and the host supplies the verifier", () => {
	assert.match(promotion, /--json isDraft,isImmutable/);
	assert.match(promotion, /\.isImmutable/);
	assert.match(reconciler, /join\(config\.checkout, "scripts\/prepare-release-lock\.ts"\)/);
	assert.doesNotMatch(reconciler, /join\(releaseTree, "scripts\/prepare-release-lock\.ts"\)/);
});

await test("derived signer identity matches today's literals in the canonical repository", () => {
	const canonicalRun = {
		CI: "true",
		GITHUB_SERVER_URL: "https://github.com",
		GITHUB_REPOSITORY: "hephaestus-build/Hephaestus",
	};
	assert.equal(releaseSignerRepository(canonicalRun), "hephaestus-build/Hephaestus");
	assert.equal(
		releaseSignerIdentity(canonicalRun),
		"https://github.com/hephaestus-build/Hephaestus/.github/workflows/release.yml@refs/heads/main",
	);
	assert.equal(
		releaseSignerIdentity({}),
		"https://github.com/hephaestus-build/Hephaestus/.github/workflows/release.yml@refs/heads/main",
	);
	assert.throws(() => releaseSignerRepository({ CI: "true" }), /GITHUB_REPOSITORY/);
});

await test("release publication requires native smoke tests for every supported host", () => {
	const smokeGate =
		release.match(/ {2}supported-host-smoke:[\s\S]*?\n {2}publish-release:/)?.[0] ?? "";

	assert.match(smokeGate, /architecture: amd64\n\s+runner: ubuntu-24\.04/);
	assert.match(smokeGate, /architecture: arm64\n\s+runner: ubuntu-24\.04-arm/);
	assert.match(smokeGate, /up -d --wait --wait-timeout 600/);
	assert.match(smokeGate, /prepare-release-lock\.ts/);
	assert.match(smokeGate, /contents: write/);
	assert.match(release, /gh release upload "\$TAG_NAME" host-smoke\/\*\.json/);
});

await test("the release smoke reaches the installation by the name the installer answers with", () => {
	// Traefik routes on the hostname the installer answered with, so a rename in the script has to
	// reach this curl or the ingress check fails for the first time at a release.
	assert.ok(
		release.includes(`--resolve ${SMOKE_HOSTNAME}:443:127.0.0.1`),
		`release.yml must resolve ${SMOKE_HOSTNAME} to the loopback`,
	);
	assert.ok(
		release.includes(`https://${SMOKE_HOSTNAME}/`),
		`release.yml must request the installation at ${SMOKE_HOSTNAME}`,
	);
});
