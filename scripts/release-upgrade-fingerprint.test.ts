import assert from "node:assert/strict";
import { test } from "node:test";

import { preservationSql } from "./lib/release-upgrade-fingerprint.ts";

void test("candidate comparison requires exact released ids and rejects SQL-shaped ids", () => {
	assert.throws(() => preservationSql("candidate"), /released people ids/u);
	assert.throws(
		() => preservationSql("candidate", { accounts: ["1); DROP TABLE account"], identities: [] }),
		/decimal/u,
	);
});
