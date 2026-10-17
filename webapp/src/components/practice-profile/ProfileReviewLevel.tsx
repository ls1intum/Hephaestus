import { ExternalLinkIcon, PlayIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type { GroupRef, ProfileReviewDetail } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import type { PanelState } from "@/components/common/panel-state";
import {
	PracticeTabsList,
	PracticeTabsRail,
	PracticeTabsSkeleton,
	PracticeTabsTrigger,
} from "@/components/common/practice-tabs";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useNow } from "@/components/common/use-now";
import { reviewedWorkIcon } from "@/components/icons/reviewed-work-icon";
import {
	DetailDrawerHeader,
	LevelPathRow,
} from "@/components/layout/detail-drawer/DetailDrawerHeader";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { RequestedReviewTag, ReviewOrdinalTag } from "@/components/practice-trace/review-tags";
import {
	type ReviewPracticeFilters,
	ReviewPracticeTable,
	ReviewPracticeTableSkeleton,
} from "@/components/practice-trace/ReviewPracticeTable";
import { TraceEventTimeline } from "@/components/practice-trace/TraceEventTimeline";
import {
	isActiveReview,
	OWN_REVIEW_STATUS_DEFS,
} from "@/components/practice-vocabulary/review-status-defs";
import { OwnFeedbackList } from "@/components/profile/OwnFeedbackList";
import type { FeedbackResponseControls } from "@/components/profile/reviews";
import { Button, buttonVariants } from "@/components/ui/button";
import { DrawerBody, DrawerTitle } from "@/components/ui/drawer";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsContent } from "@/components/ui/tabs";
import { formatDayTime } from "@/lib/dates";
import { itemName } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { LabelledBlock } from "./practice-profile-blocks";
import {
	REVIEW_SELECTION_CLEARED,
	REVIEW_TABS,
	reviewLevel,
	type ReviewTab,
} from "./practice-profile-search";

export type ProfileReviewDetailState = PanelState<{ detail: ProfileReviewDetail }>;

export interface ProfileReviewLevelProps {
	nested?: boolean;
	path: LevelPath;
	state: ProfileReviewDetailState;
	positionOnWork?: number;
	groupOf: (practiceSlug: string) => GroupRef | undefined;
	onOpenPractice: (practiceSlug: string) => void;
	tab: ReviewTab;
	onTabChange: (tab: ReviewTab) => void;
	filters: ReviewPracticeFilters;
	onFiltersChange: (filters: ReviewPracticeFilters) => void;
	focusResultId?: string;
	/**
	 * One result in full, with its problems, open questions and evidence, which the route reads when
	 * its row opens. Without it a row shows the result's list facts alone.
	 */
	renderResult?: (resultId: string) => ReactNode;
	eventLabel: (name: string) => string;
	/** The reader's responses to the feedback this review gave them; absent where they may not respond. */
	responses?: FeedbackResponseControls;
	onReviewNow?: (itemId: string) => void;
	requesting?: string;
	/**
	 * Why the last ask from this level started nothing, drawn by the route with the fix only an
	 * admin can apply. It is said here rather than in a toast: it is the answer they asked for.
	 */
	refusal?: ReactNode;
}

const SKELETON_ROWS = 4;

const TAB_LABELS: Record<ReviewTab, string> = {
	practices: "Every practice",
	noticed: "What we noticed",
};

/**
 * Swapped in for this review rather than opened over it: the review level is read off the stack by
 * kind, so a second review over the first would never be the one shown.
 */
function earlierReviewLink(reviewId: string) {
	return (
		<InlineLink
			render={
				<DetailStackLink
					entry={reviewLevel(reviewId)}
					swap
					levelSearch={REVIEW_SELECTION_CLEARED}
				/>
			}
			className="text-sm"
		>
			Open the earlier review
		</InlineLink>
	);
}

