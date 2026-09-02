import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { describe, test } from "node:test";

const cacheAction = await readFile(".github/actions/setup-caches/action.yml", "utf8");
const browserAction = await readFile(".github/actions/setup-browsers/action.yml", "utf8");

function actionStep(name: string): string {
	const marker = `    - name: ${name}\n`;
	const start = cacheAction.indexOf(marker);
	assert.ok(start >= 0);
	const end = cacheAction.indexOf("\n    - name:", start + marker.length);
	return cacheAction.slice(start, end < 0 ? undefined : end);
}

await describe("CI cache policy", async () => {
	await test("cache actions use precomputed identities", () => {
		const cacheKeys = cacheAction.split("\n").filter((line) => line.trimStart().startsWith("key:"));

		assert.notEqual(cacheKeys.length, 0);
		for (const key of cacheKeys) {
			assert.ok(!key.includes("hashFiles("));
			assert.ok(key.includes("steps.identity.outputs."));
		}
	});

	await test("Maven caches are written only by reactor jobs on the default branch", async () => {
		assert.ok(actionStep("Set up JDK 21").includes("cache-jdk: false"));
		// A pom change must fall back to the newest default-branch cache, not a full download.
		const restore = actionStep("Restore Maven dependencies");
		assert.match(restore, /actions\/cache\/restore@/);
		assert.match(restore, /restore-keys:[\s\S]*?-maven-\s*$/m);
		assert.ok(restore.includes("steps.identity.outputs.save != 'true'"));
		const save = actionStep("Cache Maven dependencies");
		assert.ok(save.includes("steps.identity.outputs.save == 'true'"));
		assert.ok(save.includes("inputs.cache-type == 'application-server-reactor'"));
		const build = await readFile(".github/workflows/ci-build.yml", "utf8");
		const e2e = build.slice(
			build.indexOf("\n  webapp-e2e:"),
			build.indexOf("\n  application-server-image:"),
		);
		assert.match(e2e, /uses: actions\/setup-java@/);
		assert.doesNotMatch(e2e, /setup-caches/);
	});

	await test("browser consumers share one cache-and-install action", () => {
		assert.match(
			browserAction,
			/key: \${{ runner\.os }}-playwright-\${{ steps\.playwright\.outputs\.version }}/,
		);
		assert.doesNotMatch(browserAction, /restore-keys:/);
		assert.match(browserAction, /playwright install chromium --with-deps/);
	});
});
