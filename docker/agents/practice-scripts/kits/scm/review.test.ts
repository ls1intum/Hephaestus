import assert from "node:assert/strict";
import { test } from "node:test";

import { UnratedError, type Evidence } from "../../lib/core/practice-script.ts";
import {
	readGeneralComments,
	readLinkedWorkItemCapture,
	readReviewComments,
	readReviewThreads,
} from "./review.ts";

const evidence = (value: unknown): Pick<Evidence, "record"> => ({
	record: async () => ({ status: "FOUND", text: JSON.stringify(value) }),
});

const readers = [
	{
		name: "linked_work_items.json",
		read: readLinkedWorkItemCapture,
		empty: { workItems: [], unresolvedReferences: [] },
		result: { items: [], unresolved: [] },
		invalid: [
			[],
			{},
			{ workItems: null },
			{ workItems: [null] },
			{ workItems: [{ number: "1", title: "Issue", body: "" }] },
			{ workItems: [{ number: 1, title: "Issue" }] },
			{ workItems: [{ number: 1, title: "Issue", body: "", subIssuesSource: "everything" }] },
			{ workItems: [], unresolvedReferences: ["1"] },
		],
	},
	{
		name: "comments.json",
		read: readReviewComments,
		empty: [],
		result: [],
		invalid: [
			{},
			{ comments: [] },
			[null],
			[{ path: "src/a.ts" }],
			[{ path: "src/a.ts", body: 1 }],
		],
	},
	{
		name: "general_comments.json",
		read: readGeneralComments,
		empty: { comments: [] },
		result: [],
		invalid: [
			[],
			{},
			{ comments: null },
			{ comments: [null] },
			{ comments: [{}] },
			{ comments: [{ body: "Ask", bot: "false" }] },
		],
	},
	{
		name: "review_threads.json",
		read: readReviewThreads,
		empty: { threads: [], reviewDecisions: [] },
		result: { threads: [], decisions: [] },
		invalid: [
			[],
			{},
			{ threads: [], reviewDecisions: {} },
			{ threads: [null], reviewDecisions: [] },
			{ threads: [{}], reviewDecisions: [] },
			{ threads: [{ state: "RESOLVED", line: "7" }], reviewDecisions: [] },
			{ threads: [], reviewDecisions: [{}] },
			{ threads: [], reviewDecisions: [{ state: "APPROVED", author: false }] },
		],
	},
];

for (const { name, read, empty, result, invalid } of readers) {
	void test(`${name} distinguishes absent capture from measured empty data`, async () => {
		assert.equal(await read({ record: async () => ({ status: "ABSENT" }) }), null);
		assert.deepEqual(await read(evidence(empty)), result);
	});
	void test(`${name} rejects wrong envelopes and malformed entries as unavailable`, async () => {
		for (const value of [null, true, "record", ...invalid]) {
			await assert.rejects(read(evidence(value)), (error: unknown) => {
				assert.ok(error instanceof UnratedError);
				assert.equal(error.reason, "unavailable");
				assert.ok(error.message.includes(name));
				return true;
			});
		}
	});
	void test(`${name} rejects malformed JSON as unavailable`, async () => {
		await assert.rejects(
			read({ record: async () => ({ status: "FOUND", text: "{" }) }),
			(error: unknown) => {
				assert.ok(error instanceof UnratedError);
				assert.equal(error.reason, "unavailable");
				assert.equal(error.message, `${name} is not valid JSON`);
				return true;
			},
		);
	});
}

void test("comment projection preserves captured body lines and omitted provider fields", async () => {
	const record = {
		record: async () => ({
			status: "FOUND" as const,
			text: '[\n  {\n    "path": "src/a.ts",\n    "body": "Please fix this"\n  }\n]',
		}),
	};
	assert.deepEqual(await readReviewComments(record), [
		{
			path: "src/a.ts",
			body: "Please fix this",
			recordLine: 4,
			outdated: false,
			bot: false,
			id: undefined,
			thread: undefined,
			inReplyTo: undefined,
			line: undefined,
			side: undefined,
			author: undefined,
			createdAt: undefined,
		},
	]);
});
