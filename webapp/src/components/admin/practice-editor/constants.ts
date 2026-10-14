import type { FilterOption } from "@/components/common/FilterToggle";
import { ITEM_KIND, itemKindLabel, type KnownItemKind } from "@/lib/item-kinds";

const ITEM_KIND_HINTS: Record<string, string> = {
	[ITEM_KIND.pullRequest]: "Reviews work submitted in a pull or merge request",
	[ITEM_KIND.pullRequestReview]:
		"Reviews one review a reviewer submitted on a pull or merge request",
	[ITEM_KIND.issue]: "Reviews work described or discussed in an issue",
	[ITEM_KIND.thread]: "Reviews messages in a conversation thread",
	[ITEM_KIND.document]: "Reviews the writing in a published document",
};

/** Undefined for a kind this build has never heard of, which is still offered under its own name. */
export function itemKindHint(kind: string): string | undefined {
	return ITEM_KIND_HINTS[kind];
}

/** The kinds a practice list can be narrowed to: the kinds a practice reviews on an event. */
const FILTERABLE_ITEM_KINDS = [
	ITEM_KIND.pullRequest,
	ITEM_KIND.pullRequestReview,
	ITEM_KIND.issue,
	ITEM_KIND.thread,
	ITEM_KIND.document,
] as const satisfies readonly KnownItemKind[];

export const ITEM_KIND_FILTER_OPTIONS = FILTERABLE_ITEM_KINDS.map((kind) => ({
	value: kind,
	label: itemKindLabel(kind, 2),
}));

/**
 * The same list for {@link FilterToggle}. "All" is shortened on screen to fit the row, so the full
 * name is restored for a screen reader (WCAG 2.2 SC 2.5.3).
 */
export const ITEM_KIND_TOGGLE_OPTIONS: FilterOption<"ALL" | KnownItemKind>[] = [
	{ value: "ALL", label: "All work", shortLabel: "All", srSuffix: "work" },
	...ITEM_KIND_FILTER_OPTIONS,
];

export function generateSlug(name: string): string {
	return name
		.toLowerCase()
		.trim()
		.replaceAll(/[^a-z0-9]+/gu, "-")
		.replaceAll(/^-+|-+$/gu, "")
		.slice(0, 64);
}

export function isValidSlug(slug: string): boolean {
	return /^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(slug) && slug.length >= 3 && slug.length <= 64;
}
