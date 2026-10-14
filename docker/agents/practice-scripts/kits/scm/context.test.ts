import assert from "node:assert/strict";
import { test } from "node:test";

import { UnratedError, type Evidence } from "../../lib/core/practice-script.ts";
import { readProjectInventory, repositoryIssues } from "./context.ts";

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
				issues: [
					{
						number: 7,
						title: "",
						state: "OPEN",
						repository: "team/project",
						repository_id: 1,
						provider_id: 1,
					},
					{ number: 8 },
					{ title: "No number" },
				],
			}),
		}),
	);
	const issues = repositoryIssues(inventory, { repositoryId: 1 });
	assert.deepEqual(
		issues?.map((issue) => issue.number),
		[7],
	);
	assert.partialDeepStrictEqual(issues[0], { title: "", state: "OPEN" });
});

void test("an absent inventory is null, and malformed JSON or a failed read stops the program", async () => {
	assert.equal(await readProjectInventory(evidence({})), null);
	await assert.rejects(readProjectInventory(evidence({ "project_inventory.json": "{" })), {
		name: "UnratedError",
		reason: "unavailable",
		message: "project_inventory.json is not valid JSON",
	});
	await assert.rejects(readProjectInventory(evidence({}, ["project_inventory.json"])), {
		name: "UnratedError",
		reason: "unavailable",
	});
});
