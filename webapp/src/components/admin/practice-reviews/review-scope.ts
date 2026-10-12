import type { KnownArtifactKind } from "@/lib/artifact-kinds";
import { hasText } from "@/lib/text";

/**
 * The tabs under Practice reviews, each of which keeps the shared scope in its URL, and the Reviews
 * tab's second list, the work no review started on (`unreviewed`), which applies less of it.
 */
export type ReviewTab = "overview" | "runs" | "unreviewed" | "observations" | "feedback";

/**
 * The narrowing that follows the reader from tab to tab: one person, a set of practices and groups, a
 * range of days, and the selection — the work, the review or the observation a list was opened for
 * (`review-selection.ts`).
 *
 * The person has one value but two URL names. The feedback list names the person a piece of feedback
 * is addressed to, `recipientUserId`. Every other tab names the developer an observation is about,
 * `subjectUserId`. Thus, an address that worked before the scope was shared still works.
 */
export interface ReviewScope {
	personId?: number;
	groupSlug?: string[];
	practiceSlug?: string[];
	from?: string;
	to?: string;
	agentJobId?: string;
	artifactKind?: KnownArtifactKind;
	artifactId?: number;
	observationId?: string;
	/** A tracked piece of feedback; its review is `agentJobId`. */
	feedbackId?: string;
	/** A tracked practice: every list narrows to it, whatever the practice filter holds. */
	selectedPractice?: string;
	/** The record above was picked with a row's crosshair, not filtered by (`review-selection.ts`). */
	selected?: true;
}

/** The scope as a tab's URL holds it. */
export interface ReviewScopeSearch extends Omit<ReviewScope, "personId"> {
	subjectUserId?: number;
	recipientUserId?: number;
}

/** One part of the scope, as the toolbar shows it. */
export type ScopeFacet =
	| "person"
	| "group"
	| "practice"
	| "dates"
	| "review"
	| "work"
	| "observation";

/** The selection applies on every list: each one narrows to what follows from it. */
const SELECTION: readonly ScopeFacet[] = ["review", "work", "observation"];

/**
 * Which parts of the scope each tab's endpoint can filter by. A part a tab cannot apply stays in its
 * URL, so the next tab gets it back, and the toolbar shows it as not applied.
 */
export const SCOPE_APPLIES: Record<ReviewTab, readonly ScopeFacet[]> = {
	overview: [],
	// Work no review read has no developer, practice or group yet: only when it happened, and which.
	unreviewed: ["dates", "work"],
	runs: ["person", "group", "practice", "dates", ...SELECTION],
	observations: ["person", "group", "practice", "dates", ...SELECTION],
	feedback: ["person", "group", "practice", "dates", ...SELECTION],
};

function present<T>(values: T[] | undefined): T[] | undefined {
	return values !== undefined && values.length > 0 ? values : undefined;
}

export function readScope(search: ReviewScopeSearch): ReviewScope {
	return {
		personId: search.subjectUserId ?? search.recipientUserId,
		groupSlug: present(search.groupSlug),
		practiceSlug: present(search.practiceSlug),
		from: search.from,
		to: search.to,
		agentJobId: search.agentJobId,
		artifactKind: search.artifactKind,
		artifactId: search.artifactKind ? search.artifactId : undefined,
		observationId: search.observationId,
		feedbackId: search.feedbackId,
		selectedPractice: search.selectedPractice,
		selected: search.selected,
	};
}

/**
 * A scope patch in the URL names of `tab`. Both person names are written, so a change never leaves
 * the other name behind with an old value.
 */
export function scopeSearch(tab: ReviewTab, scope: Partial<ReviewScope>): ReviewScopeSearch {
	const { personId, ...rest } = scope;
	if (!("personId" in scope)) {
		return rest;
	}
	return tab === "feedback"
		? { ...rest, recipientUserId: personId, subjectUserId: undefined }
		: { ...rest, subjectUserId: personId, recipientUserId: undefined };
}

/** The search that a link to `tab` carries: the scope of the tab the reader is on, and nothing else. */
export function carriedScope(tab: ReviewTab, previous: ReviewScopeSearch): ReviewScopeSearch {
	const scope = readScope(previous);
	const search = scopeSearch(tab, scope);
	// Only the keys with a value, so a link does not spell out every empty one.
	return Object.fromEntries(Object.entries(search).filter(([, value]) => value !== undefined));
}

/** Every part of the scope, cleared. */
export function clearedScope(): ReviewScopeSearch {
	return {
		subjectUserId: undefined,
		recipientUserId: undefined,
		groupSlug: undefined,
		practiceSlug: undefined,
		from: undefined,
		to: undefined,
		agentJobId: undefined,
		artifactKind: undefined,
		artifactId: undefined,
		observationId: undefined,
		feedbackId: undefined,
		selectedPractice: undefined,
		selected: undefined,
	};
}

/** The parts of the scope that hold a value. */
export function setScopeFacets(scope: ReviewScope): ScopeFacet[] {
	const set: ScopeFacet[] = [];
	if (scope.personId !== undefined) {
		set.push("person");
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
	if (hasText(scope.agentJobId)) {
		set.push("review");
	}
	if (scope.artifactKind !== undefined) {
		set.push("work");
	}
	if (hasText(scope.observationId)) {
		set.push("observation");
	}
	return set;
}

/** True when a part of the scope that `tab` applies narrows its list. */
export function hasAppliedScope(tab: ReviewTab, scope: ReviewScope): boolean {
	return setScopeFacets(scope).some((facet) => SCOPE_APPLIES[tab].includes(facet));
}
