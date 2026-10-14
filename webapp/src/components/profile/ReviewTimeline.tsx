import type { ProfileReview } from "@/api/types.gen";

import { ReviewCard } from "./ReviewCard";
import type { FeedbackResponseControls } from "./reviews";

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
}

export function ReviewTimeline({
	reviews,
	responses,
	showPracticeName = true,
	initiallyOpen = "all",
	continues = false,
}: ReviewTimelineProps) {
	return (
		<ol className="flex min-w-0 flex-col" aria-label="Reviews">
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
		</ol>
	);
}

/** Every card open, or only the newest card's first result. */
function cardInitiallyOpen(
	initiallyOpen: "all" | "newest",
	index: number,
): "all" | "first" | "none" {
	if (initiallyOpen === "all") {
		return "all";
	}
	return index === 0 ? "first" : "none";
}
