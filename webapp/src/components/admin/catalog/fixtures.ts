import type { CatalogGroup, CatalogPractice, PracticeDefinition } from "@/api/types.gen";
import { mockPullRequestReviewedBy, mockPullRequestReviewFields } from "@/mocks/fixtures/practice";

export function catalogPracticeDefinition(
	overrides: Partial<PracticeDefinition> = {},
): PracticeDefinition {
	return {
		name: "Say what changed and why",
		...mockPullRequestReviewFields,
		reviewedBy: mockPullRequestReviewedBy,
		criteria: "The description states why the change is needed and how it was verified.",
		script: null,
		scan: null,
		lineNotes: true,
		guidance: {
			whyItMatters: "Reviewers should not need to reconstruct the author's intent.",
			whatGoodLooksLike: "The description states why, what changed, and how it was verified.",
			goingWell: "You explain each change so a reviewer can start from your intent.",
			visual: null,
			guide: null,
		},
		...overrides,
	};
}

export function catalogPractice(overrides: Partial<CatalogPractice> = {}): CatalogPractice {
	const definition = overrides.definition ?? catalogPracticeDefinition();
	return {
		slug: "describe-what-and-why",
		kind: "scm.pull_request",
		groupSlug: null,
		position: 0,
		definition,
		shipped: definition,
		excluded: false,
		offered: true,
		state: "FROM_HEPHAESTUS",
		changeKind: "NONE",
		etag: "tag-1",
		...overrides,
	};
}

export function catalogGroup(overrides: Partial<CatalogGroup> = {}): CatalogGroup {
	const presentation = {
		name: overrides.name ?? "Packaging work for review",
		description: overrides.description ?? "Make a change cheap to review before you ask for one.",
		icon: overrides.icon ?? "Package",
		color: overrides.color ?? "sky",
	};
	return {
		slug: "review-ready-work",
		position: 0,
		...presentation,
		shipped: presentation,
		excluded: false,
		offered: true,
		state: "FROM_HEPHAESTUS",
		changeKind: "NONE",
		etag: "group-tag-1",
		...overrides,
	};
}
