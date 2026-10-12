import assert from "node:assert/strict";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { asArray, asRecord, asString, readJsonFileSync } from "./lib/json.ts";
import {
	DEVELOPERS,
	READER_CARDS,
	READER_RUNS,
	SPLITS,
	bucketOf,
	readerCards,
} from "./lib/practices-demo.ts";

// These checks cover only the demo's own data. Which standings, small bars and card states the
// demo produces depends on the server's rules, so only the running page confirms them
// (docs/contributor/local-development.mdx § Seeding the practices demo).

const catalog = readJsonFileSync(
	fileURLToPath(
		new URL(
			"../server/application/src/main/resources/practices/default-catalog.json",
			import.meta.url,
		),
	),
);

/** Each bundled practice's group, from the catalog the seed's workspace installs. */
const GROUP_OF = new Map(
	asArray(asRecord(catalog, "catalog").groups, "groups").flatMap((entry) => {
		const group = asRecord(entry, "group");
		const slug = asString(group.slug, "group slug");
		return asArray(group.practices, "practices").map(
			(practice) => [asString(asRecord(practice, "practice").slug, "practice slug"), slug] as const,
		);
	}),
);

void test("the demo splits every group that the catalog ships, except communication", () => {
	const groups = new Set(GROUP_OF.values());
	assert.deepEqual(
		Object.keys(SPLITS).filter((group) => !groups.has(group)),
		[],
	);
	// Its practices review conversations, and the demo writes reviews of pull requests and issues only.
	assert.deepEqual(
		[...groups].filter((group) => !(group in SPLITS)),
		["communication"],
	);
});

void test("the demo names only practices that the catalog ships", () => {
	const practices = [
		...READER_RUNS.flatMap((run) => run.observations.map((observation) => observation.practice)),
		...READER_CARDS.map((card) => card.practice),
	];
	assert.deepEqual(
		practices.filter((practice) => !GROUP_OF.has(practice)),
		[],
	);
});

void test("the reader gets a practice that never applied, one not observed yet and a group not observed yet", () => {
	const outcomes = new Map<string, string[]>();
	for (const observation of READER_RUNS.flatMap((run) => run.observations)) {
		outcomes.set(observation.practice, [
			...(outcomes.get(observation.practice) ?? []),
			observation.outcome,
		]);
	}
	// The Practice profile's "Does not apply to your work yet" fold: every observation found it did
	// not apply.
	assert.deepEqual(
		[...outcomes]
			.filter(([, each]) => each.every((outcome) => outcome === "NOT_APPLICABLE"))
			.map(([practice]) => practice),
		["changes-dependencies-deliberately"],
	);
	// "Practices without observations yet": a practice of a group the reader has observations in
	// that no run of the reader observes.
	const observedGroups = new Set([...outcomes.keys()].map((practice) => GROUP_OF.get(practice)));
	assert.ok(
		[...GROUP_OF].some(([practice, group]) => observedGroups.has(group) && !outcomes.has(practice)),
	);
	// "Practice groups without observations yet": a group the reader has no observation in.
	assert.ok([...new Set(GROUP_OF.values())].some((group) => !observedGroups.has(group)));
});

void test("each group places every synthetic developer once, by its numbers", () => {
	for (const [groupIndex, [group, split]] of Object.entries(SPLITS).entries()) {
		const counts = { needs: 0, mixed: 0, well: 0, none: 0 };
		for (let index = 0; index < DEVELOPERS; index += 1) {
			counts[bucketOf(split, groupIndex, index)] += 1;
		}
		assert.deepEqual(Object.values(counts), split, group);
	}
});

void test("each card cites only problems that the reader's runs recorded on its practice", () => {
	const jobIds = new Map(READER_RUNS.map((run) => [run.key, `job-${run.key}`]));
	const outcomes = new Map<string, string>(
		READER_RUNS.flatMap((run) =>
			run.observations.map(
				(observation) => [`${run.key}/${observation.practice}`, observation.outcome] as const,
			),
		),
	);
	const observationIds = new Map([...outcomes.keys()].map((key) => [key, key]));
	const cards = readerCards(5, jobIds, observationIds);
	assert.equal(new Set(cards.map((card) => card.id)).size, READER_CARDS.length);
	for (const card of cards) {
		assert.ok(
			card.evidence.every((key) => outcomes.get(key) === "NOT_MET"),
			card.practiceSlug,
		);
		assert.ok(card.response === null || card.deliveredAt !== null, card.practiceSlug);
		assert.ok(
			card.response?.resolution !== "DISPUTED" || card.response.explanation !== null,
			card.practiceSlug,
		);
	}
});
