import type { KnownItemKind } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

/** The tabs under Practice reviews, each of which keeps the shared scope in its URL. */
export type ReviewTab = "overview" | "reviews" | "results" | "problems" | "feedback";

/**
 * The narrowing that follows the reader from tab to tab: one developer, a set of practices and
 * groups, a range of days, a kind of item, and the selection — the item, the review, the result,
 * the problem or the feedback a list was opened for (`review-selection.ts`). Every tab names the
 * developer `developerId`: feedback has one developer, as a result does.
 */
export interface ReviewScope {
	developerId?: string;
	groupSlug?: string[];
	practiceSlug?: string[];
	from?: string;
	to?: string;
	itemKind?: KnownItemKind;
	itemId?: string;
	reviewId?: string;
	resultId?: string;
	problemId?: string;
	/** A tracked piece of feedback; its review is `reviewId`. */
	feedbackId?: string;
	/** A tracked practice: every list narrows to it, whatever the practice filter holds. */
	selectedPractice?: string;
	/** The record above was picked with a row's crosshair, not filtered by (`review-selection.ts`). */
	selected?: true;
}

/** The scope as a tab's URL holds it: the same names. */
export type ReviewScopeSearch = ReviewScope;

/** One part of the scope, as the toolbar shows it. */
export type ScopeFacet =
	| "developer"
	| "group"
	| "practice"
	| "dates"
	| "kind"
	| "review"
	| "item"
	| "result"
	| "problem";

/** The selection applies on every list: each one narrows to what follows from it. */
const SELECTION: readonly ScopeFacet[] = ["review", "item", "result"];

/**
 * Which parts of the scope each tab's endpoint can filter by. A part a tab cannot apply stays in its
 * URL, so the next tab gets it back, and the toolbar shows it as not applied.
 */
export const SCOPE_APPLIES: Record<ReviewTab, readonly ScopeFacet[]> = {
	overview: [],
	reviews: ["developer", "group", "practice", "dates", "review", "item"],
	results: ["developer", "group", "practice", "dates", "kind", ...SELECTION],
	problems: ["developer", "group", "practice", "dates", "kind", ...SELECTION, "problem"],
	feedback: ["developer", "group", "practice", "dates", "kind", "review", "item"],
};

function present<T>(values: T[] | undefined): T[] | undefined {
	return values !== undefined && values.length > 0 ? values : undefined;
}

export function readScope(search: ReviewScopeSearch): ReviewScope {
	return {
		developerId: search.developerId,
		groupSlug: present(search.groupSlug),
		practiceSlug: present(search.practiceSlug),
		from: search.from,
		to: search.to,
		itemKind: search.itemKind,
		itemId: search.itemId,
		reviewId: search.reviewId,
		resultId: search.resultId,
		problemId: search.problemId,
		feedbackId: search.feedbackId,
		selectedPractice: search.selectedPractice,
		selected: search.selected,
	};
}

/** The search that a link to another tab carries: the scope of the tab the reader is on, and nothing else. */
export function carriedScope(previous: ReviewScopeSearch): ReviewScopeSearch {
	// Only the keys with a value, so a link does not spell out every empty one.
	return Object.fromEntries(
		Object.entries(readScope(previous)).filter(([, value]) => value !== undefined),
	);
}

/** Every part of the scope, cleared. */
export function clearedScope(): ReviewScopeSearch {
	return {
		developerId: undefined,
		groupSlug: undefined,
		practiceSlug: undefined,
		from: undefined,
		to: undefined,
		itemKind: undefined,
		itemId: undefined,
		reviewId: undefined,
		resultId: undefined,
		problemId: undefined,
		feedbackId: undefined,
		selectedPractice: undefined,
		selected: undefined,
	};
}

/** The parts of the scope that hold a value. */
export function setScopeFacets(scope: ReviewScope): ScopeFacet[] {
	const set: ScopeFacet[] = [];
	if (hasText(scope.developerId)) {
		set.push("developer");
	}
	if ((scope.groupSlug?.length ?? 0) > 0) {
		set.push("group");
	}
	if ((scope.practiceSlug?.length ?? 0) > 0 || hasText(scope.selectedPractice)) {
		set.push("practice");
	}
	if (hasText(scope.from)) {
		set.push("dates");
	}
	if (scope.itemKind !== undefined) {
		set.push("kind");
	}
	if (hasText(scope.reviewId)) {
		set.push("review");
	}
	if (hasText(scope.itemId)) {
		set.push("item");
	}
	if (hasText(scope.resultId)) {
		set.push("result");
	}
	if (hasText(scope.problemId)) {
		set.push("problem");
	}
	return set;
}

/** True when a part of the scope that `tab` applies narrows its list. */
export function hasAppliedScope(tab: ReviewTab, scope: ReviewScope): boolean {
	return setScopeFacets(scope).some((facet) => SCOPE_APPLIES[tab].includes(facet));
}
