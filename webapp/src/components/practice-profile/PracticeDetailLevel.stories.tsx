import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import type { DetailStackEntry } from "@/components/layout/detail-drawer/detail-stack";
import { DetailDrawerHeader } from "@/components/layout/detail-drawer/DetailDrawerHeader";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import type { PracticeGuidanceState } from "@/components/practice-guidance/PracticeIntro";
import type { ReviewFeedState } from "@/components/profile/reviews";
import { DrawerBody, DrawerTitle } from "@/components/ui/drawer";
import { hasText } from "@/lib/text";
import { withPageBehind, withProvider } from "@/stories/decorators";
import { useFeedbackRatings } from "@/stories/feedback-ratings";
import { expectSettledVisible } from "@/stories/overlay";
import {
	detailPractices,
	detailResult,
	detailReviews,
	focusedChanges,
	metWithoutSummary,
	requestedCleanReview,
	unwrittenAbout,
} from "@/stories/practice-detail-story-mock-data";
import { ALL_FEEDBACK_CARDS } from "@/stories/practice-feedback-cards-story-mock-data";
import { bundledGuidance, bundledPractice } from "@/stories/practice-guidance-story-mock-data";
import { packagingGroup } from "@/stories/practice-profile-story-mock-data";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { StatefulPatch } from "@/stories/stateful";
import { precedes } from "@/test/dom";

import {
	DEFAULT_PRACTICE_TAB,
	type PracticeProfileDetailLevelKind,
	type PracticeTab,
} from "./practice-profile-search";
import { PracticeDetailLevel, type PracticeDetailLevelProps } from "./PracticeDetailLevel";

const readyFeed = {
	status: "ready",
	reviews: detailReviews,
	hasMore: false,
	isLoadingMore: false,
	onLoadMore: fn(),
} satisfies ReviewFeedState;

/** The practice the preview fixtures write feedback about, both open and resolved. */
const describedPractice =
	detailPractices.find((practice) => practice.slug === "describe-what-and-why") ?? focusedChanges;

interface LevelState {
	stack: DetailStackEntry<PracticeProfileDetailLevelKind>[];
	tab: PracticeTab;
}

/** The bundled picture and guide; the introduction's own states are `PracticeIntro`'s stories. */
const readyGuidance = { status: "ready", ...bundledGuidance } satisfies PracticeGuidanceState;

/** The level with the ratings held in story state, where the route keeps them on the server. */
function RatedLevel(props: PracticeDetailLevelProps) {
	const { ratingProps } = useFeedbackRatings();
	return <PracticeDetailLevel {...props} ratingProps={ratingProps} />;
}

const meta = {
	component: PracticeDetailLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind, withProvider("GITHUB")],
	args: {
		path: {
			behind: [
				{ label: "Practice profile", depth: 0 },
				{ label: packagingGroup.name, depth: 1 },
			],
			onClose: fn(),
		},
		practice: focusedChanges,
		guidance: readyGuidance,
		feed: readyFeed,
		feedbackCards: ALL_FEEDBACK_CARDS,

		onOpenGroup: fn(),
		tab: DEFAULT_PRACTICE_TAB,
		onTabChange: fn(),
		skeletonRows: 3,
		isLoading: false,
		onRetry: fn(),
	},
	argTypes: {
		// A discriminated union renders as a free-text box, which cannot produce a valid value.
		feed: { control: false },
		// The close is the drawer stack's, which the render holds; only the crumbs come from here.
		path: { control: false },
		// A discriminated union, for the reason `feed` gives.
		guidance: { control: false },
	},
	render: (args) => (
		<StatefulPatch<LevelState>
			initial={{
				stack: [
					{ kind: "practice-group", id: packagingGroup.slug },
					{ kind: "practice", id: args.practice?.slug ?? focusedChanges.slug },
				],
				tab: args.tab,
			}}
		>
			{(state, patch) => (
				<DetailDrawerStack
					stack={state.stack}
					onClose={(depth) => patch({ stack: state.stack.slice(0, depth) })}
				>
					{(entry, level) =>
						entry.kind === "practice" ? (
							<RatedLevel
								{...args}
								nested={level.nested}
								path={{
									behind: args.path.behind,
									onClose: (depth) => patch({ stack: state.stack.slice(0, depth) }),
								}}
								tab={state.tab}
								onTabChange={(tab) => {
									args.onTabChange?.(tab);
									patch({ tab });
								}}
								onOpenGroup={() => {
									args.onOpenGroup?.();
									patch({ stack: state.stack.slice(0, 1) });
								}}
							/>
						) : (
							// A covered level is not inert, so it is a dialog with a name like any other.
							<>
								<DetailDrawerHeader nested={level.nested}>
									<DrawerTitle>{packagingGroup.name}</DrawerTitle>
								</DetailDrawerHeader>
								<DrawerBody />
							</>
						)
					}
				</DetailDrawerStack>
			)}
		</StatefulPatch>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeDetailLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

const olderSummary = detailReviews[1]?.results[0]?.summary;
if (!hasText(olderSummary)) {
	throw new Error("Expected a second review with a summary in the fixture feed.");
}
const newestSummary = detailResult.summary ?? "";

export const Default: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { level: 2, name: "Feedback" }));
		const [first, second, third] = screen.getAllByRole("tab");
		await expect(first).toHaveAccessibleName("Feedback 1");
		await expect(second).toHaveAccessibleName("Results 3");
		await expect(third).toHaveAccessibleName("Guide");
		const intro = screen.getByRole("region", { name: "Introduction" });
		await expect(precedes(intro, screen.getByRole("tablist", { name: "Practice" }))).toBe(true);
		await expect(
			within(intro).getByRole("figure", { name: focusedChanges.whatGoodLooksLike ?? "" }),
		).toBeVisible();
		await expect(screen.getByRole("tab", { name: "Feedback 1" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByRole("tab", { name: "Results 3" })).toHaveAttribute(
			"aria-selected",
			"false",
		);
		await expect(screen.queryByRole("heading", { name: "Results" })).not.toBeInTheDocument();
	},
};

