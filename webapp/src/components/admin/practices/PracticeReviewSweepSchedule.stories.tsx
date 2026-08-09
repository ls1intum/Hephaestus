import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";
import type { ReviewSweepSchedule } from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewSweepSchedule } from "./PracticeReviewSweepSchedule";

/**
 * The schedule as it reaches the component: ISO strings, because no response transformer revives them.
 * A fixture built from `new Date(…)` would let a screen that calls `.toLocaleString()` on a string pass
 * here and break in the browser.
 */
const schedule = (overrides: Partial<Wire<ReviewSweepSchedule>> = {}): ReviewSweepSchedule =>
	({
		id: "22222222-2222-2222-2222-222222222222",
		artifactKind: "scm.pull_request",
		cadence: "DAILY",
		lookbackDays: 2,
		enabled: true,
		nextRunAt: "2026-08-10T02:17:00Z",
		lastRunAt: "2026-08-09T02:17:00Z",
		createdByAccountId: 7,
		createdAt: "2026-08-01T09:00:00Z",
		...overrides,
	}) satisfies Wire<ReviewSweepSchedule> as unknown as ReviewSweepSchedule;

const meta = {
	title: "Workspace admin/Practices/Keep checking new work",
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
		await expect(canvas.getByText(/nothing is checked on a schedule/i)).toBeInTheDocument();
		// A closed Base UI trigger falls back to printing the raw value, and "scm.pull_request" is not
		// a decision anyone can check.
		await expect(canvas.getByRole("combobox", { name: /Kind of work/ })).toHaveTextContent(
			"Pull or merge requests",
		);
		await expect(canvas.getByRole("combobox", { name: /How often/ })).toHaveTextContent(
			"Every day",
		);
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

		await expect(canvas.getByRole("combobox", { name: /How far back/ })).toHaveTextContent(
			"The last 7 days",
		);
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
		await expect(canvas.getByText(/every day, covering the last 2 days/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Pause" })).toBeInTheDocument();
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
		await expect(canvas.getByText(/has not checked anything yet/i)).toBeInTheDocument();
	},
};

export const Paused: Story = {
	args: { schedules: [schedule({ enabled: false })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/nothing is being checked/i)).toBeInTheDocument();
		await expect(canvas.getByRole("button", { name: "Resume" })).toBeInTheDocument();
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
		await expect(canvas.getByText(/recurring checks couldn't be loaded/i)).toBeInTheDocument();
	},
};
