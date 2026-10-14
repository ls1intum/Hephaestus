import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import type { PracticeScriptRun, ReviewDetail } from "@/api/types.gen";
import { eventLabel } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";

import {
	reviewDetailBackingOff,
	reviewDetailCancelledOnBudget,
	reviewDetailCompleted,
	reviewDetailExpiredRuntime,
	reviewDetailProcessingFailed,
	reviewDetailQueued,
	reviewDetailRunning,
	reviewDetailScanManyJobs,
	reviewDetailTimedOut,
	reviewFeedbackChecksOf,
	reviewPracticeScripts,
	reviewUsageTwoAttempts,
	reviewUsageUnpriced,
} from "./fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { reviewLevel } from "./review-levels";
import { ReviewLevel, type ReviewLevelProps, RUNTIME_EXPIRED } from "./ReviewLevel";

const ready = (review: ReviewDetail): ReviewLevelProps["review"] => ({ status: "ready", review });

/** How each practice's script ran, by the practice it ran for. */
const SCRIPTS: Record<string, PracticeScriptRun> = {
	"thin-controllers": reviewPracticeScripts.fromCode,
	"errors-carry-context": reviewPracticeScripts.withModels,
	"product-language": reviewPracticeScripts.withheld,
};

const withScripts: ReviewDetail = {
	...reviewDetailCompleted,
	practices: reviewDetailCompleted.practices.map((practice) => ({
		...practice,
		script: SCRIPTS[practice.practiceSlug] ?? null,
	})),
};

/**
 * One review's sheet, and only the review, in the order every record sheet has: a status line,
 * What it is, State and history, the practices it answered, Technical and the actions. Its results,
 * its feedback and when that was read are its trace; the other moments on the work are the work's.
 */
const meta = {
	component: ReviewLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		review: ready(reviewDetailCompleted),
		eventLabel,
		feedbackChecks: { status: "ready", checks: [] },
		onCancel: fn(),
		cancelPending: false,
		onRequestReview: fn(),
	},
	argTypes: { path: { control: false }, eventLabel: { control: false } },
	render: (args) => (
		<InLevelStack
			entry={reviewLevel(
				args.review.status === "ready" ? args.review.review.id : reviewDetailCompleted.id,
			)}
			path={args.path}
		>
			{(level) => <ReviewLevel {...args} {...level} />}
		</InLevelStack>
	),
} satisfies Meta<typeof ReviewLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args, userEvent }) => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		// The fixed order of every sheet.
		const headings = panel
			.getAllByRole("heading", { level: 3 })
			.map((heading) => heading.textContent);
		await expect(headings).toStrictEqual([
			SHEET_SECTIONS.what,
			SHEET_SECTIONS.state,
			"Every practice",
			SHEET_SECTIONS.technical,
		]);
		// Status, duration and the finishing time once, in the status line.
		const status = panelElement.querySelector("[data-sheet-status]");
		await expect(status).toHaveTextContent(/Complete.*ran .*finished/u);
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("Asked for");
		history.getByText("Started");
		// The event that started it is the review's; the other events on the work are the work's.
		panel.getByText("Started by");
		panel.getByText(/Marked ready for review/u);
		panel.getByText("Review ID");
		// The review alone: no link to another record of Practice reviews.
		for (const link of panel.queryAllByRole("link")) {
			await expect(String(link.getAttribute("href"))).not.toMatch(/\/admin\/practices\/reviews/u);
		}
		await userEvent.click(panel.getByRole("button", { name: "Request another review" }));
		// It starts a review, so it asks first.
		await expect(args.onRequestReview).not.toHaveBeenCalled();
		const confirm = await screen.findByRole("alertdialog");
		await userEvent.click(within(confirm).getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).toHaveBeenCalledOnce();
	},
};

/**
 * Every practice with its state and the reason for it. A withheld practice is not a result: it
 * says why it was withheld instead of counting outcomes.
 */
export const PracticesWithTheirState: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const list = panel.getByRole("list", { name: "Every practice in this review" });
		const withheld = list.querySelector('[data-practice-status="WITHHELD"]');
		await expect(withheld).not.toBeNull();
		await expect(withheld).toHaveTextContent("Withheld");
		await expect(withheld).toHaveTextContent(/provider did not return them/u);
		const done = list.querySelector('[data-practice-status="DONE"]');
		await expect(done).toHaveTextContent(/Done.*result/u);
	},
};

/** A running review can be cancelled; another would only be coalesced into it. */
export const Running: Story = {
	args: { review: ready(reviewDetailRunning) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("button", { name: "Cancel review" })).toBeEnabled();
		await expect(panel.queryByRole("button", { name: "Request another review" })).toBeNull();
	},
};

/** Waiting in the queue, before any practice is reached. */
export const Queued: Story = {
	args: { review: ready(reviewDetailQueued) },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			/Queued.*asked for/u,
		);
		within(panelElement).getByText("Practices appear here as the review reaches them.");
	},
};