export const ResultsTab: Story = {
	args: { tab: "results" },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Results" }));
		await expect(screen.getByRole("tab", { name: "Guide" })).toHaveAttribute(
			"aria-selected",
			"false",
		);
		// Below the top, dismissing returns to the group behind, so the control says Back.
		await expect(screen.getByRole("button", { name: "Back" })).toBeVisible();
		await expect(screen.getByRole("tab", { name: "Results 3" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByRole("tab", { name: "Feedback 1" })).toBeVisible();
		await expect(screen.getAllByText(newestSummary)).not.toHaveLength(0);
		await expect(screen.getAllByText(olderSummary)).not.toHaveLength(0);
		// The review also recorded a result on another practice; only this practice's is shown.
		await expect(screen.queryByText(metWithoutSummary.practiceName)).not.toBeInTheDocument();
		await expect(screen.getByRole("heading", { name: focusedChanges.name })).toBeVisible();
		await expect(screen.queryByText(/is evidence only/u)).not.toBeInTheDocument();
	},
};

export const RequestedReviewClean: Story = {
	args: {
		tab: "results",
		feed: { ...readyFeed, reviews: [requestedCleanReview, ...detailReviews] },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Results" }));
		await expect(screen.getAllByText("Needs attention")).not.toHaveLength(0);
		await expect(
			screen.getByText(/A requested review is evidence only and does not change your standing\./u),
		).toBeVisible();
	},
};

export const FeedbackTab: Story = {
	args: { tab: "feedback", practice: describedPractice },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { level: 2, name: "Feedback" }));
		await expect(screen.getByRole("heading", { level: 3, name: "Current feedback" })).toBeVisible();
		await expect(screen.getByRole("tab", { name: "Feedback 2" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		// The open card stands on its own words; the resolved one keeps the words it was written in.
		const open = screen.getByRole("article", {
			name: "Descriptions name the files, not the problem",
		});
		const resolved = screen.getByRole("article", {
			name: "Descriptions named the what, rarely the why",
		});
		await expect(within(open).getByText("Open")).toBeVisible();
		await expect(
			screen.getByRole("heading", { level: 3, name: "Resolved feedback" }),
		).toBeVisible();
		await expect(within(resolved).getByText("Resolved 9 September")).toBeVisible();
		await userEvent.click(within(open).getByRole("button", { name: "Helpful" }));
		await expect(
			screen.getByRole("textbox", { name: "What worked about this feedback?" }),
		).toBeVisible();
		await expect(
			screen.queryByRole("button", { name: "Learn more about this practice" }),
		).not.toBeInTheDocument();
	},
};

export const FeedbackEmpty: Story = {
	// A practice no feedback was ever written about, so the tab has nothing to list.
	args: { tab: "feedback", practice: unwrittenAbout },
	play: async () => {
		await expectSettledVisible(await screen.findByText("No feedback yet"));
		await expect(
			screen.getByText(
				"Feedback appears here once a review sees the same pattern in your work more than once. Feedback posted on a single piece of work stays there. Open its result to find recorded comment links.",
			),
		).toBeVisible();
		await expect(screen.getByRole("heading", { level: 2, name: "Feedback" })).toBeVisible();
		await expect(screen.queryByText("Current feedback")).not.toBeInTheDocument();
		await expect(screen.queryByText("Resolved feedback")).not.toBeInTheDocument();
	},
};

export const FeedbackOnTheWorkOnly: Story = {
	args: { tab: "feedback", feedbackCards: [] },
	play: async () => {
		await expectSettledVisible(await screen.findByText("No feedback yet"));
		await userEvent.click(screen.getByRole("tab", { name: "Results 3" }));
		await expect(screen.getByRole("tab", { name: "Results 3" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getAllByText(newestSummary)).not.toHaveLength(0);
	},
};

export const GuideTab: Story = {
	args: { tab: "guide" },
	play: async () => {
		await expectSettledVisible(
			await screen.findByRole("heading", { level: 3, name: "Practice guide" }),
		);
		await expect(screen.getByRole("tab", { name: "Guide" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		const panel = screen.getByRole("tabpanel", { name: "Guide" });
		for (const heading of [
			"How to do it",
			"When it does not apply",
			"Common mistakes",
			"Sources",
		]) {
			await expect(within(panel).getByRole("heading", { level: 4, name: heading })).toBeVisible();
		}
		await expect(
			within(panel).getByRole("img", { name: /^Three changes follow in order/u }),
		).toBeVisible();
		await expect(screen.getByRole("region", { name: "Introduction" })).toBeVisible();
		await expect(screen.queryByText("Read more")).not.toBeInTheDocument();
	},
};

/**
 * A practice with a picture and no guide has no Guide tab, so a link that asks for the guide lands
 * on the feedback instead of an empty tab.
 */
export const WithoutGuide: Story = {
	args: { tab: "guide", guidance: { ...readyGuidance, guide: null } },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { level: 2, name: "Feedback" }));
		await expect(screen.getByRole("tab", { name: "Feedback 1" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.queryByRole("tab", { name: "Guide" })).not.toBeInTheDocument();
		await expect(screen.getByRole("img", { name: bundledGuidance.visual.alt })).toBeVisible();
	},
};

/** No "Where you stand" section: the header's badge and trend carry the standing. */
export const WithoutWhereYouStand: Story = {
	args: { tab: "results" },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Results" }));
		await expect(screen.queryByText("Where you stand")).not.toBeInTheDocument();
		await expect(
			screen.queryByText(/counts most, and older work counts less/u),
		).not.toBeInTheDocument();
	},
};

// Loaded pages cannot establish an exact count until every page is known.
export const MoreToLoad: Story = {
	args: { tab: "results", feed: { ...readyFeed, hasMore: true } },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("button", { name: "View earlier reviews" }));
		await expect(screen.getByRole("tab", { name: "Results" })).toBeVisible();
		await expect(screen.queryByRole("tab", { name: /^Results \d/u })).not.toBeInTheDocument();
		// The feedback cards arrive in one piece, so that tab is counted either way.
		await expect(screen.getByRole("tab", { name: "Feedback 1" })).toBeVisible();
	},
};

/**
 * A practice no review has settled: the badge and the trend in the header say so, and nothing
 * claims a basis or a direction.
 */
export const NotReviewed: Story = {
	args: {
		practice: {
			...focusedChanges,
			standing: "NOT_OBSERVED",
			direction: null,
			trendSupport: null,
		},
		feed: { ...readyFeed, reviews: [] },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByRole("region", { name: "Introduction" }));
		await expect(screen.getAllByText("Not reviewed")).toHaveLength(1);
		await expect(screen.queryByText(/No practice in this group/u)).not.toBeInTheDocument();
		await expect(screen.queryByText(/Read from|counts most/u)).not.toBeInTheDocument();
		await expect(screen.queryByText(/pieces? of work$/u)).not.toBeInTheDocument();
		// The header's chip is the only one: no direction is claimed over no verdict.
		await expect(screen.getAllByText("Not enough to compare yet")).toHaveLength(1);
	},
};

/**
 * The rail and the tab it opens on each draw their own shape, so nothing jumps when the level
 * lands: a feedback card by default.
 */
export const Loading: Story = {
	args: { isLoading: true, practice: undefined },
	play: async () => {
		await screen.findByText("Loading feedback…");
		await expect(screen.queryByRole("tab")).toBeNull();
	},
};

export const LoadingResults: Story = {
	args: { isLoading: true, practice: undefined, tab: "results" },
	play: async () => {
		await screen.findByText("Loading reviews…");
		await expect(screen.queryByRole("tab")).toBeNull();
	},
};

export const LoadFailed: Story = {
	args: { error: new Error("Unavailable") },
	play: async () => {
		await expectSettledVisible(
			await screen.findByText(`We could not load your standing for ${focusedChanges.name}`),
		);
	},
};

export const GuidanceOnly: Story = {
	args: {
		practice: undefined,
		described: { name: "Pair on schema changes", ...bundledPractice },
	},
	play: async () => {
		await expectSettledVisible(
			await screen.findByText("This practice offers guidance without automated review."),
		);
		await expect(screen.getByRole("heading", { name: "Pair on schema changes" })).toBeVisible();
		await expect(screen.getByText(bundledPractice.whyItMatters)).toBeVisible();
		await expect(screen.getByRole("heading", { level: 3, name: "Practice guide" })).toBeVisible();
		await expect(screen.queryByRole("tab")).toBeNull();
		await expect(screen.queryByRole("heading", { name: "Results" })).toBeNull();
		await expect(screen.queryByText("Not reviewed")).toBeNull();
	},
};

/** The server answers 404 for a practice that this workspace does not review, which offers no retry. */
export const Missing: Story = {
	args: {
		practice: undefined,
		guidance: { status: "error", error: { status: 404 }, onRetry: fn() },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText(/We could not find this practice\./u));
		await expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
	},
};

