import { describe, expect, it } from "vitest";

import type { GroupRef, PracticeGroupStanding } from "@/api/types.gen";
import { packagingStanding, practiceStandings } from "@/stories/practice-profile-story-mock-data";

import { groupsOfStandings, partitionGroupsByPractices } from "./use-practice-standings";

function group(slug: string): GroupRef {
	return { slug, name: slug, icon: null, color: null };
}

const [practice] = practiceStandings;
if (!practice) {
	throw new Error("The fixtures carry at least one practice with a standing");
}

describe("groupsOfStandings", () => {
	it("names every group the standings carry, in their order, with its icon and colour", () => {
		const empty = {
			groupSlug: "testing-discipline",
			groupName: "Testing your changes",
			groupDescription: null,
			groupIcon: "TestTube",
			groupColor: "amber",
			standing: "NOT_OBSERVED",
			direction: null,
			trendSupport: null,
			results: [],
			sources: [],
			feedbackSince: null,
			feedbackSpanDays: null,
			guidance: null,
			guidanceSource: null,
			basis: null,
		} satisfies PracticeGroupStanding;
		const standings = [
			{ ...packagingStanding, groupDescription: "Work a reviewer can start on." },
			// A shown group with nothing reviewed yet is still a group the profile can show.
			empty,
		];

		expect(groupsOfStandings(standings)).toStrictEqual([
			{
				slug: packagingStanding.groupSlug,
				name: packagingStanding.groupName,
				icon: packagingStanding.groupIcon,
				color: packagingStanding.groupColor,
			},
			{
				slug: empty.groupSlug,
				name: empty.groupName,
				icon: empty.groupIcon,
				color: empty.groupColor,
			},
		]);
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
