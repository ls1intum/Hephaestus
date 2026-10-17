import type { GroupRef, PracticeGroupStanding, PracticeStanding } from "@/api/types.gen";

import { statusValues } from "@/components/common/status-def";
import { PRACTICE_GROUP_STANDING_DEFS } from "./practice-group-standing-defs";

export type SortDirection = "asc" | "desc";

/**
 * What needs attention first: the reading order of the registry. The practices tables sort by
 * standing alone, so the direction is the whole of their sort.
 */
export const DEFAULT_PRACTICE_GROUP_SORT: SortDirection = "asc";

/** Registry declaration order: what needs attention first, what no review has reached last. */
const STANDING_ORDER = statusValues(PRACTICE_GROUP_STANDING_DEFS);

/**
 * Orders a copy of `items` by standing under `sort`, needs attention first when ascending. Ties
 * fall through to `tieBreak` in both directions, so a header press reverses the standings and
 * nothing else.
 */
export function sortByStanding<T>(
	items: readonly T[],
	sort: SortDirection,
	standingOf: (item: T) => PracticeStanding["standing"],
	tieBreak: (left: T, right: T) => number,
): T[] {
	const sign = sort === "asc" ? 1 : -1;
	const rank = (item: T) => STANDING_ORDER.indexOf(standingOf(item));
	return [...items].sort(
		(left, right) => sign * (rank(left) - rank(right)) || tieBreak(left, right),
	);
}

export function sortPracticeGroups<TGroup extends Pick<GroupRef, "slug">>(
	groups: readonly TGroup[],
	standings: Record<string, PracticeGroupStanding | undefined>,
	sort: SortDirection,
): TGroup[] {
	const arrival = new Map(groups.map((group, index) => [group.slug, index]));
	return sortByStanding(
		groups,
		sort,
		(group) => standings[group.slug]?.standing ?? "NOT_OBSERVED",
		(left, right) => (arrival.get(left.slug) ?? 0) - (arrival.get(right.slug) ?? 0),
	);
}
