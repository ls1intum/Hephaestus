import assert from "node:assert/strict";
import { test } from "node:test";
import {
	folderCitationIndex,
	citableTextIndex,
} from "../../../main/resources/agent/pi-folder-index.ts";

await test("the full folder remains citable when a primary readiness source is absent", () => {
	const result = folderCitationIndex({
		sources: [
			{
				kind: "outline.documents",
				citation: { side: "FORBIDDEN", commit: "FORBIDDEN" },
				state: { availability: "UNAVAILABLE" },
			},
		],
		files: [
			{ kind: "outline.documents", file: { path: "context/docs/engineering/design.md" } },
			{ kind: "scm.repository.tree", file: { path: "repos/42/.git/HEAD" } },
		],
	});
	assert.equal(
		result.sourceFileSources.get("context/docs/engineering/design.md"),
		"outline.documents",
	);
	assert.equal(result.sourceFileSources.get("repos/42/.git/HEAD"), "scm.repository.tree");
	assert.equal(result.availableSourceKinds.has("outline.documents"), true);
});

await test("missing, duplicate and unsafe folder proofs fail closed", () => {
	assert.throws(() => folderCitationIndex({ sources: [] }), /expected sources and files/u);
	const sourceFile = { kind: "outline.documents", file: { path: "context/docs/c/d.md" } };
	assert.throws(
		() => folderCitationIndex({ sources: [], files: [sourceFile, sourceFile] }),
		/duplicate/u,
	);
	for (const path of [
		"../foreign",
		"/foreign",
		"context/../foreign",
		String.raw`context\foreign`,
	]) {
		assert.throws(
			() => folderCitationIndex({ sources: [], files: [{ kind: "x", file: { path } }] }),
			/invalid/u,
		);
	}
});

await test("composed views are readable but only canonical records are citable", () => {
	const result = folderCitationIndex({
		sources: [],
		files: [
			{ kind: "review.problems", file: { path: "inputs/history/problems.json" } },
			{
				kind: "workspace.project-inventory",
				file: { path: "context/project_inventory.json" },
			},
			{ kind: "review.problems", file: { path: "context/people/42/problems.jsonl" } },
		],
	});
	assert.equal(result.sourceFileSources.has("inputs/history/problems.json"), false);
	assert.equal(result.sourceFileSources.has("context/project_inventory.json"), false);
	assert.equal(result.sourceFileSources.has("context/people/42/problems.jsonl"), true);
});

await test("logical captured texts preserve their digest, staged path and opaque version", () => {
	assert.deepEqual(citableTextIndex({}), []);
	for (const invalid of [null, {}, "text"]) {
		assert.throws(() => citableTextIndex({ citableTexts: invalid }), /citableTexts array/u);
	}
	const text = {
		source: "custom.text",
		path: "thread/message",
		side: "NEW",
		commit: "opaque:v1",
		stagedPath: "context/message.txt",
		sha256: "a".repeat(64),
	};
	assert.deepEqual(citableTextIndex({ citableTexts: [text] }), [text]);
	for (const invalid of [
		{ ...text, stagedPath: "../foreign" },
		{ ...text, sha256: "bad" },
		{ ...text, side: "MIDDLE" },
	]) {
		assert.throws(() => citableTextIndex({ citableTexts: [invalid] }), /invalid citable text/u);
	}
});
