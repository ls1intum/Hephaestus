import type { QueryClient } from "@tanstack/react-query";

import { getPracticeGuidanceQueryKey } from "@/api/@tanstack/react-query.gen";

/** A first read started before a write must not refill the separate guidance cache afterward. */
export async function refreshPracticeGuidance(
	queryClient: QueryClient,
	workspaceSlug: string,
	practiceSlug: string,
): Promise<void> {
	const filters = {
		queryKey: getPracticeGuidanceQueryKey({ path: { workspaceSlug, practiceSlug } }),
		exact: true,
	};
	await queryClient.cancelQueries(filters);
	await queryClient.invalidateQueries(filters);
}
