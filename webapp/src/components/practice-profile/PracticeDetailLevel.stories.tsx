import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import type { DetailStackEntry } from "@/components/layout/detail-drawer/detail-stack";
import { DetailDrawerHeader } from "@/components/layout/detail-drawer/DetailDrawerHeader";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import type { PracticeGuidanceState } from "@/components/practice-guidance/PracticeIntro";
import type { ReviewRunFeedState } from "@/components/profile/review-runs";
import { DrawerBody, DrawerTitle } from "@/components/ui/drawer";
import { withPageBehind, withProvider } from "@/stories/decorators";
import { useFeedbackRatings } from "@/stories/feedback-ratings";
import { expectSettledVisible } from "@/stories/overlay";
import {
	detailPractices,
	detailResult,
	detailRun,
	detailRuns,
	focusedChanges,
	requestedCleanRun,
	unwrittenAbout,
} from "@/stories/practice-detail-story-mock-data";
import { ALL_FEEDBACK_CARDS } from "@/stories/practice-feedback-cards-story-mock-data";
import { bundledGuidance } from "@/stories/practice-guidance-story-mock-data";
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
	runs: detailRuns,
	hasMore: false,
	isLoadingMore: false,
	onLoadMore: fn(),
} satisfies ReviewRunFeedState;

/** The practice the preview fixtures write feedback about, both open and resolved. */
const describedPractice =
	detailPractices.find((practice) => practice.slug === "describe-what-and-why") ?? focusedChanges;

interface LevelState {
	stack: DetailStackEntry<PracticeProfileDetailLevelKind>[];
	tab: PracticeTab;
}

/** The bundled picture and guide; the introduction's own states are `PracticeIntro`'s stories. */
const readyGuidance = {
	status: "ready",
	visual: bundledGuidance.visual,
	guide: bundledGuidance.guide,
} satisfies PracticeGuidanceState;

/** The level with the ratings held in story state, where the route keeps them on the server. */
function RatedLevel(props: PracticeDetailLevelProps) {
	const { ratingProps } = useFeedbackRatings();
	return <PracticeDetailLevel {...props} ratingProps={ratingProps} />;
}

/**
 * The level is always stacked on its group's, so every story mounts it as the second level of a
 * real drawer over a real page. The tab and the stack are the route's in the app; here they are
 * held beside the drawer so a press on a tab, a card or the group name moves the level as it does
 * there. Which results are open is each row's own.
 */
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
		results: { onRespond: fn() },
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

/** The second-newest result in the fixture feed. */
const olderResult = detailRuns[1]?.practiceResults[0];
if (!olderResult) {
	throw new Error("Expected a second run in the fixture feed.");
}

/**
 * Opens on the practice's introduction — why it matters, then the picture captioned with what good
 * looks like — over the tabs. The feedback written about the practice leads the rail and is the
 * tab selected, with the results and the guide beside it.
 */
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
			within(intro).getByRole("figure", { name: focusedChanges.whatGoodLooksLike }),
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

