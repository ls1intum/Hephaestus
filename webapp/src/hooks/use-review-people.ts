import { useQuery } from "@tanstack/react-query";

import { listMembershipsOptions } from "@/api/@tanstack/react-query.gen";
import {
	MEMBER_PAGE_SIZE,
	type PersonOption,
	type ReviewPeople,
} from "@/components/admin/practice-reviews/ReviewPersonFacet";
import { hasText } from "@/lib/text";

/**
 * The people a review list can be filtered by, fetched once per workspace and shared by every facet
 * on the screen — TanStack Query deduplicates by key, so the Feedback page's Developer facet and the
 * Results page's Developer facet cost one request between them.
 *
 * <p>Lives here rather than in the facet so the facet takes people as a prop: it is the same control
 * whether its list came from the API, from a fixture, or from a page that already had one.
 */
export function useReviewPeople(workspaceSlug: string): ReviewPeople {
	const membersQuery = useQuery({
		...listMembershipsOptions({ path: { workspaceSlug }, query: { size: MEMBER_PAGE_SIZE } }),
	});
	const options: PersonOption[] = (membersQuery.data?.content ?? []).map(({ account }) => {
		const login = account.identities.find((identity) => hasText(identity.login))?.login;
		return {
			accountId: account.id,
			label: hasText(account.displayName) ? account.displayName : (login ?? `#${account.id}`),
			secondary: hasText(account.displayName) && hasText(login) ? login : undefined,
		};
	});

	return {
		options,
		capped: (membersQuery.data?.totalElements ?? 0) > options.length,
		isLoading: membersQuery.isLoading,
		isError: membersQuery.isError,
	};
}
