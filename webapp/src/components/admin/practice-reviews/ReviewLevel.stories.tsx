import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { withPageBehind } from "@/stories/decorators";
import { InLevelStack } from "@/stories/level-stack";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow } from "@/stories/reflow";
import { levelsOpenedBy } from "@/test/detail-stack";
import { precedes } from "@/test/dom";

import {
	mockJobCancelledOnBudget,
	mockJobHeldOnBudget,
	mockJobRunning,
	mockJobUsageTwoAttempts,
	mockJobUsageUnpriced,
	reviewDeliveryChecksOf,
	reviewJob,
	reviewPrecompute,
	reviewRuns,
	reviewTraceOf,
} from "./fixtures";
import { LINEAGE_REVIEW_ID } from "./lineage-fixtures";
import { SHEET_SECTIONS } from "./RecordSheet";
import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { reviewLevel } from "./review-levels";
import { ReviewLevel } from "./ReviewLevel";

const job = reviewJob(LINEAGE_REVIEW_ID);
const summary = reviewRuns.find((run) => run.id === LINEAGE_REVIEW_ID);

/** The groups the trace fixture's practices sit in, each in its own colour. */
const groups = (
	[
		["review-ready-work", "Packaging work for review", "package", "blue"],
		["communication", "Communicating in the open", "message-circle", "violet"],
		["testing-discipline", "Testing discipline", "flask-conical", "emerald"],
		["acting-on-review-feedback", "Acting on review feedback", "repeat", "amber"],
	] as const
).map(([slug, name, icon, color]) => ({ slug, name, icon, color }));

/**
 * One review's sheet, and only the review, in the order every record sheet has: a status line,
 * What it is, State and history, the practices it answered, Technical (closed) and the actions.
 * Its observations, its feedback and when that was read are its trace; the other moments and the
 * merge are the work's.
 */
const meta = {
	component: ReviewLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		path: { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() },
		job: { status: "ready", job },
		summary,
		trace: { status: "ready", trace: reviewTraceOf(job) },
		groups,
		deliveryChecks: { status: "ready", checks: [] },
		precompute: { status: "ready", scripts: [] },
		usage: {
			status: "ready",
			usage: { ...mockJobUsageTwoAttempts, attempts: mockJobUsageTwoAttempts.attempts.slice(1) },
		},
		onCancel: fn(),
		cancelPending: false,
		onRetryResultProcessing: fn(),
		retryResultProcessingPending: false,
		onRequestReview: fn(),
	},
	argTypes: { path: { control: false }, precompute: { control: false } },
	render: (args) => (
		<InLevelStack
			entry={reviewLevel(args.job.status === "ready" ? args.job.job.id : job.id)}
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
		await expect(status).toHaveTextContent(/Completed.*ran 5 min.*finished 28 July/u);
		await expect(panel.getAllByText(/ran 5 min/u)).toHaveLength(1);
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("1 min after it finished");
		await expect(history.queryByText("Review on the work accepted")).toBeNull();
		// The moment that started it is the review's; the other moments on the work are the work's.
		panel.getByText("Started by");
		// Technical is open by default.
		panel.getByText("Review ID");
		panel.getByRole("button", { name: "Copy configuration" });
		// The review alone: no other record's facts, no link to another record.
		await expect(panel.queryByText(/other moments on this work/u)).toBeNull();
		await expect(panel.queryByText(/Until the developer read it/u)).toBeNull();
		await expect(panel.queryByText(/reached the developer/u)).toBeNull();
		for (const link of panel.queryAllByRole("link")) {
			await expect(String(link.getAttribute("href"))).not.toMatch(/\/admin\/practices\/reviews/u);
		}
		await userEvent.click(panel.getByRole("button", { name: "Request another review" }));
		// It starts an agent job, so it asks first.
		await expect(args.onRequestReview).not.toHaveBeenCalled();
		const confirm = await screen.findByRole("alertdialog");
		await userEvent.click(within(confirm).getByRole("button", { name: "Request review" }));
		await expect(args.onRequestReview).toHaveBeenCalledOnce();
	},
};

/**
 * Every practice in colour: its state as a badge, its group in the group's colour, and the reason
 * for its state. The sheet counts no verdicts: how the review answered a practice is its trace's.
 */
export const PracticesInColour: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const list = panel.getByRole("list", { name: "Every practice in this review" });
		const thin = within(list).getByText("Thin controllers").closest("li");
		await expect(thin).not.toBeNull();
		await expect(thin?.querySelector('[data-slot="badge"]')).toHaveTextContent("Reviewed");
		await expect(thin?.querySelectorAll('[data-slot="badge"]')).toHaveLength(1);
		await expect(thin).not.toHaveTextContent(/\d+ not met/u);
	},
};

