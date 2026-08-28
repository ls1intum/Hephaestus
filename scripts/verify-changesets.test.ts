import assert from "node:assert/strict";
import test from "node:test";

import { verifyChangesets } from "./verify-changesets.ts";

const status = (releases: unknown[], summary: string) => ({
	changesets: [{ id: "note", releases, summary }],
});
const rootRelease = (type = "patch") => [{ name: "hephaestus", type }];

void test("accepts root releases and explained opt-outs", () => {
	assert.doesNotThrow(() =>
		verifyChangesets(status(rootRelease(), "Fixes sign-in."), [".changeset/note.md"]),
	);
	assert.doesNotThrow(() =>
		verifyChangesets(status([], "No release note: CI only."), [".changeset/note.md"]),
	);
	assert.doesNotThrow(() =>
		verifyChangesets(status(rootRelease("major"), "Stable API."), [".changeset/note.md"], false),
	);
});

void test("rejects blank notes, non-root releases, and pre-1.0 majors", () => {
	assert.throws(() => verifyChangesets(status([], ""), [".changeset/note.md"]), /must explain/);
	assert.throws(
		() =>
			verifyChangesets(status([{ name: "webapp", type: "patch" }], "Fix."), [".changeset/note.md"]),
		/must release only/,
	);
	assert.throws(
		() =>
			verifyChangesets(status([...rootRelease(), ...rootRelease()], "Fix."), [
				".changeset/note.md",
			]),
		/must release only/,
	);
	assert.throws(
		() => verifyChangesets(status(rootRelease("major"), "Breaks API."), [".changeset/note.md"]),
		/pre-1.0 major/,
	);
});

void test("rejects files Changesets did not parse and release-note trailers", () => {
	assert.throws(
		() => verifyChangesets({ changesets: [] }, [".changeset/note.md"]),
		/did not parse/,
	);
	assert.throws(
		() =>
			verifyChangesets(status(rootRelease(), "Fix.\n\nCo-authored-by: Bot"), [
				".changeset/note.md",
			]),
		/must not contain Co-authored-by/,
	);
});
