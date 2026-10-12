import { MessageSquareTextIcon } from "lucide-react";

import { PageHeader } from "@/components/layout/PageHeader";

import type { ResultControls, ReviewRunFeedState } from "./review-runs";
import { ReviewRunFeed } from "./ReviewRunFeed";

export interface ReviewedWorkFeedbackPageProps {
	/** The reader's own reviews of the work, newest first. */
	feed: ReviewRunFeedState;
	/** Answers a result; absent where this reader may not answer, which leaves no controls. */
	results?: ResultControls;
}

/**
 * Where a comment Hephaestus posted on a pull request, merge request or issue sends its reader: their
 * own reviews of that work, every practice together, each result with its answers. Somebody the
 * comment was not about finds nothing of theirs, and is told so rather than shown anybody else's.
 */
export function ReviewedWorkFeedbackPage({ feed, results }: ReviewedWorkFeedbackPageProps) {
	return (
		<div className="flex max-w-4xl min-w-0 flex-col gap-6">
			<PageHeader
				icon={<MessageSquareTextIcon />}
				title="Your feedback on this work"
				description="Mark a result addressed or not applicable, or dispute it and say why. Workspace admins read a dispute’s explanation. While it stands, a later review of this work does not raise the same point again."
			/>
			<ReviewRunFeed
				feed={feed}
				results={results}
				skeletonRows={2}
				emptyTitle="Nothing here is about your work"
				emptyDescription="Reviews of this work recorded no results about you. The comment that brought you here was written for somebody else."
			/>
		</div>
	);
}