/** A running review has started and can be canceled; another would only be coalesced into it. */
export const Running: Story = {
	args: {
		job: { status: "ready", job: mockJobRunning },
		summary: undefined,
		trace: { status: "ready", trace: reviewTraceOf(mockJobRunning) },
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			/Running.*started/u,
		);
		await expect(panel.queryByText("Not yet")).toBeNull();
		panel.getByText("Started");
		await expect(panel.queryByText("Review on the work accepted")).toBeNull();
		panel.getByRole("button", { name: "Cancel review" });
		await expect(panel.queryByRole("button", { name: "Request another review" })).toBeNull();
	},
};

/** Acceptance records the public decision, including a quiet one; the job can keep running. */
export const PublicDecisionAcceptedWhileRunning: Story = {
	args: {
		...Running.args,
		job: {
			status: "ready",
			job: {
				...mockJobRunning,
				publicReviewAcceptedAt: new Date(mockJobRunning.createdAt.getTime() + 120_000),
			},
		},
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText("Review on the work accepted");
		history.getByText(
			"The public decision is recorded. Each piece of feedback records its own delivery state.",
		);
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(/Running/u);
		await expect(history.queryByText("Completed")).toBeNull();
		await expect(history.queryByText("First comment posted")).toBeNull();
		panel.getByRole("button", { name: "Cancel review" });
	},
};

/** Public acceptance and posting can precede the whole review's completion. */
export const PublicCommentBeforeCompletion: Story = {
	args: {
		job: {
			status: "ready",
			job: {
				...job,
				publicReviewAcceptedAt: new Date(job.createdAt.getTime() + 120_000),
				firstFeedbackPostedAt: new Date(job.createdAt.getTime() + 150_000),
			},
		},
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		const history = within(panel.getByRole("list", { name: "History" }));
		const accepted = history.getByText("Review on the work accepted");
		const posted = history.getByText("First comment posted");
		const completed = history.getByText("Completed");
		await expect(precedes(accepted, posted)).toBe(true);
		await expect(precedes(posted, completed)).toBe(true);
		history.getByText("while the review was still running");
		await expect(history.queryByText(/after it finished/u)).toBeNull();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(/Completed/u);
	},
};

/** Another review of the same work waits or runs: asking again would only be coalesced into it. */
export const AnotherReviewActive: Story = {
	args: { requestBlocked: REQUEST_BLOCKED.active },
	play: async ({ args }) => {
		const panel = within(await settledDrawerPanel());
		const request = panel.getByRole("button", { name: "Request another review" });
		await expect(request).toHaveAttribute("aria-disabled", "true");
		await expect(args.onRequestReview).not.toHaveBeenCalled();
	},
};

/** The value under one fact's label in the sheet in front. */
async function phase(label: string) {
	const panel = within(await settledDrawerPanel());
	return panel.getByText(label, { selector: "dt" }).nextElementSibling;
}

/**
 * The current attempt's phases beside its history: wall-clock spans between the boundaries the job
 * records. Claimed the moment it was requested, so the wait is an exact zero, not an unknown.
 */
export const Phases: Story = {
	play: async () => {
		await expect(await phase("Wait to start")).toHaveTextContent("0 seconds");
		await expect(await phase("Preparation")).toHaveTextContent("40 seconds");
		await expect(await phase("Execution")).toHaveTextContent("4 minutes");
	},
};

/** A running review's execution is in progress, never a figure. */
export const PhasesWhileRunning: Story = {
	args: Running.args,
	play: async () => {
		await expect(await phase("Wait to start")).toHaveTextContent("20 seconds");
		await expect(await phase("Preparation")).toHaveTextContent("40 seconds");
		await expect(await phase("Execution")).toHaveTextContent("In progress");
	},
};

/** Claimed but not yet handed to the sandbox. */
export const PhasesWhilePreparing: Story = {
	args: {
		...Running.args,
		job: { status: "ready", job: { ...mockJobRunning, executionStartedAt: undefined } },
	},
	play: async () => {
		await expect(await phase("Preparation")).toHaveTextContent("In progress");
		await expect(await phase("Execution")).toHaveTextContent("Not started");
	},
};

export const PhasesWhileQueued: Story = {
	args: {
		...Running.args,
		job: {
			status: "ready",
			job: {
				...mockJobRunning,
				status: "QUEUED",
				startedAt: undefined,
				executionStartedAt: undefined,
			},
		},
	},
	play: async () => {
		await expect(await phase("Wait to start")).toHaveTextContent("In progress");
		await expect(await phase("Preparation")).toHaveTextContent("Not started");
		await expect(await phase("Execution")).toHaveTextContent("Not started");
	},
};

