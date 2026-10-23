import type { ReactNode } from "react";

import type {
	PracticeStanding,
	ReviewedPractice,
	ResultList,
	ProfileReview,
} from "@/api/types.gen";
import {
	PracticeTabsList,
	PracticeTabsRail,
	PracticeTabsSkeleton,
	PracticeTabsTrigger,
} from "@/components/common/practice-tabs";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { LevelHeader } from "@/components/layout/detail-drawer/LevelHeader";
import { Section } from "@/components/layout/Section";
import { PracticeGuideMarkdown } from "@/components/practice-guidance/PracticeGuideMarkdown";
import {
	type PracticeGuidanceState,
	PracticeIntro,
} from "@/components/practice-guidance/PracticeIntro";
import { isOpenFeedback } from "@/components/practice-vocabulary/feedback-state-defs";
import {
	type FeedbackRatingProps,
	PracticeFeedbackCard,
	type PracticeFeedbackCardEntry,
	PracticeFeedbackCardSkeleton,
} from "@/components/practice-vocabulary/PracticeFeedbackCard";
import { StandingBadge, TrendNote } from "@/components/practice-vocabulary/StandingBadge";
import { StandingBasisNote } from "@/components/practice-vocabulary/StandingBasisNote";
import { ReviewFeed, ReviewFeedSkeleton } from "@/components/profile/ReviewFeed";
import {
	EMPTY_REVIEW_FEED,
	type FeedbackResponseControls,
	type ReviewFeedState,
} from "@/components/profile/reviews";
import { DrawerBody } from "@/components/ui/drawer";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent } from "@/components/ui/tabs";
import { problemStatusOf } from "@/lib/problem-detail";
import { hasText } from "@/lib/text";

import { NeverAppliedBadge } from "./FoldedPractices";
import { newestFirst } from "./practice-feedback-cards";
import { NEVER_APPLIED } from "./practice-folds";
import { FeedbackEmpty, LabelledBlock } from "./practice-profile-blocks";
import { DEFAULT_PRACTICE_TAB, PRACTICE_TABS, type PracticeTab } from "./practice-profile-search";
import { requestedReviewNote } from "./requested-review-note";

const TAB_LABELS: Record<PracticeTab, string> = {
	feedback: "Feedback",
	results: "Observations",
	guide: "Guide",
};

export interface PracticeDetailLevelProps {
	nested?: boolean;
	/**
	 * Where the level sits, from the drawer: the path is what names the group this practice is in.
	 */
	path: LevelPath;
	/** Returns to the group's level under this one; a card's group name is a link only with it. */
	onOpenGroup?: () => void;
	/**
	 * The practice, as the standings carry it: the catalog's words and where the reader stands.
	 * Absent for a practice with no standing, which the level shows from its words and guidance
	 * alone.
	 */
	practice?: PracticeStanding;
	/** The name and words of a practice with no standing, from the workspace's catalog. */
	described?: Pick<ReviewedPractice, "name" | "whyItMatters" | "whatGoodLooksLike">;
	/**
	 * The practice's guidance, which loads on its own after the standing: the picture joins the
	 * introduction, and a guide adds the Guide tab.
	 */
	guidance: PracticeGuidanceState;
	/**
	 * The tab asked for, from the route's `practiceTab` search param. Guide on a practice with no
	 * guide shows the feedback.
	 */
	tab: PracticeTab;
	onTabChange?: (tab: PracticeTab) => void;
	feed?: ReviewFeedState;
	renderObservation?: (result: ResultList) => ReactNode;
	selectedObservationId?: string;
	selectedObservation?: ReactNode;
	/** The developer's practice feedback; the level shows the cards written about this practice. */
	feedbackCards?: PracticeFeedbackCardEntry[];
	/**
	 * Wires one card's rating to wherever the ratings are kept; without it the cards cannot be
	 * rated.
	 */
	ratingProps?: (feedbackId: string) => FeedbackRatingProps;
	/** How many rows the feed draws while its first page loads. */
	skeletonRows: number;
	/** The reader's responses to their feedback, from the route; without them none can be given. */
	responses?: FeedbackResponseControls;
	isLoading: boolean;
	error?: unknown;
	onRetry?: () => void;
	/** Every review so far found that the practice did not apply; the level says so. */
	neverApplied?: boolean;
}

/**
 * The feedback written about one practice: the one open card it carries (a practice has one live
 * card, so the newest is the one), and the cards the work resolved, newest first.
 */
function feedbackCardsOf(cards: PracticeFeedbackCardEntry[], practiceSlug: string) {
	const own = newestFirst(cards.filter((card) => card.practiceSlug === practiceSlug));
	return {
		open: own.find((card) => isOpenFeedback(card.state)),
		resolved: own.filter((card) => !isOpenFeedback(card.state) && card.state !== "withdrawn"),
	};
}

const NO_CARDS: PracticeFeedbackCardEntry[] = [];

const NO_WORDS = { whyItMatters: null, whatGoodLooksLike: null };

const GUIDANCE_ONLY = "This practice offers guidance without automated review.";

