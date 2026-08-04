import { describe, expect, it } from "vitest";
import type { CatalogEntryStatus, CuratedCatalog } from "@/api/types.gen";
import {
	orderedPracticeSlugs,
	placeCuratedPractice,
	reorderCuratedAreas,
	reorderCuratedPractices,
} from "./curated-catalog-cache";

const status: CatalogEntryStatus = {
	etag: "entry",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
};

const automatedReview = {
	mode: "LANGUAGE_MODEL",
	evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
} as const;

const catalog = {
	etag: "structure",
	customOrder: false,
	summary: {
		total: 5,
		updatesChangingDetection: 0,
		updatesChangingWordingOnly: 0,
		updatesChangingPresentation: 0,
		editedHere: 0,
		yours: 0,
		notOffered: 0,
		noLongerShipped: 0,
	},
	areas: [
		{ slug: "a", position: 0, definition: { name: "A" }, status },
		{ slug: "b", position: 1, definition: { name: "B" }, status: { ...status, offered: false } },
	],
	practices: [
		{
			slug: "one",
			name: "One",
			artifactType: "ISSUE",
			automatedReview,
			areaSlug: "a",
			position: 0,
			effectivelyOffered: true,
			status,
		},
		{
			slug: "two",
			name: "Two",
			artifactType: "ISSUE",
			automatedReview,
			areaSlug: "a",
			position: 1,
			effectivelyOffered: true,
			status,
		},
		{
			slug: "three",
			name: "Three",
			artifactType: "ISSUE",
			automatedReview,
			areaSlug: "b",
			position: 0,
			effectivelyOffered: false,
			status,
		},
	],
} satisfies CuratedCatalog;

describe("curated catalog cache", () => {
	it("reorders areas without changing their definitions", () => {
		const updated = reorderCuratedAreas(catalog, ["b", "a"]);
		expect(updated.areas.map(({ slug, position }) => ({ slug, position }))).toEqual([
			{ slug: "a", position: 1 },
			{ slug: "b", position: 0 },
		]);
		expect(updated.areas[0].definition).toBe(catalog.areas[0].definition);
	});

	it("moves a practice and normalizes both buckets", () => {
		const updated = placeCuratedPractice(catalog, "two", "b", 0);
		expect(orderedPracticeSlugs(updated, "a")).toEqual(["one"]);
		expect(orderedPracticeSlugs(updated, "b")).toEqual(["two", "three"]);
		expect(updated.practices.find(({ slug }) => slug === "two")?.effectivelyOffered).toBe(false);
	});

	it("reorders within one area", () => {
		const updated = reorderCuratedPractices(catalog, "a", ["two", "one"]);
		expect(orderedPracticeSlugs(updated, "a")).toEqual(["two", "one"]);
	});
});
