import { useQuery } from "@tanstack/react-query";

import {
	listPracticeGroupStandingsOptions,
	listPracticeStandingsOptions,
	listReviewedPracticesOptions,
} from "@/api/@tanstack/react-query.gen";
import type {
	GroupRef,
	PracticeGroupStanding,
	PracticeStanding,
	ReviewedPractice,
} from "@/api/types.gen";
import {
	combinePanelStates,
	type LoadState,
	queryLoadState,
} from "@/components/common/panel-state";
import { hasText } from "@/lib/text";

export interface PracticeStandings {
	/** The groups the practice profile shows, in catalog order: those with a practice in them. */
	groups: GroupRef[];
	/**
	 * The groups of reviewed practices with no standing to show. The table lists none of them; the
	 * all-groups table folds them with the groups not observed yet, and a link to one opens a level
	 * that says so.
	 */
	emptyGroups: GroupRef[];
	/** The developer's standing in each group, keyed by group slug. */
	groupStandings: Record<string, PracticeGroupStanding>;
	practiceStandings: PracticeStanding[];
	/** Every practice Hephaestus reviews in the workspace, with its name and words, standing or not. */
	reviewedPractices: ReviewedPractice[];
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
 * The groups of the practices Hephaestus reviews, once each, in the catalog's order the list keeps.
 * A practice in no group adds none.
 */
export function groupsOfReviewedPractices(practices: ReviewedPractice[]): GroupRef[] {
	const groups = new Map<string, GroupRef>();
	for (const { group } of practices) {
		if (group !== null && !groups.has(group.slug)) {
			groups.set(group.slug, group);
		}
	}
	return [...groups.values()];
}

/**
 * Splits the groups into the ones the profile shows and the ones it leaves out, keeping the
 * catalog's order in both. A group is shown when the standings carry a practice in it: the standings
 * carry every practice review is admitted for and none that is switched off, so a group whose
 * practices are all switched off is as empty as one with no practices at all.
 */
export function partitionGroupsByPractices(
	groups: GroupRef[],
	practicesByGroup: Record<string, PracticeStanding[] | undefined>,
): { shown: GroupRef[]; empty: GroupRef[] } {
	const shown: GroupRef[] = [];
	const empty: GroupRef[] = [];
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
	// The member's view of the catalog: the practices Hephaestus reviews, each with its group.
	const reviewedQuery = useQuery(listReviewedPracticesOptions({ path: { workspaceSlug } }));
	const groupStandingsQuery = useQuery(
		listPracticeGroupStandingsOptions({ path: { workspaceSlug } }),
	);
	const standingsQuery = useQuery(listPracticeStandingsOptions({ path: { workspaceSlug } }));
	const practiceStandings = standingsQuery.data ?? [];
	const practicesByGroup = groupPracticeStandings(practiceStandings);
	const reviewedPractices = reviewedQuery.data ?? [];
	const { shown, empty } = partitionGroupsByPractices(
		groupsOfReviewedPractices(reviewedPractices),
		practicesByGroup,
	);
	return {
		groups: shown,
		emptyGroups: empty,
		groupStandings: Object.fromEntries(
			(groupStandingsQuery.data ?? []).map((standing) => [standing.groupSlug, standing]),
		),
		practiceStandings,
		reviewedPractices,
		practicesByGroup,
		state: combinePanelStates(
			[reviewedQuery, groupStandingsQuery, standingsQuery].map((query) => queryLoadState(query)),
		),
	};
}