export function PracticeDetailLevel({
	nested,
	path,
	onOpenGroup,
	practice,
	described,
	guidance,
	tab,
	onTabChange,
	feed = EMPTY_REVIEW_FEED,
	renderObservation,
	selectedObservationId,
	selectedObservation,
	feedbackCards = NO_CARDS,
	ratingProps,
	skeletonRows,
	responses,
	isLoading,
	error,
	onRetry,
	neverApplied = false,
}: PracticeDetailLevelProps) {
	const reviews =
		feed.status === "ready" && practice
			? feed.reviews
					.map((review) => ({
						...review,
						results: review.results.filter((result) => result.practiceSlug === practice.slug),
					}))
					.filter((review) => review.results.length > 0)
			: [];
	const feedback = practice
		? feedbackCardsOf(feedbackCards, practice.slug)
		: { open: undefined, resolved: [] };
	const feedbackCount = (feedback.open ? 1 : 0) + feedback.resolved.length;
	// Feedback arrives whole; paged results cannot supply an exact tab count.
	const counts: Partial<Record<PracticeTab, number>> = {
		results:
			feed.status === "ready" && !feed.hasMore && selectedObservationId === undefined
				? reviews.reduce((count, review) => count + review.results.length, 0)
				: undefined,
		feedback: feedbackCount,
	};
	const guide = guidance.status === "ready" ? (guidance.guide ?? undefined) : undefined;
	// The Guide tab exists only once a guide has arrived; until then, or without one, a link to it
	// lands on the feedback.
	const tabs = PRACTICE_TABS.filter((candidate) => candidate !== "guide" || guide !== undefined);
	const shownTab = tabs.includes(tab) ? tab : DEFAULT_PRACTICE_TAB;

	let body: ReactNode;
	if (isLoading) {
		body = (
			// The tabs and the one they open on, as they will be laid out.
			<div className="flex flex-col gap-4">
				<PracticeTabsSkeleton tabs={tabs.length} />
				{shownTab === "results" ? (
					<ReviewFeedSkeleton rows={skeletonRows} />
				) : (
					<div className="flex flex-col gap-2.5" aria-busy="true">
						<span className="sr-only">Loading feedback…</span>
						<PracticeFeedbackCardSkeleton />
					</div>
				)}
			</div>
		);
	} else if (error != null) {
		body = (
			<QueryErrorAlert
				error={error}
				title={
					practice
						? `We could not load your standing for ${practice.name}`
						: "We could not load this practice"
				}
				onRetry={onRetry}
			/>
		);
	} else if (practice || selectedObservation !== undefined) {
		const requestedNote = practice ? requestedReviewNote(practice, reviews) : undefined;
		body = (
			<>
				{neverApplied && <p className="max-w-2xl text-sm">{NEVER_APPLIED.description}</p>}
				<PracticeIntro practice={practice ?? described ?? NO_WORDS} guidance={guidance} />
				<Tabs
					value={shownTab}
					onValueChange={(next) => {
						const chosen = tabs.find((candidate) => candidate === next);
						if (chosen) {
							onTabChange?.(chosen);
						}
					}}
					className="gap-4"
				>
					<PracticeTabsRail>
						<PracticeTabsList aria-label="Practice">
							{tabs.map((candidate) => (
								<PracticeTabsTrigger key={candidate} value={candidate} count={counts[candidate]}>
									{TAB_LABELS[candidate]}
								</PracticeTabsTrigger>
							))}
						</PracticeTabsList>
					</PracticeTabsRail>
					<TabsContent value="feedback" className="min-w-0">
						<PracticeFeedbackSection
							feedback={feedback}
							ratingProps={ratingProps}
							onOpenGroup={onOpenGroup}
						/>
					</TabsContent>
					<TabsContent value="results" className="min-w-0">
						<Section
							size="lg"
							title="Observations"
							description="Reviews of your work that reached this practice, newest first: what each recorded, the evidence, and the next step."
						>
							{hasText(requestedNote) && <p className="max-w-2xl text-sm">{requestedNote}</p>}
							<ObservationHistory
								feed={feed}
								reviews={reviews}
								selectedId={selectedObservationId}
								selected={selectedObservation}
								renderObservation={renderObservation}
								skeletonRows={skeletonRows}
								responses={responses}
							/>
						</Section>
					</TabsContent>
					{guide && (
						<TabsContent value="guide" className="min-w-0">
							{/* `h3`: the guide's own headings are `h4`, and the outline may not skip a level. */}
							<Section size="lg" level={3} title="Practice guide">
								<PracticeGuideMarkdown guide={guide} />
							</Section>
						</TabsContent>
					)}
				</Tabs>
			</>
		);
	} else if (guidance.status === "ready") {
		body = (
			<>
				<p className="max-w-2xl text-sm">{GUIDANCE_ONLY}</p>
				<PracticeIntro practice={described ?? NO_WORDS} guidance={guidance} />
				{guide && (
					// The guide uses h4 headings; its parent must be h3 to preserve the outline.
					<Section size="lg" level={3} title="Practice guide">
						<PracticeGuideMarkdown guide={guide} />
					</Section>
				)}
			</>
		);
	} else if (guidance.status === "loading") {
		body = (
			<div className="flex max-w-2xl flex-col gap-2" aria-busy="true">
				<span className="sr-only">Loading this practice…</span>
				<Skeleton className="h-5 w-full" />
				<Skeleton className="h-5 w-3/4" />
			</div>
		);
	} else if (problemStatusOf(guidance.error) === 404) {
		body = (
			<p className="text-sm text-muted-foreground">
				We could not find this practice. Check the link, or ask a workspace admin whether this
				workspace reviews it.
			</p>
		);
	} else {
		// Any other failure says nothing about whether the practice exists, so it offers the retry.
		body = (
			<QueryErrorAlert
				error={guidance.error}
				title="We could not load this practice"
				onRetry={guidance.onRetry}
			/>
		);
	}

	return (
		<>
			<LevelHeader
				nested={nested}
				path={path}
				current="Practice"
				title={practice?.name ?? described?.name ?? "Practice"}
				chips={
					practice && <PracticeStandingChips practice={practice} neverApplied={neverApplied} />
				}
			/>
			<DrawerBody className="flex flex-col gap-6 pt-2">{body}</DrawerBody>
		</>
	);
}

