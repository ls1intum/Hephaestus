import { XIcon } from "lucide-react";
import type { ReactNode } from "react";

import { ResultCount } from "@/components/common/ResultCount";
import { Button } from "@/components/ui/button";

import { type AppliedFacetPill, AppliedFacetPills } from "./AppliedFacetPills";
import { hasAppliedScope } from "./review-scope";
import { ReviewScopeFilters, type ScopeFiltersProps } from "./ReviewScopeFilters";

export interface ReviewFilterBarProps extends ScopeFiltersProps {
	/** Clears every part of the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	/** Every facet of the tab, and its sort where it has one, in the order the row shows them. */
	facets: ReactNode;
	/** The pills of the tab's own facets, shown below the row. */
	pills: AppliedFacetPill[];
	/** Pills for filters with no control in the row, such as one a link set: "Finished after merge: Only". */
	referencePills?: ReactNode;
	/** Whether one of the tab's own facets narrows the list, which offers "Reset". */
	hasFacet: boolean;
	/** Clears the tab's own facets. The shared scope stays. */
	onReset: () => void;
	/** How many rows the filters select; absent while the answer is on its way. */
	total: number | undefined;
	/** Singular and plural of what the list counts. */
	noun: readonly [string, string];
}

/**
 * The filters of the Reviews, Results and Feedback tabs, in two places. The shared scope
 * (developer, group, practice, date) and "Clear scope" go above the tabs (`ReviewScopeFilters`),
 * because they apply to every tab. Below the tabs are all of the tab's own facets in one row that
 * wraps, then "Reset" for those facets alone, and the count on the right. The pills of the tab's
 * own facets are below that row on a phone.
 */
export function ReviewFilterBar({
	onClearScope,
	facets,
	pills,
	referencePills,
	hasFacet,
	onReset,
	total,
	noun,
	...scopeProps
}: ReviewFilterBarProps) {
	const { tab, scope } = scopeProps;
	const hasFilter = hasFacet || hasAppliedScope(tab, scope);
	return (
		<>
			<ReviewScopeFilters {...scopeProps} onClear={onClearScope} />
			<div role="group" aria-label="Filters" className="space-y-2">
				{/* Chips wrap side by side at every width: a phone fits two or three to a line. */}
				<div className="flex flex-wrap items-center gap-2">
					{facets}
					{hasFacet && (
						<Button variant="ghost" size="sm" className="h-8" onClick={onReset}>
							Reset
							<XIcon aria-hidden data-icon="inline-end" />
						</Button>
					)}
					<div className="basis-full sm:ml-auto sm:basis-auto">
						<ResultCount total={total} noun={noun} hasFilter={hasFilter} />
					</div>
				</div>
				<div className="flex flex-wrap gap-2 empty:hidden">
					<AppliedFacetPills pills={pills} />
					{referencePills}
				</div>
			</div>
		</>
	);
}
