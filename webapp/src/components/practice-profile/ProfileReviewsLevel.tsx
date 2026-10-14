import type { ReactNode } from "react";

import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { SelectFilter } from "@/components/common/SelectFilter";
import {
	DetailDrawerHeader,
	LevelPathRow,
} from "@/components/layout/detail-drawer/DetailDrawerHeader";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { TraceKindFilter } from "@/components/practice-trace/TraceKindFilter";
import { EarlierReviews } from "@/components/profile/ReviewFeed";
import type { ReviewFeedState } from "@/components/profile/reviews";
import { DrawerBody, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { Skeleton } from "@/components/ui/skeleton";
import { hasText } from "@/lib/text";

import {
	PROFILE_REVIEWS_PAGE_SIZE,
	REVIEW_TIMEFRAMES,
	REVIEW_TIMEFRAME_DEFS,
	REVIEWS_OF_YOUR_WORK,
	type ReviewTimeframe,
} from "./practice-profile-search";
import { ReviewsTable, ReviewsTableSkeleton } from "./ReviewsTable";

const TIMEFRAME_OPTIONS = REVIEW_TIMEFRAMES.map((value) => ({
	value,
	label: REVIEW_TIMEFRAME_DEFS[value].label,
}));

export interface ProfileReviewsLevelProps {
	nested?: boolean;
	path: LevelPath;
	feed: ReviewFeedState;
	/**
	 * Each review's position among the reviews of its work, by review id; absent while the feed does
	 * not hold every review, so no row claims a count it cannot see.
	 */
	positions?: Map<string, number>;
	onOpenReview: (reviewId: string) => void;
	/** The review whose level is open over this one. */
	openReviewId?: string;
	/** The item kind the list is narrowed to, or `undefined` for all of it. */
	kind?: string;
	onKindChange: (kind: string | undefined) => void;
	/** How far back the list reaches, or `undefined` for every review. */
	since?: ReviewTimeframe;
	onSinceChange: (since: ReviewTimeframe | undefined) => void;
	/** Asks for a review of an item; absent where the reader may not ask. */
	onReviewNow?: (itemId: string) => void;
	/** The item an ask is in flight about. */
	requesting?: string;
}

/**
 * The reviews that recorded something about the reader's own work, newest first, as a level over
 * the practice profile. The server lists a review once it records something about the reader, so
 * one still running may not be here yet.
 */
export function ProfileReviewsLevel({
	nested,
	path,
	feed,
	positions,
	onOpenReview,
	openReviewId,
	kind,
	onKindChange,
	since,
	onSinceChange,
	onReviewNow,
	requesting,
}: ProfileReviewsLevelProps) {
	const reviews = feed.status === "ready" ? feed.reviews : [];
	const filtered = hasText(kind) || since !== undefined;

	let body: ReactNode;
	switch (feed.status) {
		case "error": {
			body = (
				<QueryErrorAlert
					error={feed.error}
					title="We could not load reviews of your work"
					onRetry={feed.onRetry}
				/>
			);
			break;
		}
		case "loading": {
			body = <ReviewsTableSkeleton rows={PROFILE_REVIEWS_PAGE_SIZE} />;
			break;
		}
		case "ready": {
			body = (
				<>
					<ReviewsTable
						reviews={feed.reviews}
						filtered={filtered}
						onOpenReview={onOpenReview}
						openReviewId={openReviewId}
						onReviewNow={onReviewNow}
						requesting={requesting}
						positions={positions}
					/>
					<EarlierReviews {...feed} loadingRow={<Skeleton className="h-12 w-full" />} />
				</>
			);
			break;
		}
	}

	return (
		<>
			<DetailDrawerHeader nested={nested} dismiss="inPath">
				<div className="flex min-w-0 flex-1 flex-col gap-2">
					<LevelPathRow {...path} current="Reviews" />
					<DrawerTitle className="text-2xl font-semibold tracking-tight break-words">
						{REVIEWS_OF_YOUR_WORK}
					</DrawerTitle>
					<DrawerDescription className="max-w-2xl">
						Reviews that recorded something about your work, newest first. A review that is still
						running appears once it records something.
					</DrawerDescription>
				</div>
			</DetailDrawerHeader>
			<DrawerBody className="flex flex-col gap-3 pt-2">
				<FilterToolbar
					hasFilter={filtered}
					onReset={() => {
						onKindChange(undefined);
						onSinceChange(undefined);
					}}
				>
					<TraceKindFilter
						seen={reviews.map((entry) => entry.review.item.kind)}
						value={kind}
						onChange={onKindChange}
					/>
					<SelectFilter
						label="Timeframe"
						allLabel="All time"
						options={TIMEFRAME_OPTIONS}
						value={since}
						onChange={onSinceChange}
					/>
				</FilterToolbar>
				{body}
			</DrawerBody>
		</>
	);
}
