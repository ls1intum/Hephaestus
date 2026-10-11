import assert from "node:assert/strict";
import { describe, test } from "node:test";

import type { Unrated } from "./contract.ts";
import { renderSection, type ScriptResult } from "./leads.ts";

const POINTER = "`out/p.json`";

function result(parts: Partial<ScriptResult>): ScriptResult {
	return {
		practice: "p",
		contract: "definition",
		status: "ok",
		models: {},
		leads: [],
		facts: {},
		unrated: [],
		directions: [],
		kinds: {},
		dropped: 0,
		...parts,
	};
}

/** Ten directions of the longest length that a script may return. */
const longDirections = Array.from({ length: 10 }, (_, i) => `${String(i)} ${"d".repeat(296)}`);

const manyLeads = Array.from({ length: 200 }, (_, i) => ({
	at: { change: `src/file${String(i)}.ts`, line: 1 },
	kind: "candidate",
	quote: "x".repeat(120),
	facts: { note: "y".repeat(150) },
}));

const unrated = (count: number): Unrated[] =>
	Array.from({ length: count }, (_, i) => ({
		what: `added comments of group ${String(i)} ${"w".repeat(120)}`,
		count: i + 1,
		reason: "deadline",
	}));

void describe("the section of a script's result", () => {
	void test("long directions and many leads cannot push the not-rated lines out, and the cut says what it left out", () => {
		const section = renderSection(
			result({
				directions: longDirections,
				leads: manyLeads,
				kinds: { candidate: "A line to check." },
				unrated: unrated(2),
				dropped: 3,
			}),
			"",
			POINTER,
		);
		assert.ok(section.length <= 3000, `${String(section.length)} characters`);
		assert.match(section, /^- 0 d+\n/u);
		assert.match(
			section,
			/^- \d+ more lines are not shown, 200 of them leads\. The full result is in `out\/p\.json`\.$/mu,
		);
		const notRated = section.split("\n").filter((line) => line.startsWith("- Not rated:"));
		assert.equal(notRated.length, 3);
		assert.match(notRated[0] ?? "", /^- Not rated: 1 added comments of group 0 w+ \(deadline\)\./u);
		assert.match(
			section,
			/- Not rated: 3 lead\(s\) with a kind that the script did not declare or cited lines that the source does not hold \(off-format\)\. Read those yourself\.\n$/u,
		);
	});

	void test("not-rated lines that do not fit in their share are counted", () => {
		const section = renderSection(
			result({ directions: longDirections, unrated: unrated(20) }),
			"",
			POINTER,
		);
		assert.ok(section.length <= 3000, `${String(section.length)} characters`);
		const omitted =
			/^- Not rated: (?<count>\d+) more entries are not shown\. The full result is in `out\/p\.json`\.$/mu.exec(
				section,
			);
		assert.ok(omitted !== null, section);
		const shown = section.split("\n").filter((line) => /^- Not rated: \d+ added/u.test(line));
		assert.equal(shown.length + Number(omitted.groups?.count), 20);
		assert.ok(shown.length > 0);
	});

	void test("a failed script's notice and its error stay first, on one bounded line", () => {
		const message = `Script failed: ${"e".repeat(1990)}\n    at run (practices/p.ts:4:9)`;
		const section = renderSection(
			result({ status: "error", directions: [message], error: message }),
			"",
			POINTER,
		);
		assert.ok(section.length <= 3000);
		const [notice, blank, line, ...rest] = section.split("\n");
		assert.equal(notice, "> **Script failed.** Agent must analyze this practice manually.");
		assert.equal(blank, "");
		assert.match(line ?? "", /^- Script failed: e+…$/u);
		assert.ok((line ?? "").length <= 302);
		assert.deepEqual(rest, [""]);
	});

	void test("a section that fits is whole, with no count", () => {
		const section = renderSection(
			result({ directions: ["Read the change."], unrated: unrated(1) }),
			"",
			POINTER,
		);
		assert.equal(
			section,
			`- Read the change.\n- Not rated: 1 added comments of group 0 ${"w".repeat(120)} (deadline). Read those yourself.\n`,
		);
	});
});
