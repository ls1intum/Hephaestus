import { useQuery } from "@tanstack/react-query";

import { listGroupsOptions, listPracticesOptions } from "@/api/@tanstack/react-query.gen";
import {
	groupFacetOptions,
	type PracticeFacetOption,
	practiceFacetOptions,
} from "@/components/admin/practice-reviews/ResultFilters";
import type { ReviewPeople } from "@/components/admin/practice-reviews/ReviewPersonFacet";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { useReviewPeople } from "@/hooks/use-review-people";

export interface ReviewScopeSources {
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
}

/**
 * The options of the shared scope's facets, for every tab under Practice reviews. TanStack Query
 * deduplicates by key, so a switch between tabs asks for none of them again.
 */
export function useReviewScopeSources(workspaceSlug: string): ReviewScopeSources {
	const groupsQuery = useQuery({ ...listGroupsOptions({ path: { workspaceSlug } }) });
	const practicesQuery = useQuery({ ...listPracticesOptions({ path: { workspaceSlug } }) });
	const people = useReviewPeople(workspaceSlug);
	return {
		groups: {
			options: groupFacetOptions(groupsQuery.data),
			isLoading: groupsQuery.isLoading,
			isError: groupsQuery.isError,
		},
		practices: {
			options: practiceFacetOptions(practicesQuery.data, groupsQuery.data),
			isLoading: practicesQuery.isLoading,
			isError: practicesQuery.isError,
		},
		people,
	};
}
