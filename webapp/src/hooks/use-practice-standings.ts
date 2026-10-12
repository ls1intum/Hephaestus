import { useQuery } from "@tanstack/react-query";

import {
	listGroupsOptions,
	listPracticeGroupStandingsOptions,
	listPracticeStandingsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { PracticeGroup, PracticeGroupStanding, PracticeStanding } from "@/api/types.gen";
import {
	combinePanelStates,
	type LoadState,
	queryLoadState,
} from "@/components/common/panel-state";
import { hasText } from "@/lib/text";

export interface PracticeStandings {
	/** The groups the practice profile shows, in catalog order: those with a practice in them. */
	groups: PracticeGroup[];
	/**
	 * The groups with no practice to show: none added, or all switched off. The table lists none of
	 * them; the all-groups table folds them with the groups not observed yet, and a link to one opens
	 * a level that says so.
	 */
	emptyGroups: PracticeGroup[];
	/** The developer's standing in each group, keyed by group slug. */
	groupStandings: Record<string, PracticeGroupStanding>;
	practiceStandings: PracticeStanding[];
	practicesByGroup: Record<string, PracticeStanding[] | undefined>;
	/** The three queries as one: the page shows them together, so they load and fail as one. */
	state: LoadState;
}

/** The practice standings under their group's slug; a practice in no group is in none of them. */
function groupPracticeStandings(
	practiceStandings: PracticeStanding[],
): Record<string, PracticeStanding[] | undefined> {
	const byGroup: Record<string, PracticeStanding[] | undefined> = {};
	for (const practice of practiceStandings) {
		if (hasText(practice.groupSlug)) {
			(byGroup[practice.groupSlug] ??= []).push(practice);
		}
	}
	return byGroup;
}

/**
 * Splits the groups into the ones the profile shows and the ones it leaves out, keeping the
 * catalog's order in both. A group is shown when the standings carry a practice in it: the standings
 * carry every practice review is admitted for and none that is switched off, so a group whose
 * practices are all switched off is as empty as one with no practices at all.
 */
export function partitionGroupsByPractices(
	groups: PracticeGroup[],
	practicesByGroup: Record<string, PracticeStanding[] | undefined>,
): { shown: PracticeGroup[]; empty: PracticeGroup[] } {
	const shown: PracticeGroup[] = [];
	const empty: PracticeGroup[] = [];
	for (const group of groups) {
		((practicesByGroup[group.slug]?.length ?? 0) > 0 ? shown : empty).push(group);
	}
	return { shown, empty };
}

/**
 * The developer's practice standings in one workspace — the groups, the standing in each and the
 * standing on every practice — as the practice profile and the drawer over it read them.
 */
export function usePracticeStandings(workspaceSlug: string): PracticeStandings {
	const groupsQuery = useQuery(
		listGroupsOptions({
			path: { workspaceSlug },
			query: { visibleInPracticeDashboardsOnly: true },
		}),
	);
	const groupStandingsQuery = useQuery(
		listPracticeGroupStandingsOptions({ path: { workspaceSlug } }),
	);
	const standingsQuery = useQuery(listPracticeStandingsOptions({ path: { workspaceSlug } }));
	const practiceStandings = standingsQuery.data ?? [];
	const practicesByGroup = groupPracticeStandings(practiceStandings);
	const { shown, empty } = partitionGroupsByPractices(groupsQuery.data ?? [], practicesByGroup);
	return {
		groups: shown,
		emptyGroups: empty,
		groupStandings: Object.fromEntries(
			(groupStandingsQuery.data ?? []).map((standing) => [standing.groupSlug, standing]),
		),
		practiceStandings,
		practicesByGroup,
		state: combinePanelStates(
			[groupsQuery, groupStandingsQuery, standingsQuery].map((query) => queryLoadState(query)),
		),
	};
}
