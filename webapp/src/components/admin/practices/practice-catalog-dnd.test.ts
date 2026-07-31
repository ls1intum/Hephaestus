import { describe, expect, it } from "vitest";
import type { Practice } from "@/api/types.gen";
import { getPracticeDropTarget } from "./practice-catalog-dnd";
import { mockPractices } from "./story-mock-data";

function practice(slug: string, areaSlug: string | undefined, displayOrder: number): Practice {
	return { ...mockPractices[0], slug, name: slug, areaSlug, displayOrder };
}

describe("practice catalog drop target", () => {
	const practices = [
		practice("source", "quality", 0),
		practice("before", "delivery", 0),
		practice("after", "delivery", 1),
	];

	it("places before or after a row in another area", () => {
		expect(getPracticeDropTarget(practices, "source", "delivery", "after")).toEqual({
			areaSlug: "delivery",
			position: 1,
		});
		expect(getPracticeDropTarget(practices, "source", "delivery", "after", true)).toEqual({
			areaSlug: "delivery",
			position: 2,
		});
	});

	it("appends to empty areas and Unassigned", () => {
		expect(getPracticeDropTarget(practices, "source", "empty")).toEqual({
			areaSlug: "empty",
			position: 0,
		});
		expect(getPracticeDropTarget(practices, "source", null)).toEqual({
			areaSlug: null,
			position: 0,
		});
	});

	it("ignores the active row as an anchor", () => {
		expect(getPracticeDropTarget(practices, "source", "quality", "source")).toBeNull();
	});
});