/** Any other failure says nothing about whether the practice exists, so it offers the retry. */
export const MissingLoadFailed: Story = {
	args: {
		practice: undefined,
		guidance: { status: "error", error: new Error("Unavailable"), onRetry: fn() },
	},
	play: async ({ args }) => {
		await expectSettledVisible(await screen.findByText("We could not load this practice"));
		await expect(screen.queryByText(/We could not find this practice\./u)).toBeNull();
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));
		if (args.guidance.status !== "error") {
			throw new Error("Expected the guidance to have failed.");
		}
		await expect(args.guidance.onRetry).toHaveBeenCalledOnce();
	},
};

export const MobileReflow: Story = {
	args: { tab: "results" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Results" }));
		// The frontmost panel: the group level behind it is a placeholder with no body to measure.
		const panels = document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]');
		const frontmost = [...panels].at(-1);
		if (!frontmost) {
			throw new Error("Expected the practice level to be open.");
		}
		await expectNoPanelOverflow(frontmost);
	},
};

// --- The pattern label and the folds ------------------------------------------------------------

/** The pattern stays in the group's table, never in this header. */
export const PatternNotInHeader: Story = {
	args: { practice: describedPractice },
	play: async () => {
		await expectSettledVisible(
			await screen.findByRole("heading", { name: describedPractice.name }),
		);
		await expect(screen.queryByText(/^Has open feedback/u)).not.toBeInTheDocument();
	},
};

