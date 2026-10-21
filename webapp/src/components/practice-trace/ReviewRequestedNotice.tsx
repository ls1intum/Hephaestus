import type { ReactElement } from "react";

import { InlineLink } from "@/components/common/InlineLink";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export interface ReviewRequestedNoticeProps {
	/**
	 * The link to the review the request created, drawn by the route for its own surface — such as a
	 * `DetailStackLink` to that review's level.
	 */
	review: ReactElement;
}

/**
 * The answer to an accepted ask: a new review exists. It says nothing of that review's progress,
 * which only its own level reads, and leaves the record on screen as it is until the reader opens
 * the new one.
 *
 * On the page rather than only in a toast: a toast is gone before the reader looks for the way to
 * the review. The toast already announced the answer, so this is a note, not a live region.
 */
export function ReviewRequestedNotice({ review }: ReviewRequestedNoticeProps) {
	return (
		<Alert role="note">
			<AlertTitle>Review requested</AlertTitle>
			<AlertDescription>
				<InlineLink render={review}>View requested review</InlineLink>
			</AlertDescription>
		</Alert>
	);
}
