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
	// Deliberately not in `displayOrder` order: a fixture whose array order already agrees with
	// `displayOrder` cannot tell a sorted destination apart from an unsorted one.
	const entries = [
		entry("after", "delivery", 1),
		entry("source", "quality", 0),
		entry("before", "delivery", 0),
	];

	it("places before or after a row in another area", () => {
		expect(getCatalogDropTarget(entries, "source", "delivery", "after")).toStrictEqual({
			areaSlug: "delivery",
			position: 1,
		});
		expect(getCatalogDropTarget(entries, "source", "delivery", "after", true)).toStrictEqual({
			areaSlug: "delivery",
			position: 2,
		});
	});

	/**
	 * The row being dragged is not in its own destination. Counting itself would put the drop off the
	 * end of the area, and the row would come back where it started.
	 */
	it("does not count the moving row when it stays in its own area", () => {
		expect(getCatalogDropTarget(entries, "before", "delivery", "after", true)).toStrictEqual({
			areaSlug: "delivery",
			position: 1,
		});
		expect(getCatalogDropTarget(entries, "before", "delivery", "after")).toStrictEqual({
			areaSlug: "delivery",
			position: 0,
		});
	});

	/** Ties are broken the way the tree renders them, so the index means the same thing on both. */
	it("orders a displayOrder tie by name", () => {
		const tied = [entry("beta", "delivery", 0), entry("alpha", "delivery", 0)];

		expect(getCatalogDropTarget(tied, "source", "delivery", "beta")).toStrictEqual({
			areaSlug: "delivery",
			position: 1,
		});
	});

	it("appends to empty areas and Unassigned", () => {
		expect(getCatalogDropTarget(entries, "source", "empty")).toStrictEqual({
			areaSlug: "empty",
			position: 0,
		});
		expect(getCatalogDropTarget(entries, "source", null)).toStrictEqual({
			areaSlug: null,
			position: 0,
		});
	});

	it("ignores the active row as an anchor", () => {
		expect(getCatalogDropTarget(entries, "source", "quality", "source")).toBeNull();
	});

	/**
	 * An anchor from a stale render, or from an area the row is not in. Refusing the drop leaves the
	 * order alone; guessing an end of the list would move the practice somewhere nobody aimed at.
	 */
	it("refuses a drop onto an anchor the destination does not hold", () => {
		expect(
			getCatalogDropTarget(entries, "source", "delivery", "gone-in-a-later-render"),
		).toBeNull();
		expect(getCatalogDropTarget(entries, "before", "delivery", "source")).toBeNull();
	});
});
