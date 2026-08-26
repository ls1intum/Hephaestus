import assert from "node:assert/strict";
import test from "node:test";
import {
	buildPracticeFanout,
	DEFAULT_PRACTICE_BATCH_SIZE,
} from "../../../main/resources/agent/pi-runner-fanout.ts";

void test("reviews one practice per accountable turn by default", () => {
	const fanout = buildPracticeFanout(
		[
			{ slug: "a", area: "security" },
			{ slug: "b", area: "security" },
		],
		DEFAULT_PRACTICE_BATCH_SIZE,
	);

	assert.deepEqual(fanout.batches, [["a"], ["b"]]);
});

void test("keeps related practices together and splits only at the configured bound", () => {
	const fanout = buildPracticeFanout(
		[
			{ slug: "a", area: "quality" },
			{ slug: "b", area: "security" },
			{ slug: "c", area: "quality" },
			{ slug: "d", area: "quality" },
		],
		2,
	);

	assert.equal(fanout.areaCount, 2);
	assert.deepEqual(fanout.batches, [["a", "c"], ["d"], ["b"]]);
});

void test("treats ungrouped practices as independent review scopes", () => {
	const fanout = buildPracticeFanout([{ slug: "a" }, { slug: "b" }, { slug: "" }], 6);

	assert.equal(fanout.areaCount, 2);
	assert.deepEqual(fanout.batches, [["a"], ["b"]]);
});

for (const invalid of [0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY]) {
	void test(`rejects invalid batch size ${invalid}`, () => {
		assert.throws(
			() => buildPracticeFanout([{ slug: "a", area: "quality" }], invalid),
			/positive integer/,
		);
	});
}
