import { useQuery } from "@tanstack/react-query";

import { getOwnPracticeProfileOverviewOptions } from "@/api/@tanstack/react-query.gen";
import { queryLoadState } from "@/components/common/panel-state";
import { EMPTY_OVERVIEW } from "@/components/practice-profile/compose-overview";

/**
 * How often the page's review-backed reads refresh while it is open: a review can finish at any
 * moment, and a reader sitting on the page gives the cache no mount or refocus to notice it.
 */
export const PRACTICE_PROFILE_POLL_MS = 60_000;

/**
 * The practice profile's overview — what goes well, what changed and what the latest review looked at —
 * empty until it loads. The overview is read with no window, so the server compares the latest review
 * with the one before it.
 */
export function usePracticeProfileOverview(workspaceSlug: string) {
	const query = useQuery({
		...getOwnPracticeProfileOverviewOptions({ path: { workspaceSlug } }),
		refetchInterval: PRACTICE_PROFILE_POLL_MS,
	});
	return { overview: query.data ?? EMPTY_OVERVIEW, state: queryLoadState(query) };
}
