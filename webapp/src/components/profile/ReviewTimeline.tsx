import type { ProfileReview } from "@/api/types.gen";

import { ReviewCard } from "./ReviewCard";
import type { FeedbackResponseControls } from "./reviews";
import { TimelineItemSkeleton } from "./TimelineItem";

export interface ReviewTimelineProps {
	reviews: ProfileReview[];
	/** The reader's response to each piece of feedback, handed to every card. */
	responses?: FeedbackResponseControls;
	/** Off on a practice's own level, where every row is that practice and no row repeats it. */
	showPracticeName?: boolean;
	/**
	 * Which results arrive open. `"all"` is a feed the reader skims for what it found;
	 * `"newest"` opens the newest review's first row alone and leaves every earlier one to a press,
	 * which is what a practice's own level wants — its rows are all the same practice, so the
	 * latest is the one the reader came for and the rest are its history.
	 */
	initiallyOpen?: "all" | "newest";
	/** True while earlier reviews exist below the fold: the rail then ends in a dashed tail. */
	continues?: boolean;
	/** Placeholder cards after the last review while the next page loads; the list is then busy. */
	loadingMore?: number;
}

export function ReviewTimeline({
	reviews,
	responses,
	showPracticeName = true,
	initiallyOpen = "all",
	continues = false,
	loadingMore = 0,
}: ReviewTimelineProps) {
	return (
		<ol
			className="flex w-full min-w-0 flex-col"
			aria-label="Reviews"
			aria-busy={loadingMore > 0 || undefined}
		>
			{reviews.map((review, index) => (
				<ReviewCard
					key={review.review.id}
					review={review}
					responses={responses}
					showPracticeName={showPracticeName}
					initiallyOpen={cardInitiallyOpen(initiallyOpen, index)}
					tailContinues={continues && index === reviews.length - 1}
				/>
			))}
			{Array.from({ length: loadingMore }, (_, index) => (
				<TimelineItemSkeleton key={index} />
			))}
		</ol>
	);
}

function cardInitiallyOpen(
	initiallyOpen: "all" | "newest",
	index: number,
): "all" | "first" | "none" {
	if (initiallyOpen === "all") {
		return "all";
	}
	return index === 0 ? "first" : "none";
}
