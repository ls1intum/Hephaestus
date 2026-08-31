import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { validateInventory } from "./check-release-image-inventory.ts";

await test("repository release builds are covered by the evidence inventory", () => {
	const inventory = JSON.parse(readFileSync("security/release-images.json", "utf8")) as unknown;
	const workflow = readFileSync(".github/workflows/ci-docker-build.yml", "utf8");
	assert.deepEqual(validateInventory(inventory, workflow), []);
});

await test("rejects missing and duplicate inventory entries", () => {
	assert.deepEqual(
		validateInventory({ images: ["server"], upstream: [] }, 'image-name: "hephaestus-build/new"'),
		["new"],
	);
	assert.throws(
		() => validateInventory({ images: ["server", "server"], upstream: [] }, ""),
		/duplicates/,
	);
	assert.throws(() => validateInventory({ images: [], upstream: [{}] }, ""), /malformed upstream/);
});
