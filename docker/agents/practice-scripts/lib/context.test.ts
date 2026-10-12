import assert from "node:assert/strict";
import { test } from "node:test";

import { inventoryIssues, readProjectInventory } from "./context.ts";
import { UnratedError, type Evidence } from "./practice-script.ts";

/** Evidence that holds the given records, and fails the read of any name in `failing`. */
function evidence(
	records: Record<string, string>,
	failing: readonly string[] = [],
): Pick<Evidence, "record"> {
	return {
		record: async (name) => {
			if (failing.includes(name)) {
				throw new UnratedError("unavailable", `${name} could not be read`);
			}
			const text = records[name];
			return text === undefined ? { status: "ABSENT" } : { status: "FOUND", text };
		},
	};
}

void test("an inventory issue with an empty title is held, and one without a title or number is not", async () => {
	const inventory = await readProjectInventory(
		evidence({
			"project_inventory.json": JSON.stringify({
				repository: "team/project",
				issues: [{ number: 7, title: "", state: "OPEN" }, { number: 8 }, { title: "No number" }],
			}),
		}),
	);
	const issues = inventoryIssues(inventory);
	assert.deepEqual([...issues.keys()], [7]);
	assert.partialDeepStrictEqual(issues.get(7), { title: "", state: "OPEN" });
});

void test("an inventory that was not captured or is not JSON is null, and a read that fails stops the program", async () => {
	assert.equal(await readProjectInventory(evidence({})), null);
	assert.equal(await readProjectInventory(evidence({ "project_inventory.json": "{" })), null);
	await assert.rejects(readProjectInventory(evidence({}, ["project_inventory.json"])), {
		name: "UnratedError",
		reason: "unavailable",
	});
});