/** A retry clears the attempt's boundaries, so its wait reaches back to the original request. */
export const PhasesAfterARetry: Story = {
	args: {
		job: {
			status: "ready",
			job: { ...job, retryCount: 2, createdAt: new Date(job.createdAt.getTime() - 20 * 60_000) },
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByText("Wait to start", { selector: "dt" })).toBeNull();
		await expect(await phase("Request to this attempt")).toHaveTextContent(
			"20 minutes (includes earlier attempts and retry waits)",
		);
	},
};

/** A run from before the execution boundary was recorded: its two phases are unknown, never zero. */
export const PhasesBeforeExecutionWasRecorded: Story = {
	args: { job: { status: "ready", job: { ...job, executionStartedAt: undefined } } },
	play: async () => {
		await expect(await phase("Preparation")).toHaveTextContent("Not recorded");
		await expect(await phase("Execution")).toHaveTextContent("Not recorded");
	},
};

/** A review on hold is the one alert: it is happening now. */
export const OnHold: Story = {
	args: { job: { status: "ready", job: mockJobHeldOnBudget }, summary: undefined },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getAllByRole("alert")).toHaveLength(1);
	},
};

/** A failed review says what went wrong where it ended, in its history. */
export const Failed: Story = {
	args: {
		job: {
			status: "ready",
			job: {
				...job,
				status: "FAILED",
				exitCode: 1,
				errorMessage: "Cannot compute diff: all resolution strategies failed for commit 27f4e88c.",
				firstFeedbackPostedAt: undefined,
				deliveryStatus: undefined,
			},
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/Cannot compute diff/u);
		panel.getByText(/It ended early/u);
		await expect(panel.queryByRole("alert")).toBeNull();
	},
};

/** A review that assessed nothing says so, so it does not read as one that looked and found nothing. */
export const NothingAssessed: Story = {
	args: { job: { status: "ready", job: { ...job, reviewOutcome: "INSUFFICIENT_EVIDENCE" } } },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(
			/Nothing assessed/u,
		);
	},
};

export const Coalesced: Story = {
	args: { job: { status: "ready", job: { ...job, reviewOutcome: "COALESCED" } } },
	play: async () => {
		const panelElement = await settledDrawerPanel();
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(/Coalesced/u);
	},
};

/**
 * A queued review a newer one replaced reads Replaced, not Completed, and says the newer review carries
 * its practices. That review's id is under Technical, and a link opens its sheet in place of this
 * one: the one link a sheet makes to another record, since the newer review is where its practices
 * are.
 */
export const ReplacedByANewerReview: Story = {
	args: {
		job: {
			status: "ready",
			job: {
				...job,
				reviewOutcome: "SUPERSEDED",
				coveringJobId: "eeeeeeee-2222-2222-2222-222222222222",
				startedAt: undefined,
				executionStartedAt: undefined,
				deliveryStatus: undefined,
			},
		},
	},
	play: async () => {
		const panelElement = await settledDrawerPanel();
		const panel = within(panelElement);
		await expect(panelElement.querySelector("[data-sheet-status]")).toHaveTextContent(/Replaced/u);
		await expect(panelElement.querySelector("[data-sheet-status]")).not.toHaveTextContent(
			/Completed/u,
		);
		panel.getByText(/A newer review of the same work replaced it while it waited/u);
		panel.getByText("Replaced: a newer review of the same work carries its practices");
		panel.getByText("eeeeeeee-2222-2222-2222-222222222222");
		await expect(
			levelsOpenedBy(panel.getByRole("link", { name: "Open the newer review" })),
		).toEqual(["review:eeeeeeee-2222-2222-2222-222222222222"]);
	},
};

/** The checks the review ran for itself are on the review, not on a piece of its feedback. */
export const WithDeliveryChecks: Story = {
	args: { deliveryChecks: { status: "ready", checks: reviewDeliveryChecksOf(job) } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByRole("heading", { name: "Delivery checks of the review" });
	},
};

/** At 390px the practices reflow as a list instead of scrolling a table. */
export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};

/**
 * A retried review lists what each attempt cost under Technical, with their total; a single
 * attempt is only its cost.
 */
export const CostPerAttempt: Story = {
	args: {
		job: {
			status: "ready",
			job: { ...job, retryCount: 1, traceId: "4bf92f3577b34da6a3ce929d0e0e4736" },
		},
		usage: { status: "ready", usage: mockJobUsageTwoAttempts },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("$0.04 over 2 attempts");
		panel.getByRole("heading", { name: "Attempts" });
		panel.getByText("Attempt 1");
		panel.getByText("Attempt 2");
		panel.getByText("4bf92f3577b34da6a3ce929d0e0e4736");
		panel.getByRole("button", { name: "Copy trace id" });
	},
};

