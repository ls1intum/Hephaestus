import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import type { MorePages } from "@/runtime/tanstack-query/infinite-list";

/**
 * The end of an audit table: the next page on request, and why the last request failed. The rows
 * already read stay above it, and the same press asks again.
 */
export function AuditListEnd({
	list,
	noun,
}: {
	list: Pick<MorePages, "hasMore" | "isLoadingMore" | "loadMoreError" | "onLoadMore">;
	/** What the rows are, in the plural: "events". */
	noun: string;
}) {
	const failed = list.loadMoreError != null;
	if (!list.hasMore && !failed) {
		return null;
	}
	return (
		<div className="flex flex-col items-center gap-2">
			{failed && (
				<p role="alert" className="text-sm text-muted-foreground">
					We could not load more {noun}.
				</p>
			)}
			<Button variant="outline" onClick={list.onLoadMore} disabled={list.isLoadingMore}>
				{list.isLoadingMore ? <Spinner className="mr-2 size-3.5" /> : null}
				Load more
			</Button>
		</div>
	);
}
