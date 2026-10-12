import { Card, CardContent } from "@/components/ui/card";

import type { OutcomeScope } from "./review-outcomes";
import { ReviewQueueNow, type ReviewQueueState } from "./ReviewQueueNow";
import { type FinishedInRange, ReviewTimeCard, type ReviewTimingState } from "./ReviewTimeCard";

export interface ReviewQueueCardProps {
	workspaceSlug: string;
	/** The queue as it stands now, whatever the range. */
	queue: ReviewQueueState;
	/** How long the range's reviews took. */
	timing: ReviewTimingState;
	/** The reviews of the range that finished, as the Reviews tile counts them. */
	finished: FinishedInRange;
	/** The range as the lists' day filters, so every figure opens the rows it counts. */
	scope: OutcomeScope;
	/** The range by name: "Last 30 days". */
	label: string;
	/** The range as words in a sentence: "in the last 30 days". */
	during: string;
}

/**
 * The one card of the Queue area, in two parts: on the left what the queue holds now, on the right,
 * under the range's name, how long the range's reviews took and what finished. Side by side with a
 * rule between them from a medium width, one above the other below it. Each part has its title and
 * its (i), as the other cards of the overview.
 */
export function ReviewQueueCard({
	workspaceSlug,
	queue,
	timing,
	finished,
	scope,
	label,
	during,
}: ReviewQueueCardProps) {
	return (
		<Card size="sm" className="w-full">
			<CardContent className="@container">
				<div className="grid grid-cols-1 gap-4 @3xl:grid-cols-2 @3xl:gap-0">
					<div className="min-w-0 @3xl:pr-6">
						<ReviewQueueNow workspaceSlug={workspaceSlug} state={queue} />
					</div>
					<div className="min-w-0 border-t pt-4 @3xl:border-t-0 @3xl:border-l @3xl:pt-0 @3xl:pl-6">
						<ReviewTimeCard
							workspaceSlug={workspaceSlug}
							state={timing}
							scope={scope}
							label={label}
							during={during}
							finished={finished}
						/>
					</div>
				</div>
			</CardContent>
		</Card>
	);
}
