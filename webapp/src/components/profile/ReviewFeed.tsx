import { PulseIcon } from "@primer/octicons-react";
import type { ReactNode } from "react";

import type { ProfileReview } from "@/api/types.gen";
import { InfiniteListEnd } from "@/components/common/InfiniteListEnd";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { rendersContent } from "@/lib/react-node";
import type { MorePages } from "@/runtime/tanstack-query/infinite-list";

import type { ReviewFeedState } from "./reviews";
import { ReviewTimeline, type ReviewTimelineProps } from "./ReviewTimeline";

export interface ReviewFeedProps extends Pick<
	ReviewTimelineProps,
	"responses" | "showPracticeName" | "initiallyOpen"
> {
	feed: ReviewFeedState;
	/**
	 * The reviews this surface shows, when they are not simply the feed's — a practice's own level
	 * narrows them to its results.
	 */
	reviews?: ProfileReview[];
	/** How many review cards the skeleton draws, so nothing jumps when the real ones land. */
	skeletonRows: number;
	/** Why there is nothing here, in this surface's words. */
	emptyTitle: string;
	emptyDescription: string;
	/** A way out of a narrowing that left the feed empty. */
	emptyAction?: ReactNode;
}

/**
 * One practice surface's review feed with its error, loading and empty states, in one home so the
 * rail cannot end one way on one surface and another way on the next.
 */
export function ReviewFeed({
	feed,
	reviews,
	skeletonRows,
	responses,
	showPracticeName,
	initiallyOpen,
	emptyTitle,
	emptyDescription,
	emptyAction,
}: ReviewFeedProps) {
	if (feed.status === "error") {
		return (
			<QueryErrorAlert
				error={feed.error}
				title="We could not load reviews"
				onRetry={feed.onRetry}
			/>
		);
	}
	if (feed.status === "loading") {
		return <ReviewFeedSkeleton rows={skeletonRows} />;
	}
	const shown = reviews ?? feed.reviews;
	// A narrowing can leave the pages read so far empty while earlier ones still hold its reviews, so
	// the empty state, which says nothing reached this surface at all, waits for the last page.
	if (shown.length === 0 && feed.hasMore) {
		return (
			<div className="flex flex-col items-start gap-2">
				<p className="text-sm text-muted-foreground">The latest reviews have no results here.</p>
				{emptyAction}
				<EarlierReviews {...feed} loadingRow={REVIEW_CARD_SKELETON} />
			</div>
		);
	}
	if (shown.length === 0) {
		return (
			<Empty>
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<PulseIcon />
					</EmptyMedia>
					<EmptyTitle>{emptyTitle}</EmptyTitle>
					<EmptyDescription>{emptyDescription}</EmptyDescription>
				</EmptyHeader>
				{rendersContent(emptyAction) && <EmptyContent>{emptyAction}</EmptyContent>}
			</Empty>
		);
	}
	return (
		<>
			<ReviewTimeline
				reviews={shown}
				responses={responses}
				showPracticeName={showPracticeName}
				initiallyOpen={initiallyOpen}
				continues={feed.hasMore}
			/>
			<EarlierReviews {...feed} loadingRow={REVIEW_CARD_SKELETON} />
		</>
	);
}

const REVIEW_CARD_SKELETON = <Skeleton className="h-24 w-full" />;

/** The earlier pages, in the words every review feed uses. */
export function EarlierReviews({ loadingRow, ...more }: MorePages & { loadingRow: ReactNode }) {
	return (
		<InfiniteListEnd
			{...more}
			moreLabel="View earlier reviews"
			failedLabel="We could not load earlier reviews."
			loadingRow={loadingRow}
		/>
	);
}

/** One block per review card the feed will show, so the surface does not jump when they land. */
export function ReviewFeedSkeleton({ rows }: { rows: number }) {
	return (
		<div className="flex flex-col gap-2.5" aria-busy="true">
			<span className="sr-only">Loading reviews…</span>
			{Array.from({ length: rows }, (_, index) => (
				<Skeleton key={index} className="h-24 w-full" />
			))}
		</div>
	);
}
