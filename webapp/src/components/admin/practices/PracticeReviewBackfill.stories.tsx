import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import type { ReviewBackfillRun } from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { expectNoPageOverflow } from "@/test/reflow";
import { PracticeReviewBackfill } from "./PracticeReviewBackfill";

/**
 * The run as it reaches the component: ISO strings, because no response transformer revives them.
 * A fixture built from `new Date(…)` would let a screen that calls `.toLocaleDateString()` on a
 * string pass here and break in the browser.
 */
const run = (overrides: Partial<Wire<ReviewBackfillRun>> = {}): ReviewBackfillRun =>
	({
		id: "11111111-1111-1111-1111-111111111111",
		artifactKind: "scm.pull_request",
		fromAt: "2026-07-08T00:00:00Z",
		toAt: "2026-08-07T00:00:00Z",
		status: "AWAITING_CONFIRMATION",
		// A campaign an admin scoped by hand. The other value, SWEEP, belongs to a run a recurring
		// check opened, and those are shown by the schedule card rather than here.
		discoveredVia: "BACKFILL",
		estimatedArtifacts: 128,
		estimatedCostUsd: 15.36,
		submittedCount: 0,
		passedCount: 0,
		failedCount: 0,
		requestedByAccountId: 7,
		createdAt: "2026-08-07T09:00:00Z",
		...overrides,
	}) satisfies Wire<ReviewBackfillRun> as unknown as ReviewBackfillRun;

const meta = {
	title: "Workspace admin/Practices/Review past work",
	component: PracticeReviewBackfill,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		runs: [],
		isLoading: false,
		isError: false,
		onRetry: fn(),
		isEstimating: false,
		onEstimate: fn(),
		isUpdating: false,
		onConfirm: fn(),
		onCancel: fn(),
	},
	decorators: [
		(Story) => (
			<div className="mx-auto w-full max-w-3xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeReviewBackfill>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ChooseARange: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/nothing is reviewed until you confirm/i)).toBeInTheDocument();
		// A closed Base UI trigger falls back to printing the raw value, and "scm.pull_request" is not
		// a decision anyone can check.
		await expect(canvas.getByRole("combobox", { name: /Kind of work/ })).toHaveTextContent(
			"Pull or merge requests",
		);
		await expect(canvas.getByRole("combobox", { name: /How far back/ })).toHaveTextContent(
			"The last 30 days",
		);
		await expectNoPageOverflow();
	},
};

export const AwaitingConfirmation: Story = {
	args: { runs: [run()] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("128 pull or merge requests")).toBeInTheDocument();
		await expect(canvas.getByText("$15.36")).toBeInTheDocument();
		await expect(
			canvas.getByRole("button", { name: /review 128 pull or merge requests/i }),
		).toBeInTheDocument();
	},
};

/**
 * A fresh workspace has no priced reviews to forecast from, so the cost must read as unknown — a
 * $0.00 here would invite exactly the unconsidered spend this screen exists to prevent.
 */
export const CostUnknown: Story = {
	args: { runs: [run({ estimatedCostUsd: undefined })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Unknown")).toBeInTheDocument();
	},
};

/**
 * An estimate of nothing is a dead end unless the screen says how to get out of it: the disabled
 * button states what cannot happen, and the line beside it names the next move.
 */
export const NothingInRange: Story = {
	args: { runs: [run({ estimatedArtifacts: 0, estimatedCostUsd: 0 })] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /nothing to review/i })).toBeDisabled();
		await expect(canvas.getByText(/discard this and try a longer one/i)).toBeInTheDocument();
	},
};

export const Running: Story = {
	args: {
		runs: [
			run({ status: "RUNNING", submittedCount: 40, passedCount: 12, confirmedByAccountId: 7 }),
		],
	},
};

/**
 * An artifact the campaign could not read is not one it measured and found nothing in; folding the
 * two together would leave a baseline in which the two absences are indistinguishable.
 */
export const SomeCouldNotBeRead: Story = {
	args: {
		runs: [
			run({
				status: "RUNNING",
				submittedCount: 40,
				passedCount: 12,
				failedCount: 3,
				confirmedByAccountId: 7,
			}),
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/3 could not be read, and stay unmeasured/i)).toBeVisible();
		await expect(canvas.getByText(/12 already measured/i)).toBeVisible();
	},
};

/** The pause has to read as "still owed", never as "skipped". */
export const PausedOnBudget: Story = {
	args: {
		runs: [
			run({
				status: "PAUSED",
				pauseReason: "BUDGET_EXHAUSTED",
				submittedCount: 40,
				passedCount: 12,
				confirmedByAccountId: 7,
			}),
		],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/nothing has been skipped/i)).toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const WithHistory: Story = {
	args: {
		runs: [
			run({
				id: "22222222-2222-2222-2222-222222222222",
				status: "COMPLETED",
				submittedCount: 118,
				passedCount: 10,
				confirmedByAccountId: 7,
				finishedAt: "2026-08-06T12:00:00Z",
			}),
			run({
				id: "33333333-3333-3333-3333-333333333333",
				artifactKind: "scm.issue",
				status: "CANCELLED",
				estimatedArtifacts: 60,
				submittedCount: 12,
				passedCount: 3,
				confirmedByAccountId: 7,
				finishedAt: "2026-08-05T12:00:00Z",
			}),
		],
	},
};

export const Loading: Story = {
	args: { isLoading: true },
};

/**
 * The failure is about this list, not about the campaign. Left at "couldn't be loaded", an admin reads
 * it as "stopped" and confirms a second backfill over the same stretch of history — twice the spend.
 */
export const LoadFailed: Story = {
	args: { isError: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/backfills couldn't be loaded/i)).toBeInTheDocument();
		await expect(canvas.getByText(/already running is unaffected/i)).toBeInTheDocument();
	},
};