/** A practice that never applied says so instead of a gap. */
export const NeverApplied: Story = {
	args: {
		practice: { ...focusedChanges, standing: "NO_OPPORTUNITY", doesNotApplyOnly: true },
		neverApplied: true,
	},
	play: async () => {
		await expect(await screen.findAllByText("Does not apply to your work yet")).not.toHaveLength(0);
	},
};

/**
 * The standing includes earlier assessments whose criterion version is unavailable. The level marks
 * that basis beside the badge; those assessments provide no trend or quoted feedback.
 */
export const EarlierReviewsCriteriaUnknown: Story = {
	args: {
		practice: {
			...focusedChanges,
			standing: "MIXED",
			basis: "EARLIER_REVIEWS_CRITERIA_UNKNOWN",
			direction: null,
			trendSupport: null,
			toWorkOn: [],
			strengths: [],
		},
	},
	play: async () => {
		const mark = await screen.findByRole("button", {
			name: "Based on earlier reviews, criteria unknown",
		});
		await expect(mark).toHaveAccessibleDescription(
			/They do not show whether the current criteria are met\./u,
		);
	},
};

/** A practice its current reviews decided carries no earlier-reviews mark. */
export const CurrentReviewsCarryNoMark: Story = {
	args: { practice: { ...focusedChanges, basis: "CURRENT_REVIEWS" } },
	play: async () => {
		await screen.findAllByRole("heading");
		await expect(screen.queryByRole("button", { name: /^Based on earlier reviews/u })).toBeNull();
	},
};

export const NeverAppliedDark: Story = {
	args: { practice: describedPractice, neverApplied: true },
	globals: { theme: "dark" },
};

export const NeverAppliedPhone: Story = {
	args: { practice: describedPractice, neverApplied: true },
	globals: { viewport: { value: "mobile" } },
};