export function ProfileReviewLevel({
	nested,
	path,
	state,
	positionOnWork,
	groupOf,
	onOpenPractice,
	tab,
	onTabChange,
	filters,
	onFiltersChange,
	focusResultId,
	renderResult,
	eventLabel,
	responses,
	onReviewNow,
	requesting,
	refusal,
}: ProfileReviewLevelProps) {
	const detail = state.status === "ready" ? state.detail : undefined;

	let body: ReactNode;
	if (state.status === "error") {
		body = (
			<QueryErrorAlert
				error={state.error}
				title="We could not load this review"
				onRetry={state.onRetry}
			/>
		);
	} else if (state.status === "loading") {
		body = (
			<div className="flex flex-col gap-4">
				<PracticeTabsSkeleton tabs={REVIEW_TABS.length} />
				<ReviewPracticeTableSkeleton rows={SKELETON_ROWS} />
			</div>
		);
	} else {
		const { review, results, feedback, trace } = state.detail;
		const counts: Record<ReviewTab, number> = {
			practices: results.length,
			noticed: trace.events.length,
		};
		body = (
			<>
				{feedback.length > 0 && (
					<LabelledBlock label="Feedback from this review" className="flex flex-col gap-2.5">
						<OwnFeedbackList feedback={feedback} responses={responses} />
					</LabelledBlock>
				)}
				<Tabs
					value={tab}
					onValueChange={(next) => {
						const chosen = REVIEW_TABS.find((candidate) => candidate === next);
						if (chosen) {
							onTabChange(chosen);
						}
					}}
					className="gap-4"
				>
					<PracticeTabsRail>
						<PracticeTabsList aria-label="This review">
							{REVIEW_TABS.map((candidate) => (
								<PracticeTabsTrigger key={candidate} value={candidate} count={counts[candidate]}>
									{TAB_LABELS[candidate]}
								</PracticeTabsTrigger>
							))}
						</PracticeTabsList>
					</PracticeTabsRail>
					<TabsContent value="practices" className="min-w-0">
						<ReviewPracticeTable
							results={results}
							reviewId={review.id}
							groupOf={groupOf}
							filters={filters}
							onFiltersChange={onFiltersChange}
							onOpenPractice={onOpenPractice}
							earlierReviewLink={earlierReviewLink}
							renderResult={renderResult}
							focusPracticeSlug={
								results.find((result) => result.id === focusResultId)?.practiceSlug
							}
							emptyMessage={
								isActiveReview(review.status)
									? "Results appear here as the review reaches each practice."
									: "This review recorded no result about you, so there is nothing to list here."
							}
						/>
					</TabsContent>
					<TabsContent value="noticed" className="min-w-0">
						<TraceEventTimeline events={trace.events} eventLabel={eventLabel} />
					</TabsContent>
				</Tabs>
			</>
		);
	}

	return (
		<>
			<DetailDrawerHeader nested={nested} dismiss="inPath">
				<ReviewHead
					path={path}
					detail={detail}
					positionOnWork={positionOnWork}
					onReviewNow={onReviewNow}
					requesting={requesting}
					refusal={refusal}
				/>
			</DetailDrawerHeader>
			<DrawerBody className="flex flex-col gap-4 pt-2">{body}</DrawerBody>
		</>
	);
}

interface ReviewHeadProps {
	path: LevelPath;
	detail: ProfileReviewDetail | undefined;
	positionOnWork: number | undefined;
	onReviewNow?: (itemId: string) => void;
	requesting?: string;
	refusal?: ReactNode;
}

function ReviewHead({
	path,
	detail,
	positionOnWork,
	onReviewNow,
	requesting,
	refusal,
}: ReviewHeadProps) {
	const today = new Date(useNow());
	const review = detail?.review;
	const item = review?.item;
	const at = review?.completedAt ?? review?.createdAt;
	const ItemIcon = reviewedWorkIcon(item?.kind, item?.provider ?? undefined);
	const isRequesting = item !== undefined && requesting === item.id;
	const canAsk = review?.kind === "ITEM" && onReviewNow !== undefined;

	return (
		<div className="flex min-w-0 flex-1 flex-col gap-2">
			<LevelPathRow {...path} current="Review" />
			<p className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground">
				<ItemIcon className="size-3.5 shrink-0" />
				<span className="truncate">
					{item ? itemName(item) : "Reviewed work"}
					{hasText(item?.container) && `, ${item.container}`}
				</span>
			</p>
			<DrawerTitle className="text-2xl font-semibold tracking-tight break-words">
				{hasText(item?.title) ? item.title : (item?.label ?? "Review")}
			</DrawerTitle>
			<div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm text-muted-foreground">
				{at && (
					<span>
						Reviewed{" "}
						<time dateTime={at.toISOString()} className="text-foreground">
							{formatDayTime(at, today)}
						</time>
					</span>
				)}
				{review && <StatusBadge def={OWN_REVIEW_STATUS_DEFS[review.status]} />}
				{review?.origin === "REQUEST" && <RequestedReviewTag />}
				<ReviewOrdinalTag position={positionOnWork} />
			</div>
			{item && (hasText(item.url) || canAsk) && (
				<div className="flex flex-wrap items-center gap-2 pt-1">
					{hasText(item.url) && (
						<a
							href={item.url}
							target="_blank"
							rel="noopener noreferrer"
							className={cn(buttonVariants({ variant: "outline" }))}
						>
							<ExternalLinkIcon aria-hidden data-icon="inline-start" />
							Open the original
							<span className="sr-only"> (opens in a new tab)</span>
						</a>
					)}
					{canAsk && (
						<Button type="button" disabled={isRequesting} onClick={() => onReviewNow(item.id)}>
							{isRequesting ? (
								<Spinner data-icon="inline-start" />
							) : (
								<PlayIcon aria-hidden data-icon="inline-start" />
							)}
							{isRequesting ? "Requesting review…" : "Request review"}
						</Button>
					)}
				</div>
			)}
			{refusal}
		</div>
	);
}
