import assert from "node:assert/strict";
import { test } from "node:test";

import { assertCoverage, testIdentities } from "./verify-server-test-selection.ts";

void test("reads skipped discovery identities including parameterized cases", () => {
	assert.deepEqual(
		testIdentities(
			'<testsuite><testcase classname="Example" name="case[1]"><skipped/></testcase><testcase classname="Example" name="case[2]"><skipped/></testcase></testsuite>',
		),
		new Set(["Example#case[1]", "Example#case[2]"]),
	);
	assert.throws(() => testIdentities("<testsuite>"));
	assert.throws(() => testIdentities('<testsuite><testcase name="missing class"/></testsuite>'));
});
void test("rejects missing tests, overlapping shards, unexpected tests and empty discoveries", () => {
	const inventory = new Set(["a", "b"]);
	assert.doesNotThrow(() => assertCoverage(inventory, [new Set(["a"]), new Set(["b"])], true));
	assert.throws(() => assertCoverage(inventory, [new Set(["a"])], true));
	assert.throws(() => assertCoverage(inventory, [inventory, new Set(["b"])], true));
	assert.throws(() => assertCoverage(inventory, [new Set(["a", "b", "c"])], true));
	assert.throws(() => assertCoverage(new Set(), [new Set()], true));
	assert.throws(() => assertCoverage(inventory, [inventory, new Set()], true));
	assert.doesNotThrow(() => assertCoverage(inventory, [inventory, new Set(["b"])], false));
});

void test("requires every integration shard to own a non-empty, disjoint part of the tier", () => {
	const integration = new Set(["Provider#a", "Startup#b", "Application#c"]);
	const providers = new Set(["Provider#a"]);
	const runtimeAndStartup = new Set(["Startup#b"]);
	const application = new Set(["Application#c"]);
	assert.doesNotThrow(() =>
		assertCoverage(integration, [providers, runtimeAndStartup, application], true),
	);
	const empty = [providers, new Set<string>(), application];
	assert.throws(() => assertCoverage(integration, empty, true), /contains no tests/u);
	const omitted = [providers, runtimeAndStartup, providers];
	assert.throws(() => assertCoverage(integration, omitted, true), /omitted/u);
	const overlapping = [providers, runtimeAndStartup, new Set(["Application#c", "Provider#a"])];
	assert.throws(() => assertCoverage(integration, overlapping, true), /overlap/u);
});

void test("rejects executed tests as inventory evidence", () => {
	for (const outcome of ["", '<failure message="Failed assertion"/>']) {
		assert.throws(() =>
			testIdentities(
				`<testsuite><testcase classname="Example" name="executed">${outcome}</testcase></testsuite>`,
			),
		);
	}
});
