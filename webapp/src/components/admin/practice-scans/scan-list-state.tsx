import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

/** One of the report's paged lists, as the route read it. Later pages load on request. */
export type ScanListState<TEntry> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| {
			status: "ready";
			entries: readonly TEntry[];
			hasNext: boolean;
			loadingMore: boolean;
			onLoadMore: () => void;
	  };

/** The end of a report list: a request for the next page while there is one. */
export function ScanListMore({
	hasNext,
	loadingMore,
	onLoadMore,
	noun,
}: {
	hasNext: boolean;
	loadingMore: boolean;
	onLoadMore: () => void;
	noun: string;
}) {
	if (!hasNext) {
		return null;
	}
	return (
		<div className="flex justify-center">
			<Button type="button" variant="outline" size="sm" disabled={loadingMore} onClick={onLoadMore}>
				{loadingMore && <Spinner />}
				{loadingMore ? "Loading…" : `Show more ${noun}`}
			</Button>
		</div>
	);
}
