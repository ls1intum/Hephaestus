import { MessageSquareTextIcon } from "lucide-react";

import { PageHeader } from "@/components/layout/PageHeader";

import { ReviewFeed } from "./ReviewFeed";
import type { FeedbackResponseControls, ReviewFeedState } from "./reviews";

export interface ItemFeedbackPageProps {
	feed: ReviewFeedState;
	/** The reader's response to each piece of feedback; absent where they may not respond. */
	responses?: FeedbackResponseControls;
}

export function ItemFeedbackPage({ feed, responses }: ItemFeedbackPageProps) {
	return (
		<div className="flex max-w-4xl min-w-0 flex-col gap-6">
			<PageHeader
				icon={<MessageSquareTextIcon />}
				title="Your feedback on this work"
				description="Mark feedback addressed or not applicable, or disagree and say why. Workspace admins read your reason. While your disagreement stands, a later review of this work does not raise the same point again."
			/>
			<ReviewFeed
				feed={feed}
				responses={responses}
				skeletonRows={2}
				emptyTitle="Nothing here is about your work"
				emptyDescription="Reviews of this work recorded no results about you. The comment that brought you here was written for somebody else."
			/>
		</div>
	);
}
