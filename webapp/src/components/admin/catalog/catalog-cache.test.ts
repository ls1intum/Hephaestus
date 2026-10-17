import { assert, describe, expect, it } from "vitest";

import type { Catalog } from "@/api/types.gen";

import { placeCatalogPractice, reorderCatalogGroups } from "./catalog-cache";
import { catalogGroup, catalogPractice, catalogPracticeDefinition } from "./fixtures";

const catalog: Catalog = {
	groups: [
		catalogGroup({ slug: "a", position: 0, name: "A" }),
		catalogGroup({ slug: "b", position: 1, name: "B", excluded: true, offered: false }),
	],
	practices: [
		catalogPractice({
			slug: "one",
			groupSlug: "a",
			position: 0,
			definition: catalogPracticeDefinition({ name: "One" }),
		}),
		catalogPractice({
			slug: "two",
			groupSlug: "a",
			position: 1,
			definition: catalogPracticeDefinition({ name: "Two" }),
		}),
		catalogPractice({
			slug: "three",
			groupSlug: "b",
			position: 0,
			offered: false,
			definition: catalogPracticeDefinition({ name: "Three" }),
		}),
	],
};

function slugsIn(updated: Catalog, groupSlug: string | null): string[] {
	return updated.practices
		.filter((practice) => practice.groupSlug === groupSlug)
		.sort((a, b) => a.position - b.position)
		.map((practice) => practice.slug);
}

describe("catalog cache", () => {
	it("reorders groups without changing their presentation", () => {
		const updated = reorderCatalogGroups(catalog, ["b", "a"]);
		expect(updated.groups.map(({ slug, position }) => ({ slug, position }))).toStrictEqual([
			{ slug: "a", position: 1 },
			{ slug: "b", position: 0 },
		]);
		const [reordered] = updated.groups;
		assert(reordered);
		expect(reordered.name).toBe("A");
	});

	it("moves a practice and normalizes both groups", () => {
		const updated = placeCatalogPractice(catalog, "two", "b", 0);
		expect(slugsIn(updated, "a")).toStrictEqual(["one"]);
		expect(slugsIn(updated, "b")).toStrictEqual(["two", "three"]);
		expect(updated.practices.find(({ slug }) => slug === "two")?.offered).toBe(false);
	});

	it("reorders within one group", () => {
		const updated = placeCatalogPractice(catalog, "two", "a", 0);
		expect(slugsIn(updated, "a")).toStrictEqual(["two", "one"]);
	});

	it("offers a practice again when it leaves an excluded group", () => {
		const updated = placeCatalogPractice(catalog, "three", null, 0);
		expect(slugsIn(updated, null)).toStrictEqual(["three"]);
		expect(updated.practices.find(({ slug }) => slug === "three")?.offered).toBe(true);
	});
});
