import { describe, expect, it } from "vitest";
import type { Practice } from "@/api/types.gen";
import { mockPractices } from "@/components/admin/practices/story-mock-data";
import {
	applyDisplayOrder,
	applyPracticePlacements,
	placePractice,
	practicePlacementSnapshot,
	selectPracticePatch,
	unassignPractices,
} from "./practice-catalog-cache";

function practice(slug: string, areaSlug: string | undefined, displayOrder: number): Practice {
	return {
		...mockPractices[0],
		id: displayOrder + 1,
		name: slug,
		slug,
		areaSlug,
		displayOrder,
	};
}

describe("practice catalog cache updates", () => {
	it("reorders only the requested slugs", () => {
		const practices = [
			practice("first", "quality", 0),
			practice("second", "quality", 1),
			practice("elsewhere", "delivery", 0),
		];

		const result = applyDisplayOrder(practices, ["second", "first"]);

		expect(result.map(({ slug, displayOrder }) => [slug, displayOrder])).toEqual([
			["first", 1],
			["second", 0],
			["elsewhere", 0],
		]);
	});

	it("places a practice between rows in another area and compacts both buckets", () => {
		const practices = [
			practice("first", "quality", 0),
			practice("moving", "quality", 1),
			practice("last", "quality", 2),
			practice("before", "delivery", 0),
			practice("after", "delivery", 1),
		];

		const result = placePractice(practices, "moving", "delivery", 1);

		expect(
			result
				.filter(({ areaSlug }) => areaSlug === "quality")
				.sort((a, b) => a.displayOrder - b.displayOrder)
				.map(({ slug, displayOrder }) => [slug, displayOrder]),
		).toEqual([
			["first", 0],
			["last", 1],
		]);
		expect(
			result
				.filter(({ areaSlug }) => areaSlug === "delivery")
				.sort((a, b) => a.displayOrder - b.displayOrder)
				.map(({ slug, displayOrder }) => [slug, displayOrder]),
		).toEqual([
			["before", 0],
			["moving", 1],
			["after", 2],
		]);
	});

	it("unassigns an area's practices without disturbing existing unassigned order", () => {
		const practices = [
			practice("existing", undefined, 0),
			practice("first", "quality", 0),
			practice("second", "quality", 1),
			practice("elsewhere", "delivery", 0),
		];

		const result = unassignPractices(practices, "quality");

		expect(
			result.map(({ slug, areaSlug, displayOrder }) => [slug, areaSlug, displayOrder]),
		).toEqual([
			["existing", undefined, 0],
			["first", undefined, 1],
			["second", undefined, 2],
			["elsewhere", "delivery", 0],
		]);
	});

	it("supports empty destinations and structural-only rollback", () => {
		const practices = [
			{ ...practice("moving", "quality", 0), reviewTier: "ENGAGE" as const },
			practice("remaining", "quality", 1),
		];
		const snapshot = practicePlacementSnapshot(practices, "moving", null);
		const moved = placePractice(practices, "moving", null, 0).map((item) =>
			item.slug === "moving" ? { ...item, reviewTier: "OFF" as const } : item,
		);

		const restored = applyPracticePlacements(moved, snapshot);

		expect(restored.find(({ slug }) => slug === "moving")).toMatchObject({
			reviewTier: "OFF",
			areaSlug: "quality",
			displayOrder: 0,
		});
		expect(restored.find(({ slug }) => slug === "remaining")?.displayOrder).toBe(1);
	});

	it("reconciles only fields owned by the edit request", () => {
		const updated = {
			...practice("edited", "delivery", 4),
			reviewTier: "OFF" as const,
			name: "Updated",
		};

		expect(selectPracticePatch(updated, { name: "Updated" })).toEqual({ name: "Updated" });
		expect(
			selectPracticePatch(updated, {
				area: { areaSlug: "delivery" },
			}),
		).toEqual({ areaSlug: "delivery", displayOrder: 4 });
		expect(
			selectPracticePatch({ ...updated, whyItMatters: undefined }, { clear: ["WHY_IT_MATTERS"] }),
		).toEqual({ whyItMatters: undefined });
		expect(
			selectPracticePatch(updated, {
				automatedReviewPolicy: updated.automatedReviewPolicy,
			}),
		).toEqual({
			automatedReviewPolicy: updated.automatedReviewPolicy,
			automatedReviewValidation: updated.automatedReviewValidation,
		});
		// Replacing the occasions can move the practice to a different kind of work, and with it to that
		// kind's recommended review settings — so the optimistic patch has to carry all three.
		expect(selectPracticePatch(updated, { bindings: updated.bindings })).toEqual({
			bindings: updated.bindings,
			artifactKind: updated.artifactKind,
			automatedReviewPolicy: updated.automatedReviewPolicy,
			automatedReviewValidation: updated.automatedReviewValidation,
		});
	});
});