/** An attempt stopped and the retry waits for its time: the history says when it is due. */
export const BackingOff: Story = {
	args: { review: ready(reviewDetailBackingOff) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		within(panel.getByRole("list", { name: "History" })).getByText("Retry due");
		const execution = within(panel.getByRole("list", { name: "Execution" }));
		execution.getByText("1 of 2 · Attempt 1");
		execution.getByText("2 of 2 · Attempt 2");
		execution.getByText(/out of memory/u);
	},
};

/** A scan is one review that ran many times: its sheet opens its report, and lists every run. */
export const ScanWithManyRuns: Story = {
	args: { review: ready(reviewDetailScanManyJobs), onRequestReview: undefined },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Scan");
		await expect(panel.getByRole("link", { name: "Open the scan report" })).toHaveAttribute(
			"href",
			expect.stringContaining(`/admin/practices/scans/${reviewDetailScanManyJobs.id}`),
		);
		const runs = within(panel.getByRole("list", { name: "Execution" })).getAllByRole("listitem");
		await expect(runs).toHaveLength(reviewDetailScanManyJobs.runtime?.jobs.length ?? 0);
	},
};

/** Its runtime records expired: the review's facts and practices remain, the execution is gone. */
export const RuntimeExpired: Story = {
	args: { review: ready(reviewDetailExpiredRuntime) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(RUNTIME_EXPIRED);
		panel.getByRole("list", { name: "Every practice in this review" });
	},
};

/** A review that ran out of time says why it stopped, and what the run printed. */
export const TimedOut: Story = {
	args: { review: ready(reviewDetailTimedOut) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		within(panel.getByRole("list", { name: "History" })).getByText(
			"It ran past the time it is allowed and was stopped.",
		);
		panel.getByText(/exceeded the 1200s timeout/u);
		panel.getByText("Results it recorded before it stopped still stand.");
	},
};

/** The model finished, and processing what it returned failed: the run says so on its own. */
export const ProcessingFailed: Story = {
	args: { review: ready(reviewDetailProcessingFailed) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		within(panel.getByRole("list", { name: "Execution" })).getByText("Processing failed");
	},
};

/** Stopped before it started because the AI budget was used up: the history says so. */
export const CancelledOnBudget: Story = {
	args: { review: ready(reviewDetailCancelledOnBudget) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		within(panel.getByRole("list", { name: "History" })).getByText(
			"The review budget was used up.",
		);
	},
};

/** A retried review lists what each attempt cost under Technical, with their total. */
export const CostPerAttempt: Story = {
	args: { review: ready({ ...reviewDetailCompleted, usage: reviewUsageTwoAttempts }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/over 2 attempts/u);
		panel.getByRole("heading", { name: "Attempts" });
	},
};

/** A model without a price has no cost: it says so rather than $0. */
export const CostNotKnown: Story = {
	args: { review: ready({ ...reviewDetailCompleted, usage: reviewUsageUnpriced }) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Not known: 1 attempt has no price");
	},
};

/** The feedback checks the review ran are on the review, not on a piece of its feedback. */
export const WithFeedbackChecks: Story = {
	args: {
		feedbackChecks: { status: "ready", checks: reviewFeedbackChecksOf(reviewDetailCompleted) },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByRole("heading", { name: "Feedback checks of the review" });
	},
};

export const FeedbackChecksLoading: Story = {
	args: { feedbackChecks: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByRole("heading", { name: "Feedback checks of the review" });
	},
};

/** A failed read is said, never shown as a review without checks. */
export const FeedbackChecksFailed: Story = {
	args: { feedbackChecks: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("We could not load the feedback checks");
	},
};

/**
 * Each practice that ran a program says how it ran under Every practice, and the section Practice
 * scripts has the figures.
 */
export const WithPracticeScripts: Story = {
	args: { review: ready(withScripts) },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByRole("heading", { name: "Practice scripts" });
		await expect(panel.getByRole("link", { name: "AI usage" })).toHaveAttribute(
			"href",
			"/w/demo/admin/usage",
		);
	},
};

/** No script ran, so there is no section about scripts. */
export const WithoutPracticeScripts: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByRole("heading", { name: "Practice scripts" })).toBeNull();
	},
};

/** Another review of the same work waits or runs: asking again would only be coalesced into it. */
export const AnotherReviewActive: Story = {
	args: { requestBlocked: REQUEST_BLOCKED.active },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("button", { name: "Request another review" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
	},
};

/** Why the last ask started nothing, drawn by the route above the sheet's sections. */
export const WithRefusal: Story = {
	args: { requestRefusal: <p role="alert">A review of this work started a minute ago.</p> },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByRole("alert");
	},
};

export const Loading: Story = {
	args: { review: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByRole("heading", { name: SHEET_SECTIONS.what })).toBeNull();
	},
};

export const LoadFailed: Story = {
	args: { review: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("We could not load this review");
		await expect(panel.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

/** At 390px the practices reflow as a list instead of scrolling a table. */
export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { disableSnapshot: true } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};
