import { describe, expect, test } from "bun:test";

const cacheAction = await Bun.file(".github/actions/setup-caches/action.yml").text();

function actionStep(name: string): string {
	const marker = `    - name: ${name}\n`;
	const start = cacheAction.indexOf(marker);
	expect(start).toBeGreaterThanOrEqual(0);
	const end = cacheAction.indexOf("\n    - name:", start + marker.length);
	return cacheAction.slice(start, end < 0 ? undefined : end);
}

describe("CI cache policy", () => {
	test("cache actions use precomputed identities", () => {
		const cacheKeys = cacheAction.split("\n").filter((line) => line.trimStart().startsWith("key:"));

		expect(cacheKeys).not.toHaveLength(0);
		for (const key of cacheKeys) {
			expect(key).not.toContain("hashFiles(");
			expect(key).toContain("-identity.outputs.hash");
		}
	});

	test("Webapp E2E uses Maven caches but not the Storybook browser cache", () => {
		const javaSetup = actionStep("Set up JDK 21");
		expect(javaSetup).toContain('"webapp-e2e"');
		expect(javaSetup).toContain('cache: "maven"');
		expect(javaSetup).toContain("cache-read-only:");
		expect(javaSetup).toContain("cache-jdk: false");
		expect(actionStep("Restore generated clients")).toContain('"webapp-e2e"');
		expect(actionStep("Cache generated clients")).toContain('"webapp-e2e"');
		expect(actionStep("Cache Playwright browsers")).not.toContain("webapp-e2e");
	});
});
