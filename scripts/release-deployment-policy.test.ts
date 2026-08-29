import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const release = readFileSync(".github/workflows/release.yml", "utf8");
const deployment = readFileSync(".github/workflows/deploy-locked-compose.yml", "utf8");

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

	assert.match(release, /publish-release:\n\s+needs: \[release, tag-images, upgrade-test\]/);
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
	const upgradeGate = release.match(/ {2}upgrade-test:[\s\S]*?\n {2}publish-release:/)?.[0] ?? "";
	assert.doesNotMatch(upgradeGate, /secrets: inherit/);
});
