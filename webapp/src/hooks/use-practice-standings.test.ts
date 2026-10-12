import { describe, expect, it } from "vitest";

import type { PracticeGroup } from "@/api/types.gen";
import { practiceStandings } from "@/stories/practice-profile-story-mock-data";

import { partitionGroupsByPractices } from "./use-practice-standings";

function group(slug: string): PracticeGroup {
	return {
		id: slug.length,
		slug,
		name: slug,
		displayOrder: 0,
		visibleInPracticeDashboards: true,
		autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
		createdAt: new Date("2026-01-01T00:00:00Z"),
	};
}

const [practice] = practiceStandings;
if (!practice) {
	throw new Error("The fixtures carry at least one practice with a standing");
}

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
