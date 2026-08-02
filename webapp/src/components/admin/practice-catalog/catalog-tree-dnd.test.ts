import { describe, expect, it } from "vitest";
import { getCatalogDropTarget } from "./catalog-tree-dnd";

interface Entry {
	slug: string;
	name: string;
	areaSlug?: string;
	displayOrder: number;
}

const entry = (slug: string, areaSlug: string | undefined, displayOrder: number): Entry => ({
	slug,
	name: slug,
	areaSlug,
	displayOrder,
});

describe("catalog tree drop target", () => {
	const entries = [
		entry("source", "quality", 0),
		entry("before", "delivery", 0),
		entry("after", "delivery", 1),
	];

	it("places before or after a row in another area", () => {
		expect(getCatalogDropTarget(entries, "source", "delivery", "after")).toEqual({
			areaSlug: "delivery",
			position: 1,
		});
		expect(getCatalogDropTarget(entries, "source", "delivery", "after", true)).toEqual({
			areaSlug: "delivery",
			position: 2,
		});
	});

	it("appends to empty areas and Unassigned", () => {
		expect(getCatalogDropTarget(entries, "source", "empty")).toEqual({
			areaSlug: "empty",
			position: 0,
		});
		expect(getCatalogDropTarget(entries, "source", null)).toEqual({
			areaSlug: null,
			position: 0,
		});
	});

	it("ignores the active row as an anchor", () => {
		expect(getCatalogDropTarget(entries, "source", "quality", "source")).toBeNull();
	});
});
