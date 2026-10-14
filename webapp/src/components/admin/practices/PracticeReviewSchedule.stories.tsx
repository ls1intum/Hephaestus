import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";

import { expectNoPageOverflow } from "@/stories/reflow";
import { expectClosedSelectShows } from "@/test/controls";

import { PracticeReviewSchedule } from "./PracticeReviewSchedule";
import { reviewSchedule as schedule } from "./review/fixtures";

const meta = {
	component: PracticeReviewSchedule,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		schedules: [],
		isLoading: false,
		isError: false,
		onRetry: fn(),
		isSaving: false,
		onCreate: fn(),
		onReplace: fn(),
		onSetEnabled: fn(),
		onDelete: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeReviewSchedule>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/nothing is checked on a schedule/iu)).toBeVisible();
		await expectClosedSelectShows(canvas, /Kind of work/u, "Pull or merge requests");
		await expectClosedSelectShows(canvas, /How often/u, "Every day");
		await expect(
			canvas.getByRole("button", { name: "Start checking pull or merge requests" }),
		).toBeVisible();
		await expect(canvas.getByText(/not just the first/iu)).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * The window offered must never exceed what the server accepts, or the admin's first attempt is a
 * 400 explaining a rule the form should have expressed.
 */
export const WeeklyOffersAFullWeek: Story = {
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /How often/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Every week" }));

		await expectClosedSelectShows(canvas, /How far back/u, "The last 7 days");
	},
};

/** A repository is scanned as a whole, so it asks for a budget and no window. */
export const RepositoryScanNeedsABudget: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /Kind of work/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Repositories" }));
		await expect(canvas.queryByRole("combobox", { name: /How far back/u })).not.toBeInTheDocument();

		const start = canvas.getByRole("button", { name: "Start checking repositories" });
		await userEvent.click(start);
		await expect(canvas.getByText(/Enter an amount in US dollars/u)).toBeVisible();
		await expect(args.onCreate).not.toHaveBeenCalled();

		await userEvent.type(canvas.getByRole("textbox", { name: "Budget per run (USD)" }), "25");
		await userEvent.click(start);
		await expect(args.onCreate).toHaveBeenCalledWith({
			itemKind: "scm.repository",
			cadence: "DAILY",
			lookbackDays: null,
			budgetUsd: "25",
			enabled: true,
		});
	},
};

export const Running: Story = {
	args: { schedules: [schedule()] },
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText(/every day, covering the last 2 days/iu)).toBeVisible();
		await userEvent.click(
			canvas.getByRole("button", { name: "Pause checking pull or merge requests" }),
		);
		await expect(args.onSetEnabled).toHaveBeenCalledWith(args.schedules[0], false);
		await expect(
			canvas.getByRole("button", { name: "Delete the recurring check on pull or merge requests" }),
		).toBeVisible();
		await expectNoPageOverflow();
	},
};

export const NotRunYet: Story = {
	args: { schedules: [schedule({ lastRunAt: null })] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/has not checked anything yet/iu)).toBeVisible();
	},
};

export const Paused: Story = {
	args: { schedules: [schedule({ enabled: false })] },
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText(/nothing is being checked/iu)).toBeVisible();
		await userEvent.click(
			canvas.getByRole("button", { name: "Resume checking pull or merge requests" }),
		);
		await expect(args.onSetEnabled).toHaveBeenCalledWith(args.schedules[0], true);
	},
};

/**
 * The combination that can contradict itself: a paused schedule must not promise a first check
 * "within the hour", so the whole line is written out rather than matched a clause at a time.
 */
export const PausedBeforeItEverRan: Story = {
	args: { schedules: [schedule({ enabled: false, lastRunAt: null })] },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				"Every day, covering the last 2 days. Paused, so nothing is being checked. It has not checked anything yet.",
			),
		).toBeVisible();
	},
};

/** Editing offers the same pause and resume as the row, so a paused schedule can always resume. */
export const EditAndResume: Story = {
	args: { schedules: [schedule({ enabled: false })] },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: "Edit the recurring check on pull or merge requests" }),
		);
		const keepChecking = canvas.getByRole("switch", { name: "Keep checking" });
		await expect(keepChecking).not.toBeChecked();
		await userEvent.click(keepChecking);
		await userEvent.click(canvas.getByRole("button", { name: "Save schedule" }));
		await expect(args.onReplace).toHaveBeenCalledWith(args.schedules[0], {
			itemKind: "scm.pull_request",
			cadence: "DAILY",
			lookbackDays: 2,
			budgetUsd: null,
			enabled: true,
		});
	},
};

export const RepositoryScans: Story = {
	args: {
		schedules: [
			schedule({
				id: "44444444-4444-4444-4444-444444444444",
				itemKind: "scm.repository",
				cadence: "WEEKLY",
				lookbackDays: null,
				budgetUsd: "25",
			}),
		],
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/every week, scanning each repository in the review scope, up to \$25 per run/iu,
			),
		).toBeVisible();
	},
};

export const EveryKindScheduled: Story = {
	args: {
		schedules: [
			schedule(),
			schedule({
				id: "33333333-3333-3333-3333-333333333333",
				itemKind: "scm.issue",
				cadence: "WEEKLY",
				lookbackDays: 7,
			}),
			schedule({
				id: "44444444-4444-4444-4444-444444444444",
				itemKind: "scm.repository",
				lookbackDays: null,
				budgetUsd: "25",
			}),
		],
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.queryByRole("button", { name: /start checking/iu }),
		).not.toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { isLoading: true },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("button", { name: "Start checking pull or merge requests" }),
		).toBeDisabled();
	},
};

export const Error: Story = {
	args: { isError: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/could not load recurring checks/iu)).toBeVisible();
		// The failure is about this screen, not the workspace: read as "stopped", it costs a second
		// check scheduled over the same work.
		await expect(canvas.getByText(/keep running/iu)).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeVisible();
	},
};
