import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import { test } from "node:test";

import { z } from "zod";

import { threadRecordSchema } from "./chat/index.ts";
import { documentRecordSchema } from "./docs/index.ts";
import {
	fileRecordSchema,
	issueRecordSchema,
	pullRequestRecordSchema,
	repositoryRecordSchema,
} from "./scm/records.ts";

const CONTRACTS = path.join(
	import.meta.dirname,
	"../../../../server/application/src/main/resources/contracts/items",
);

const PAIRS = [
	["chat.thread.schema.json", threadRecordSchema],
	["docs.document.schema.json", documentRecordSchema],
	["scm/pull_request.schema.json", pullRequestRecordSchema],
	["scm/pull_request_review.schema.json", pullRequestRecordSchema],
	["scm/issue.schema.json", issueRecordSchema],
	["scm/repository.schema.json", repositoryRecordSchema],
	["scm/file.schema.json", fileRecordSchema],
] as const;

const contractSchema = z.object({
	properties: z.record(z.string(), z.unknown()),
	required: z.array(z.string()).default([]),
});

for (const [file, schema] of PAIRS) {
	void test(`the kit reads ${file} with the server's fields, required where the server requires them`, () => {
		const contract = contractSchema.parse(
			JSON.parse(readFileSync(path.join(CONTRACTS, file), "utf8")),
		);
		const shape: Readonly<Record<string, z.ZodType>> = schema.shape;
		const fields = Object.keys(shape).toSorted();
		assert.deepEqual(fields, Object.keys(contract.properties).toSorted());
		const required = fields.filter((field) => shape[field]?.safeParse(undefined).success === false);
		assert.deepEqual(required, contract.required.toSorted());
	});
}
