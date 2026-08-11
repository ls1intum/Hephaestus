import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";
import { expectClosedSelectShows } from "@/test/controls";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewSweepSchedule } from "./PracticeReviewSweepSchedule";
import { sweepSchedule as schedule } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practices/Review/Keep checking new work",
	component: PracticeReviewSweepSchedule,
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
		onDelete: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeReviewSweepSchedule>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing scheduled: the empty state has to say what the absence costs, not just that it is empty. */
export const NothingScheduled: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/nothing is checked on a schedule/i);
		await expectClosedSelectShows(canvas, /Kind of work/, "Pull or merge requests");
		await expectClosedSelectShows(canvas, /How often/, "Every day");
		// The one control that authorises spending again and again names the work it commits to, and
		// the sentence beside it says the commitment outlives the first check.
		canvas.getByRole("button", { name: "Start checking pull or merge requests" });
		canvas.getByText(/not just the first/i);
		await expectNoPageOverflow();
	},
};

/**
 * The window offered must never exceed what the server accepts — twice the cadence, capped at a week —
 * or the admin's first attempt is a 400 explaining a rule the form should have expressed.
 */
export const WeeklyOffersAFullWeek: Story = {
	play: async ({ canvasElement, userEvent }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("combobox", { name: /How often/ }));
		// Base UI renders the popup in a portal outside the canvas, so the option is looked up on the
		// document rather than within the story's own subtree.
		await userEvent.click(await screen.findByRole("option", { name: "Every week" }));

		await expectClosedSelectShows(canvas, /How far back/, "The last 7 days");
	},
};

export const Running: Story = {
	args: { schedules: [schedule()] },
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/every day, covering the last 2 days/i);
		// The row's own kind is inside the accessible name: with a schedule per kind, three buttons
		// reading "Pause" would leave a voice-control or screen-reader user guessing which row they
		// stopped (WCAG 2.2 SC 2.5.3, SC 2.4.6).
		canvas.getByRole("button", { name: "Pause checking pull or merge requests" });
		canvas.getByRole("button", { name: "Remove the recurring check on pull or merge requests" });
		await expectNoPageOverflow();
	},
};

/**
 * A schedule that has never opened a campaign is not the same as one that is working, and the copy has
 * to say which — a row that looked identical either way would hide a workspace whose every turn is
 * being skipped.
 */
export const NotRunYet: Story = {
	args: { schedules: [schedule({ lastRunAt: undefined })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/has not checked anything yet/i);
	},
};

export const Paused: Story = {
	args: { schedules: [schedule({ enabled: false })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/nothing is being checked/i);
		canvas.getByRole("button", { name: "Resume checking pull or merge requests" });
	},
};

/**
 * Paused before it ever ran — the one combination whose copy used to contradict itself, promising a
 * first check "within the hour" on a schedule that was doing nothing at all.
 *
 * Neither half of the sentence is new: `Paused` shows the first clause with a last run beside it and
 * `NotRunYet` shows the second while the schedule is still running. Only their meeting is, so the
 * whole line is written out rather than matched a clause at a time.
 */
export const PausedBeforeItEverRan: Story = {
	args: { schedules: [schedule({ enabled: false, lastRunAt: undefined })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(
				"Every day, covering the last 2 days. Paused, so nothing is being checked. It has not checked anything yet.",
			),
		).toBeVisible();
	},
};

/** Both kinds scheduled: there is nothing left to add, so the form goes away rather than failing. */
export const EveryKindScheduled: Story = {
	args: {
		schedules: [
			schedule(),
			schedule({
				id: "33333333-3333-3333-3333-333333333333",
				artifactKind: "scm.issue",
				cadence: "WEEKLY",
				lookbackDays: 7,
			}),
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("button", { name: /start checking/i })).not.toBeInTheDocument();
	},
};

export const CouldNotLoad: Story = {
	args: { isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/recurring checks couldn't be loaded/i);
		// The failure is about this screen, not about the workspace: without saying so, an admin reads
		// "couldn't be loaded" as "stopped" and schedules a second check over the same work.
		canvas.getByText(/still running/i);
		canvas.getByRole("button", { name: "Try again" });
	},
};
