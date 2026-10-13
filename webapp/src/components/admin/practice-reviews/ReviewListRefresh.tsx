import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { RefreshFailure } from "@/runtime/tanstack-query/infinite-list";

/** The failed read beside the rows it could not update, including a loaded empty list. */
export function ReviewListRefresh({
	failure,
	noun,
}: {
	failure: RefreshFailure | undefined;
	noun: string;
}) {
	return failure === undefined ? null : (
		<QueryErrorAlert title={`These ${noun} may be out of date`} {...failure} />
	);
}