/** A model without a price has no cost: it says so rather than $0. */
export const CostNotKnown: Story = {
	args: { usage: { status: "ready", usage: mockJobUsageUnpriced } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Not known: 1 attempt has no price");
		await expect(panel.queryByText("$0")).toBeNull();
	},
};

/** A canceled review says in its history why the server stopped it. */
export const CanceledOnBudget: Story = {
	args: {
		job: { status: "ready", job: mockJobCancelledOnBudget },
		summary: undefined,
		trace: { status: "ready", trace: reviewTraceOf(mockJobCancelledOnBudget) },
		usage: { status: "ready", usage: { attempts: [], unpricedAttempts: 0 } },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const history = within(panel.getByRole("list", { name: "History" }));
		history.getByText(/Reason: The model budget was used up\./u);
		panel.getByText("No usage recorded");
	},
};

/**
 * The job counted model calls, but its usage ledger holds no attempt: the cost says nothing was
 * recorded, not that nothing reached the model.
 */
export const ModelCallsWithoutUsage: Story = {
	args: {
		job: { status: "ready", job: { ...job, llmTotalCalls: 12 } },
		usage: { status: "ready", usage: { attempts: [], unpricedAttempts: 0 } },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("No usage recorded");
		await expect(panel.queryByText(/reached the model/u)).toBeNull();
		await expect(panel.queryByText("$0")).toBeNull();
	},
};

/** What the review cost could not be read: the rest of the sheet still stands. */
export const CostFailed: Story = {
	args: {
		usage: { status: "error", error: new Error("Network down"), onRetry: fn() },
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText(/what the review cost/u);
		await expect(panel.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

export const Loading: Story = {
	args: { job: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByRole("heading", { name: SHEET_SECTIONS.what })).toBeNull();
	},
};

/** The model with the tier of the assignment it ran on, so a reader sees where the work went. */
export const ModelWithTier: Story = {
	args: { job: { status: "ready", job: { ...job, model: "gpt-5-nano", dataHandlingTier: "CLOUD" } } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const model = panel.getAllByRole("term").find((term) => term.textContent === "Model");
		// The tier is its icon, as on every other surface; its name is for a screen reader.
		await expect(model?.nextElementSibling).toHaveTextContent(/gpt-5-nano\s*Cloud$/u);
		await expect(panel.getByText("Cloud")).toHaveClass("sr-only");
		// The calls are the review model's: a precompute script's own models are counted elsewhere.
		await expect(panel.getByText("Review model calls")).toBeVisible();
	},
};

/**
 * What the precompute scripts did sits with the practices the review answered: it explains how
 * each practice was prepared, and its calls are not the review's technical facts.
 */
export const WithPrecomputeScripts: Story = {
	args: {
		precompute: {
			status: "ready",
			scripts: [reviewPrecompute.found, reviewPrecompute.callsNotRated, reviewPrecompute.failed],
		},
	},
	play: async () => {
		const panel = within(await settledDrawerPanel());
		const section = panel.getByRole("region", { name: "Precompute scripts" });
		await expect(
			within(section).getByText("What each practice’s script did before this review."),
		).toBeVisible();
		await expect(within(section).getByRole("link", { name: "AI usage" })).toHaveAttribute(
			"href",
			"/w/demo/admin/usage",
		);
	},
};

/** No script ran, so there is no section about scripts. */
export const WithoutPrecomputeScripts: Story = {
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(
			panel.queryByRole("region", { name: "Precompute scripts" }),
		).not.toBeInTheDocument();
	},
};

/** A failed read is said, never shown as a review without scripts. */
export const PrecomputeFailedToLoad: Story = {
	args: {
		precompute: {
			status: "error",
			error: { status: 503, detail: "The precompute record is unavailable." },
			onRetry: fn(),
		},
	},
	play: async ({ args, userEvent }) => {
		const panel = within(await settledDrawerPanel());
		const section = panel.getByRole("region", { name: "Precompute scripts" });
		await expect(
			within(section).getByText("We could not load what the precompute scripts did"),
		).toBeVisible();
		await userEvent.click(within(section).getByRole("button", { name: "Retry" }));
		if (args.precompute?.status !== "error") {
			throw new Error("This story is the error branch");
		}
		await expect(args.precompute.onRetry).toHaveBeenCalledOnce();
	},
};
