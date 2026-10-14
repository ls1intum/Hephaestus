import assert from "node:assert/strict";
import { readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { test } from "node:test";
import { requested, scan } from "../../run-bundled.ts";
import { stagePracticeScripts } from "../../stage.ts";
import { practiceInputsSchema } from "./contract.ts";
import { InputReader, parseInputs } from "./script-inputs.ts";

void test("the host stages and reads one v3 input record for each assigned practice", async () => {
	const stage = await stagePracticeScripts({ fixture: null });
	try {
		assert.deepEqual(await readdir(stage.practices), ["fixture.inputs.json"]);
		const read = await new InputReader({ practicesDir: stage.practices }).read("fixture");
		assert.ok("inputs" in read);
		assert.equal(read.inputs.contractVersion, 3);
		assert.equal(read.inputs.practice.versionId, "1");
		assert.equal(read.inputs.review.origin, "REQUEST");
	} finally {
		await rm(stage.root, { recursive: true, force: true });
	}
});

void test("core validates kind grammar without a closed domain list and preserves bigint ids", () => {
	const inputs = requested("new_domain.new_item");
	inputs.scope.item.id = "9007199254740993";
	assert.equal(practiceInputsSchema.parse(inputs).scope.item.id, "9007199254740993");
	assert.equal(practiceInputsSchema.safeParse({ ...inputs, kind: "bad" }).success, false);
});

void test("core refuses retired fields and a different assigned item kind", () => {
	const inputs = requested();
	for (const key of ["work", "cause", "subject"]) {
		assert.equal(practiceInputsSchema.safeParse({ ...inputs, [key]: {} }).success, false);
	}
	assert.equal(
		practiceInputsSchema.safeParse({
			...inputs,
			scope: { ...inputs.scope, item: { id: "1", kind: "scm.issue" } },
		}).success,
		false,
	);
	assert.equal(practiceInputsSchema.safeParse({ ...inputs, contractVersion: 2 }).success, false);
});

void test("scan inputs are developerless and cannot carry a baseVersion", () => {
	const inputs = scan("src/main.ts");
	assert.equal(practiceInputsSchema.safeParse(inputs).success, true);
	assert.equal(
		practiceInputsSchema.safeParse({ ...inputs, about: { role: "AUTHOR", developerId: "1" } })
			.success,
		false,
	);
	assert.equal(
		practiceInputsSchema.safeParse({ ...inputs, scope: { ...inputs.scope, baseVersion: "old" } })
			.success,
		false,
	);
});

void test("missing, malformed and mismatched input files cannot start a script", async () => {
	const stage = await stagePracticeScripts({ fixture: null }, {});
	try {
		const reader = new InputReader({ practicesDir: stage.practices });
		assert.ok("error" in (await reader.read("fixture")));
		await writeFile(path.join(stage.practices, "fixture.inputs.json"), "{");
		assert.ok("error" in (await reader.read("fixture")));
		assert.ok("error" in parseInputs("different", requested()));
	} finally {
		await rm(stage.root, { recursive: true, force: true });
	}
});
