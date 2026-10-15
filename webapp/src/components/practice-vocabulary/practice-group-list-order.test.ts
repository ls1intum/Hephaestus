import { describe, expect, it } from "vitest";

import type { GroupRef, PracticeGroupStanding } from "@/api/types.gen";

import { DEFAULT_PRACTICE_GROUP_SORT, sortPracticeGroups } from "./practice-group-list-order";

function group(slug: string, name: string): GroupRef {
	return { slug, name, color: null, icon: null };
}

function standing(slug: string, value: PracticeGroupStanding["standing"]): PracticeGroupStanding {
	return {
		groupSlug: slug,
		groupName: slug,
		standing: value,
		results: [],
		sources: [],
		direction: null,
		feedbackSince: null,
		feedbackSpanDays: null,
		guidance: null,
		guidanceSource: null,
		trendSupport: null,
	};
}

const slugsOf = (groups: GroupRef[]) => groups.map((entry) => entry.slug);

describe("sortPracticeGroups", () => {
	const groups = [
		group("well", "Going well"),
		group("attention", "Needs attention"),
		group("mixed", "Mixed"),
		group("silent", "Not reviewed"),
	];
	const standings = {
		well: standing("well", "STRENGTH"),
		attention: standing("attention", "DEVELOPING"),
		mixed: standing("mixed", "MIXED"),
	};

	it("puts what needs attention first when ascending, what no review reached last", () => {
		expect(slugsOf(sortPracticeGroups(groups, standings, "asc"))).toStrictEqual([
			"attention",
			"mixed",
			"well",
			"silent",
		]);
	});

	it("reverses the standings when descending", () => {
		expect(slugsOf(sortPracticeGroups(groups, standings, "desc"))).toStrictEqual([
			"silent",
			"well",
			"mixed",
			"attention",
		]);
	});

	it("breaks ties by the catalog's order the groups arrived in, in both directions", () => {
		const tied = [group("b", "Bravo"), group("c", "Charlie"), group("a", "Alpha")];
		const all = Object.fromEntries(
			tied.map((entry) => [entry.slug, standing(entry.slug, "MIXED")]),
		);
		expect(slugsOf(sortPracticeGroups(tied, all, "asc"))).toStrictEqual(["b", "c", "a"]);
		expect(slugsOf(sortPracticeGroups(tied, all, "desc"))).toStrictEqual(["b", "c", "a"]);
	});

	it("returns a copy", () => {
		const sorted = sortPracticeGroups(groups, standings, DEFAULT_PRACTICE_GROUP_SORT);
		expect(sorted).not.toBe(groups);
		expect(slugsOf(groups)[0]).toBe("well");
	});
});
