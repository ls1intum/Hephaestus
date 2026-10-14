import { parseSelectionSearch } from "@/components/admin/practice-reviews/review-selection";
import type { ScopeRecordFilter } from "@/components/admin/practice-reviews/ReviewScopeFilters";

import { useReviewSelection } from "./use-review-selection";

/**
 * The one review, piece of work or result a list is filtered to, named for its pill; undefined
 * when it filters to none, or when a crosshair picked it, which the selection bar shows instead. It
 * reads the same queries as the selection bar, so it asks for nothing twice.
 */
export function useScopeRecordFilter(
	workspaceSlug: string,
	search: unknown,
): ScopeRecordFilter | undefined {
	const read = useReviewSelection(workspaceSlug, parseSelectionSearch(search));
	// A piece of feedback or a practice is only ever tracked, never a filter of its own: it has no pill.
	if (
		read.selection === undefined ||
		read.picked ||
		read.selection.kind === "feedback" ||
		read.selection.kind === "practice"
	) {
		return undefined;
	}
	return {
		facet: read.selection.kind,
		name: read.filterName,
	};
}
