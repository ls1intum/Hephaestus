import assert from "node:assert/strict";
import { test } from "node:test";
import { fileRecordSchema, repositoryRecordSchema } from "./records.ts";

void test("SCM records preserve bigint ids and pinned Git identities", () => {
	const repository = {
		repositoryId: "9223372036854775807",
		providerId: "9007199254740993",
		commitId: "a".repeat(64),
	};
	assert.deepEqual(repositoryRecordSchema.parse(repository), repository);
	const file = { ...repository, path: "src/a.ts", blobId: "b".repeat(40) };
	assert.deepEqual(fileRecordSchema.parse(file), file);
	for (const changed of [
		{ ...file, repositoryId: 1 },
		{ ...file, blobId: "branch" },
		{ ...file, cause: {} },
	]) {
		assert.equal(fileRecordSchema.safeParse(changed).success, false);
	}
});