/** Where the reader stands on the practice, under the level's title. */
function PracticeStandingChips({
	practice,
	neverApplied,
}: {
	practice: PracticeStanding;
	neverApplied: boolean;
}) {
	// A practice that never applied has no trend, as its folded row shows.
	if (neverApplied) {
		return <NeverAppliedBadge scope="practice" />;
	}
	return (
		<>
			<StandingBadge
				standing={practice.standing}
				scope="practice"
				support={practice.trendSupport ?? undefined}
			/>
			<TrendNote
				direction={practice.direction ?? undefined}
				support={practice.trendSupport ?? undefined}
				scope="practice"
			/>
			<StandingBasisNote basis={practice.basis} />
		</>
	);
}

/** The Feedback tab: the practice's one open card, then the cards the work resolved. */
function PracticeFeedbackSection({
	feedback,
	ratingProps,
	onOpenGroup,
}: {
	feedback: ReturnType<typeof feedbackCardsOf>;
	ratingProps?: (feedbackId: string) => FeedbackRatingProps;
	onOpenGroup?: () => void;
}) {
	// No "Learn more" on a card here: the practice's introduction already leads this level.
	const feedbackCard = (card: PracticeFeedbackCardEntry) => (
		<PracticeFeedbackCard
			key={card.feedbackId}
			card={card}
			{...ratingProps?.(card.feedbackId)}
			onOpenGroup={onOpenGroup && (() => onOpenGroup())}
		/>
	);
	return (
		<Section
			size="lg"
			title="Feedback"
			description="Feedback about patterns in your work: the open card, then the ones that resolved, newest first."
		>
			{feedback.open === undefined && feedback.resolved.length === 0 ? (
				<FeedbackEmpty description="Feedback appears here once a review sees the same pattern in your work more than once. Feedback posted on a single piece of work stays there. Open its observation to find recorded comment links." />
			) : (
				<div className="flex flex-col gap-6">
					{feedback.open && (
						<LabelledBlock label="Current feedback" as="h3" className="flex flex-col gap-2.5">
							{feedbackCard(feedback.open)}
						</LabelledBlock>
					)}
					{feedback.resolved.length > 0 && (
						<LabelledBlock label="Resolved feedback" as="h3" className="flex flex-col gap-2.5">
							{feedback.resolved.map(feedbackCard)}
						</LabelledBlock>
					)}
				</div>
			)}
		</Section>
	);
}

/** A linked observation does not depend on how far its surrounding history has loaded. */
function ObservationHistory({
	feed,
	reviews,
	selectedId,
	selected,
	renderObservation,
	skeletonRows,
	responses,
}: {
	feed: ReviewFeedState;
	reviews: ProfileReview[];
	selectedId?: string;
	selected?: ReactNode;
	renderObservation?: (result: ResultList) => ReactNode;
	skeletonRows: number;
	responses?: FeedbackResponseControls;
}) {
	const remaining = reviews
		.map((review) => ({
			...review,
			results: review.results.filter((result) => result.id !== selectedId),
		}))
		.filter((review) => review.results.length > 0);
	const onlySelected =
		selected !== undefined &&
		feed.status === "ready" &&
		remaining.length === 0 &&
		!feed.hasMore &&
		!feed.isLoadingMore &&
		feed.refreshFailure === undefined;
	return (
		<>
			{selected}
			{!onlySelected && (
				<ReviewFeed
					feed={feed}
					reviews={remaining}
					skeletonRows={skeletonRows}
					responses={responses}
					renderObservation={renderObservation}
					showPracticeName={false}
					initiallyOpen="newest"
					emptyTitle={selected === undefined ? "No observations yet" : "No other observations yet"}
					emptyDescription="They appear after Hephaestus reviews your work."
				/>
			)}
		</>
	);
}
