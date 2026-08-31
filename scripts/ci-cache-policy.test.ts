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
			assert.ok(key.includes("-identity.outputs.hash"));
		}
	});

	await test("Webapp E2E uses the server build caches", () => {
		const javaSetup = actionStep("Set up JDK 21");
		assert.ok(javaSetup.includes('"webapp-e2e"'));
		assert.ok(javaSetup.includes('cache: "maven"'));
		assert.ok(javaSetup.includes("cache-read-only:"));
		assert.ok(javaSetup.includes("cache-jdk: false"));
		assert.ok(actionStep("Restore generated clients").includes('"webapp-e2e"'));
		assert.ok(actionStep("Cache generated clients").includes('"webapp-e2e"'));
	});

	await test("browser consumers share one cache-and-install action", async () => {
		assert.match(
			browserAction,
			/key: \${{ runner\.os }}-playwright-\${{ steps\.playwright\.outputs\.version }}/,
		);
		assert.doesNotMatch(browserAction, /restore-keys:/);
		assert.match(browserAction, /playwright install chromium --with-deps/);
		const workflow = await readFile(".github/workflows/ci-tests.yml", "utf8");
		assert.equal((workflow.match(/uses: \.\/\.github\/actions\/setup-browsers/g) ?? []).length, 2);
	});
});
