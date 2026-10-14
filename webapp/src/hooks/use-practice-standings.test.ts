import { describe, expect, it } from "vitest";

import type { GroupRef, ReviewedPractice } from "@/api/types.gen";
import { practiceStandings } from "@/stories/practice-profile-story-mock-data";

import { groupsOfReviewedPractices, partitionGroupsByPractices } from "./use-practice-standings";

function group(slug: string): GroupRef {
	return { slug, name: slug, icon: null, color: null };
}

function reviewed(slug: string, inGroup: GroupRef | null): ReviewedPractice {
	return { slug, name: slug, group: inGroup, whyItMatters: null, whatGoodLooksLike: null };
}

const [practice] = practiceStandings;
if (!practice) {
	throw new Error("The fixtures carry at least one practice with a standing");
}

describe("groupsOfReviewedPractices", () => {
	it("lists each group of a reviewed practice once, in the catalog's order", () => {
		const packaging = group("packaging");
		const testing = group("testing");

		const groups = groupsOfReviewedPractices([
			reviewed("scope", packaging),
			reviewed("tests", testing),
			reviewed("describe", packaging),
			// A practice in no group adds none.
			reviewed("loose", null),
		]);

		expect(groups.map((entry) => entry.slug)).toStrictEqual(["packaging", "testing"]);
	});
});

describe("partitionGroupsByPractices", () => {
	it("shows a group with a practice and leaves out one with none, in catalog order", () => {
		const groups = [group("first"), group("empty"), group("second"), group("listed-as-none")];

		const { shown, empty } = partitionGroupsByPractices(groups, {
			first: [practice],
			second: [practice],
			// An entry with no practices is as empty as no entry.
			"listed-as-none": [],
		});

		expect(shown.map((entry) => entry.slug)).toStrictEqual(["first", "second"]);
		expect(empty.map((entry) => entry.slug)).toStrictEqual(["empty", "listed-as-none"]);
	});

	it("leaves every group out while no standings are known", () => {
		const { shown, empty } = partitionGroupsByPractices([group("a")], {});

		expect(shown).toStrictEqual([]);
		expect(empty.map((entry) => entry.slug)).toStrictEqual(["a"]);
	});
});
