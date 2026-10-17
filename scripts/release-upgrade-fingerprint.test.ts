import assert from "node:assert/strict";
import { test } from "node:test";

import { peopleLayout, preservationSql } from "./lib/release-upgrade-fingerprint.ts";

void test("native comparison requires exact preserved ids and rejects SQL-shaped ids", () => {
	assert.throws(() => preservationSql("native"), /preserved people ids/u);
	assert.throws(
		() => preservationSql("native", { accounts: ["1); DROP TABLE account"], identities: [] }),
		/decimal/u,
	);
});

void test("people layout is decided by the exact table set in any order", () => {
	assert.equal(peopleLayout("workspace_membership\nuser\nidentity_link"), "legacy");
	assert.equal(peopleLayout("user\nmembership\nidentity"), "native");
});

void test("people layout refuses a mixed, partial, or empty table set", () => {
	for (const tables of [
		"",
		"identity\nidentity_link\nmembership\nuser\nworkspace_membership",
		"identity_link\nworkspace_membership",
		"identity\nmembership",
		"identity\nuser",
		"identity\nuser\nworkspace_membership",
	]) {
		assert.throws(() => peopleLayout(tables), /Unknown or ambiguous people layout/u, tables);
	}
});
