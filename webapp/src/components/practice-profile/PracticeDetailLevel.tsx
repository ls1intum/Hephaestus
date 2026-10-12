import type { ReactNode } from "react";

import type { PracticeStanding } from "@/api/types.gen";
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
import {
	EMPTY_REVIEW_RUN_FEED,
	type ResultControls,
	type ReviewRunFeedState,
} from "@/components/profile/review-runs";
import { ReviewRunFeed, ReviewRunFeedSkeleton } from "@/components/profile/ReviewRunFeed";
import { DrawerBody } from "@/components/ui/drawer";
import { Tabs, TabsContent } from "@/components/ui/tabs";
import { hasText } from "@/lib/text";

import { NeverAppliedBadge } from "./FoldedPractices";
import { newestFirst } from "./practice-feedback-cards";
import { NEVER_APPLIED } from "./practice-folds";
import { FeedbackEmpty, LabelledBlock } from "./practice-profile-blocks";
import { DEFAULT_PRACTICE_TAB, PRACTICE_TABS, type PracticeTab } from "./practice-profile-search";
import { requestedReviewNote } from "./requested-review-note";

const TAB_LABELS: Record<PracticeTab, string> = {
	feedback: "Feedback",
	results: "Results",
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
	/** The practice, as the standings carry it: the catalog's words and where the reader stands. */
	practice?: PracticeStanding;
	/**
	 * The practice's picture and guide, which load on their own after the standing: the picture
	 * joins the introduction, and a guide adds the Guide tab.
	 */
	guidance: PracticeGuidanceState;
	/**
	 * The tab asked for, from the route's `practiceTab` search param. Guide on a practice with no
	 * guide shows the feedback.
	 */
	tab: PracticeTab;
	onTabChange?: (tab: PracticeTab) => void;
	feed?: ReviewRunFeedState;
	/** The developer's practice feedback; the level shows the cards written about this practice. */
	feedbackCards?: PracticeFeedbackCardEntry[];
	/**
	 * Wires one card's rating to wherever the ratings are kept; without it the cards cannot be
	 * rated.
	 */
	ratingProps?: (feedbackId: string) => FeedbackRatingProps;
	/** How many rows the feed draws while its first page loads. */
	skeletonRows: number;
	/** The reader's response to a result, from the route. */
	results?: ResultControls;
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

/**
 * One practice as the level over its group: the practice's introduction, then its tabs — the
 * feedback written about it, which the level opens on, the results of the reviews of the reader's work,
 * and the practice guide when the practice has one. It is the deepest level, so it is the one that
 * carries the results — the newest open once the reader turns to them and every earlier one a
 * press away, each opening and closing on its own.
 */
export function PracticeDetailLevel({
	nested,
	path,
	onOpenGroup,
	practice,
	guidance,
	tab,
	onTabChange,
	feed = EMPTY_REVIEW_RUN_FEED,
	feedbackCards = NO_CARDS,
	ratingProps,
	skeletonRows,
	results,
	isLoading,
	error,
	onRetry,
	neverApplied = false,
}: PracticeDetailLevelProps) {
	// The server narrows which runs reached the practice; the level narrows the same way and shows
	// only that practice's results instead of everything the run found.
	const runs =
		feed.status === "ready" && practice
			? feed.runs
					.map((run) => ({
						...run,
						practiceResults: run.practiceResults.filter(
							(result) => result.practiceSlug === practice.slug,
						),
					}))
					.filter((run) => run.practiceResults.length > 0)
			: [];
	const feedback = practice
		? feedbackCardsOf(feedbackCards, practice.slug)
		: { open: undefined, resolved: [] };
	const feedbackCount = (feedback.open ? 1 : 0) + feedback.resolved.length;
	// What each tab counts is what it can show. The feedback cards arrive in one piece, so that
	// count is always known; the results are paged, so a number taken from the loaded pages
	// would be short while earlier runs are still a press away, and would grow under the reader as
	// they press. An unnumbered tab says less and nothing wrong, so the count waits for the last
	// page.
	const counts: Partial<Record<PracticeTab, number>> = {
		results:
			feed.status === "ready" && !feed.hasMore
				? runs.reduce((count, run) => count + run.practiceResults.length, 0)
				: undefined,
		feedback: feedbackCount,
	};
	const guide = guidance.status === "ready" ? guidance.guide : undefined;
	// The Guide tab exists only once a guide has arrived; until then, or without one, a link to it
	// lands on the feedback.
	const tabs = PRACTICE_TABS.filter((candidate) => candidate !== "guide" || guide !== undefined);
	const shownTab = tabs.includes(tab) ? tab : DEFAULT_PRACTICE_TAB;
	// No "Learn more" on a card here: the practice's introduction already leads this level.
	const feedbackCard = (card: PracticeFeedbackCardEntry) => (
		<PracticeFeedbackCard
			key={card.feedbackId}
			card={card}
			{...ratingProps?.(card.feedbackId)}
			onOpenGroup={onOpenGroup && (() => onOpenGroup())}
		/>
	);

	let body: ReactNode;
	if (isLoading) {
		body = (
			// The tabs and the one they open on, as they will be laid out.
			<div className="flex flex-col gap-4">
				<PracticeTabsSkeleton tabs={tabs.length} />
				{shownTab === "results" ? (
					<ReviewRunFeedSkeleton rows={skeletonRows} />
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
	} else if (practice) {
		const requestedNote = requestedReviewNote(practice, runs);
		body = (
			<>
				{neverApplied && <p className="max-w-2xl text-sm">{NEVER_APPLIED.description}</p>}
				<PracticeIntro practice={practice} guidance={guidance} />
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
						<Section
							size="lg"
							title="Feedback"
							description="Feedback about patterns in your work: the open card, then the ones that resolved, newest first."
						>
							{feedbackCount === 0 ? (
								<FeedbackEmpty description="Feedback appears here once a review sees the same pattern in your work more than once. Feedback posted on a single piece of work stays there. Open its result to find recorded comment links." />
							) : (
								<div className="flex flex-col gap-6">
									{feedback.open && (
										<LabelledBlock
											label="Current feedback"
											as="h3"
											className="flex flex-col gap-2.5"
										>
											{feedbackCard(feedback.open)}
										</LabelledBlock>
									)}
									{feedback.resolved.length > 0 && (
										<LabelledBlock
											label="Resolved feedback"
											as="h3"
											className="flex flex-col gap-2.5"
										>
											{feedback.resolved.map(feedbackCard)}
										</LabelledBlock>
									)}
								</div>
							)}
						</Section>
					</TabsContent>
					<TabsContent value="results" className="min-w-0">
						<Section
							size="lg"
							title="Results"
							description="Reviews of your work that reached this practice, newest first: why each was noted, the evidence, and the next step."
						>
							{hasText(requestedNote) && <p className="max-w-2xl text-sm">{requestedNote}</p>}
							<ReviewRunFeed
								feed={feed}
								runs={runs}
								skeletonRows={skeletonRows}
								results={results}
								// The rows are this practice's own, so none repeats its name under the summary.
								showPracticeName={false}
								// Every row here reviews the same practice, so the newest is the one the reader
								// came for; the rest are its history and wait for a press.
								initiallyOpen="newest"
								emptyTitle="No results yet"
								emptyDescription="A result appears here after a review checks this practice against your work."
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
	} else {
		body = (
			<p className="text-sm text-muted-foreground">
				We could not find this practice. Check the link, or ask a workspace admin whether this
				workspace reviews it.
			</p>
		);
	}

	return (
		<>
			<LevelHeader
				nested={nested}
				path={path}
				current="Practice"
				title={practice?.name ?? "Practice"}
				chips={
					practice && (
						<>
							{/* A practice that never applied has no trend, as its folded row shows. */}
							{neverApplied ? (
								<NeverAppliedBadge scope="practice" />
							) : (
								<>
									<StandingBadge
										standing={practice.standing}
										scope="practice"
										support={practice.trendSupport}
									/>
									<TrendNote
										direction={practice.direction}
										support={practice.trendSupport}
										scope="practice"
									/>
								</>
							)}
						</>
					)
				}
			/>
			<DrawerBody className="flex flex-col gap-6 pt-2">{body}</DrawerBody>
		</>
	);
}
