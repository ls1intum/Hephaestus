import { InfiniteListEnd } from "@/components/common/InfiniteListEnd";
import type { MorePages } from "@/runtime/tanstack-query/infinite-list";

export interface ReviewListEndProps extends MorePages {
	/** What the list holds, in the plural: "reviews", "feedback". */
	noun: string;
}

/** Placeholder rows for the next page: enough to read as the list going on, not a whole page. */
const LOADING_MORE_ROWS = 3;

/**
 * The placeholder rows a list's table draws after its rows while the next page loads, as its
 * `loadingMore`; none otherwise.
 */
export function loadingMoreRows({ isLoadingMore }: Pick<MorePages, "isLoadingMore">) {
	return isLoadingMore ? LOADING_MORE_ROWS : undefined;
}

/**
 * The end of an admin review list: the next page loads as it scrolls into view. The table above
 * draws that page's placeholder rows in its own columns (`loadingMoreRows`), so the end draws none.
 */
export function ReviewListEnd({ noun, ...more }: ReviewListEndProps) {
	return (
		<InfiniteListEnd
			{...more}
			moreLabel={`Show more ${noun}`}
			failedLabel={`We could not load more ${noun}.`}
			loadingRow={null}
		/>
	);
}
