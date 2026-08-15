import { useQuery } from "@tanstack/react-query";
import { getReflectionFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import type { ReflectionFeedback } from "@/api/types.gen";

export interface ReflectionFeedbackResult {
	/** Newest first, as the endpoint sends them. `undefined` until the first answer arrives. */
	feedback: ReflectionFeedback[] | undefined;
	isLoading: boolean;
	error: unknown;
	refetch: () => void;
}

/**
 * The signed-in developer's own prepared practice feedback.
 *
 * <p>Takes no user: the endpoint answers for whoever is calling and has no parameter to ask about
 * anybody else, which is the property that makes it safe to write private text onto. Nothing here
 * should ever grow one.
 *
 * <p><b>Only call this from the screen that shows the page.</b> The read is the delivery — the server
 * flips a prepared unit to delivered when it hands it over — so fetching it to drive a sidebar
 * badge, a prefetch on hover, or a route loader that runs before the reader has arrived would enter
 * text nobody has seen into the ledger as received. A window-focus refetch of the open page is fine:
 * the flip is a compare-and-set on the prepared state, so repeating it changes nothing.
 */
export function useReflectionFeedback(workspaceSlug: string): ReflectionFeedbackResult {
	const query = useQuery({
		...getReflectionFeedbackOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});

	return {
		feedback: query.data,
		isLoading: query.isPending,
		error: query.isError ? query.error : undefined,
		refetch: () => void query.refetch(),
	};
}