/**
 * On the results, the newest is open to why it was noted, the evidence, the next step and
 * its response controls; every earlier row is a line the reader can open. Nothing was loaded to
 * open it.
 */
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
		// The feed holds every run there is, so the tab can say how many results it opens on.
		await expect(screen.getByRole("tab", { name: "Results 3" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByRole("tab", { name: "Feedback 1" })).toBeVisible();
		// The run also reviewed another practice; only this practice's result is shown.
		await expect(
			screen.queryByText("The description names the motivation"),
		).not.toBeInTheDocument();

		// The newest is open without a press, with its reasons; every earlier one waits for one.
		await expect(
			screen.getByRole("button", { name: new RegExp(detailResult.summary, "u") }),
		).toHaveAttribute("aria-expanded", "true");
		await expect(
			screen.getByRole("button", { name: new RegExp(olderResult.summary, "u") }),
		).toHaveAttribute("aria-expanded", "false");
		// The verdict counts the problems, and the reader's own view ranks neither them nor it.
		await expect(screen.getByText("Not met · 3 problems")).toBeVisible();
		await expect(screen.getByRole("list", { name: "Problems" })).toBeVisible();
		for (const severity of ["Critical", "Major", "Minor", "Trivial"]) {
			await expect(screen.queryByText(severity)).not.toBeInTheDocument();
		}
		await expect(screen.getAllByText("Why it was noted")).toHaveLength(1);
		await expect(screen.getAllByText("Next step")).toHaveLength(1);

		// A run reviews this practice once, so every card here is one block: the summary leads and
		// the work it was seen on is the line under it, with no head divided off above.
		const row = screen.getByText(detailResult.summary).closest("li");
		if (!row) {
			throw new Error("Expected the result's own row.");
		}
		const rows = row.closest("ul");
		await expect(rows?.parentElement?.firstElementChild).toBe(rows);
		await expect(within(row).getByRole("link", { name: /^#902/u })).toBeVisible();
		await expect(within(row).getByText("HephaestusTest/practice-validation")).toBeVisible();

		await expect(screen.getByText("PracticeCatalogLoader.java")).toBeVisible();
		await expect(screen.getAllByRole("button", { name: "Disputed" })).toHaveLength(1);
		// The rows are this practice's own, so none repeats its name under the summary: the
		// level's title is the one place the practice is named.
		await expect(screen.getAllByText(focusedChanges.name)).toHaveLength(1);
		await expect(screen.getByRole("heading", { name: focusedChanges.name })).toBeVisible();
		await expect(screen.queryByText(/is evidence only/u)).not.toBeInTheDocument();
	},
};

/**
 * Live reviews found problems, so the standing says "Needs attention", and the newest review is one
 * the reader requested that found none. The Results tab says the requested review is evidence
 * only, where the reader sees both.
 */
export const RequestedReviewClean: Story = {
	args: { tab: "results", feed: { ...readyFeed, runs: [requestedCleanRun, ...detailRuns] } },
	play: async () => {
		await expectSettledVisible(await screen.findByRole("heading", { name: "Results" }));
		await expect(screen.getAllByText("Needs attention")).not.toHaveLength(0);
		await expect(
			screen.getByText(/A requested review is evidence only and does not change your standing\./u),
		).toBeVisible();
		await expect(screen.getByText("Requested")).toBeVisible();
	},
};

/**
 * The practice's one open card and the cards the work resolved, under a heading of the
 * Results tab's rank; a rating opens the comment band. The practice's own words open the
 * level, so no card offers to learn more about it.
 */
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

/**
 * Nothing open and nothing resolved: the same empty block the Results tab draws, and no
 * "Current feedback" or "Resolved feedback" label claims a list of nothing. It says where feedback
 * about a single piece of work is instead.
 */
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

/**
 * Feedback was left on the pull request, and no pattern has repeated into a card yet. The empty tab
 * leads to the results, where the row links each comment that carries the feedback.
 */
export const FeedbackOnTheWorkOnly: Story = {
	args: {
		tab: "feedback",
		feedbackCards: [],
		feed: {
			...readyFeed,
			runs: [
				{
					...detailRun,
					practiceResults: [
						{
							...detailResult,
							feedbackCommentUrls: [
								"https://github.com/HephaestusTest/practice-validation/pull/902#issuecomment-1",
							],
						},
					],
				},
				...detailRuns.slice(1),
			],
		},
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("No feedback yet"));
		await expect(screen.queryByRole("button", { name: "Show results" })).toBeNull();
		await userEvent.click(screen.getByRole("tab", { name: "Results 3" }));
		await expect(screen.getByRole("tab", { name: "Results 3" })).toHaveAttribute(
			"aria-selected",
			"true",
		);
		await expect(screen.getByText("Feedback on the work")).toBeVisible();
		await expect(screen.getByRole("link", { name: /Read the feedback comment/u })).toHaveAttribute(
			"href",
			"https://github.com/HephaestusTest/practice-validation/pull/902#issuecomment-1",
		);
	},
};

/**
 * The practice guide, a tab beside the results and the feedback rather than a disclosure in
 * the introduction: its headings and its figure at the width of the text, under the introduction
 * that stays above every tab.
 */
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
	args: { tab: "guide", guidance: { status: "ready", visual: bundledGuidance.visual } },
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

/**
 * Earlier runs are still a press away, so the tab carries no number: a count of the loaded pages
 * alone would be short, and would grow as the reader pressed.
 */
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
export const NotObserved: Story = {
	args: {
		practice: {
			...focusedChanges,
			standing: "NOT_OBSERVED",
			direction: undefined,
			trendSupport: undefined,
		},
		feed: { ...readyFeed, runs: [] },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByRole("region", { name: "Introduction" }));
		await expect(screen.getAllByText("Not observed yet")).toHaveLength(1);
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

/** Asked for the results, the level loads in the feed's shape instead. */
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

export const Missing: Story = {
	args: { practice: undefined },
};

export const MobileReflow: Story = {
	args: { tab: "results" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("PracticeCatalogLoader.java"));
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
		practice: { ...focusedChanges, standing: "NO_OPPORTUNITY", notApplicableOnly: true },
		neverApplied: true,
	},
	play: async () => {
		await expect(await screen.findAllByText("Does not apply to your work yet")).not.toHaveLength(0);
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
