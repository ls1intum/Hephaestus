// The palette this page and its levels share is `webapp/AGENTS.md` § Practice surfaces palette.
import type {
	GoingWellPractice,
	GroupRef,
	PracticeStanding,
	ProfileReviewRef,
} from "@/api/types.gen";
import type { LoadState } from "@/components/common/panel-state";
import {
	PracticeTabsList,
	PracticeTabsRail,
	PracticeTabsTrigger,
} from "@/components/common/practice-tabs";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Section } from "@/components/layout/Section";
import { isOpenFeedback } from "@/components/practice-vocabulary/feedback-state-defs";
import {
	type FeedbackRatingProps,
	PracticeFeedbackCard,
	type PracticeFeedbackCardEntry,
	PracticeFeedbackCardSkeleton,
} from "@/components/practice-vocabulary/PracticeFeedbackCard";
import { countPracticeStandings } from "@/components/practice-vocabulary/standing-counts";
import { Tabs, TabsContent } from "@/components/ui/tabs";

import { GoingWellPractices } from "./GoingWellPractices";
import { newestFirst } from "./practice-feedback-cards";
import { FeedbackEmpty, type FeedbackEmptyProps } from "./practice-profile-blocks";
import { FEEDBACK_TABS, type FeedbackTab } from "./practice-profile-search";
import { PracticeProfilePageHeader } from "./PracticeProfilePageHeader";

export interface PracticeProfilePageProps {
	/** The review the header's latest-review chip names; absent before the first review. */
	latestReview?: ProfileReviewRef;
	/** The practices going well, with the results that show them, between the header and the feedback. */
	goingWell: GoingWellPractice[];
	/** Every practice with a standing, for the header's overview ring. */
	practices: PracticeStanding[];
	/** The groups a card's group name can open; the header counts them. */
	groups: GroupRef[];
	/** The developer's practice feedback, open and resolved alike; the tabs split it by state. */
	feedbackCards: PracticeFeedbackCardEntry[];
	/**
	 * Wires one card's rating to wherever the ratings are kept; without it the cards cannot be
	 * rated.
	 */
	ratingProps?: (feedbackId: string) => FeedbackRatingProps;
	onOpenGroup?: (group: GroupRef) => void;
	/**
	 * Opens a practice's detail level from a feedback card. The level opens on the feedback written
	 * about the practice, under the catalog's words on it, so a card's "Learn more about this
	 * practice" and a practice named in the text land in the same place.
	 */
	onOpenPractice?: (practiceSlug: string) => void;
	/** The tab over the feedback cards, from the route's `feedback` search param. */
	feedbackTab: FeedbackTab;
	onFeedbackTabChange?: (tab: FeedbackTab) => void;
	state: LoadState;
}

/** Two cards' worth of skeleton while the cards load: what fits above the fold under the header. */
const SKELETON_CARD_COUNT = 2;

const FEEDBACK_TAB_LABELS: Record<FeedbackTab, string> = {
	open: "Open",
	resolved: "Resolved and closed",
	all: "All",
};

const NO_OPEN_FEEDBACK: FeedbackEmptyProps = { title: "No open feedback yet" };

/** What a tab with no cards says where it differs from every other empty list of feedback. */
const EMPTY_TAB: Record<FeedbackTab, FeedbackEmptyProps> = {
	open: NO_OPEN_FEEDBACK,
	resolved: {
		title: "No resolved or closed feedback yet",
		description:
			"A card moves here once your work resolves it, you mark it yourself, or your workspace changes its practice.",
	},
	all: {},
};

/** The cards each tab lists, newest first. */
function feedbackCardsByTab(
	cards: PracticeFeedbackCardEntry[],
): Record<FeedbackTab, PracticeFeedbackCardEntry[]> {
	return {
		open: newestFirst(cards.filter((card) => isOpenFeedback(card.state))),
		// A withdrawn card resolved nothing, so only "All" lists it.
		resolved: newestFirst(
			cards.filter((card) => !isOpenFeedback(card.state) && card.state !== "withdrawn"),
		),
		all: newestFirst(cards),
	};
}

/**
 * The developer's own practice profile: the overview header, the practices going well, then the
 * feedback cards under a tab row that filters them in place, with the table of every practice one
 * level over the page.
 */
export function PracticeProfilePage({
	latestReview,
	goingWell,
	practices,
	groups,
	feedbackCards,
	ratingProps,
	onOpenGroup,
	onOpenPractice,
	feedbackTab,
	onFeedbackTabChange,
	state,
}: PracticeProfilePageProps) {
	const openGroupBySlug = (groupSlug: string) => {
		const group = groups.find((candidate) => candidate.slug === groupSlug);
		if (group) {
			onOpenGroup?.(group);
		}
	};
	const cardsByTab = feedbackCardsByTab(feedbackCards);

	// The alert alone, as `profile/ProfilePage` does: the header's "No practices set up yet" and a
	// tab's "No feedback yet" are claims about the workspace, and a failed load has none to make.
	if (state.status === "error") {
		return (
			<div className="mx-auto w-full max-w-xl">
				<QueryErrorAlert
					error={state.error}
					title="We could not load your practices"
					onRetry={state.onRetry}
				/>
			</div>
		);
	}
	const isLoading = state.status === "loading";

	return (
		<div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
			<PracticeProfilePageHeader
				latestReview={latestReview}
				counts={countPracticeStandings(practices)}
				practiceCount={practices.length}
				groupCount={groups.length}
				isLoading={isLoading}
			/>
			<GoingWellPractices practices={goingWell} state={state} />
			<Section
				size="lg"
				title="Your feedback"
				description="Each card is one pattern seen more than once across your work, with the one thing to try next."
			>
				<Tabs
					value={feedbackTab}
					onValueChange={(next) => {
						const tab = FEEDBACK_TABS.find((candidate) => candidate === next);
						if (tab) {
							onFeedbackTabChange?.(tab);
						}
					}}
					className="gap-4"
				>
					<PracticeTabsRail>
						<PracticeTabsList aria-label="Feedback">
							{FEEDBACK_TABS.map((tab) => (
								<PracticeTabsTrigger
									key={tab}
									value={tab}
									count={isLoading ? undefined : cardsByTab[tab].length}
								>
									{FEEDBACK_TAB_LABELS[tab]}
								</PracticeTabsTrigger>
							))}
						</PracticeTabsList>
						<span className="pb-2 text-sm text-muted-foreground">Newest first</span>
					</PracticeTabsRail>
					<TabsContent value={feedbackTab}>
						<div className="flex flex-col gap-3" aria-busy={isLoading}>
							{isLoading &&
								Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
									<PracticeFeedbackCardSkeleton key={index} />
								))}
							{!isLoading && cardsByTab[feedbackTab].length === 0 && (
								<FeedbackEmpty {...EMPTY_TAB[feedbackTab]} />
							)}
							{!isLoading &&
								cardsByTab[feedbackTab].map((card) => (
									<PracticeFeedbackCard
										key={card.feedbackId}
										card={card}
										{...ratingProps?.(card.feedbackId)}
										onLearnMore={onOpenPractice && (() => onOpenPractice(card.practiceSlug))}
										onOpenPractice={onOpenPractice}
										onOpenGroup={onOpenGroup && openGroupBySlug}
									/>
								))}
						</div>
					</TabsContent>
				</Tabs>
			</Section>
		</div>
	);
}
