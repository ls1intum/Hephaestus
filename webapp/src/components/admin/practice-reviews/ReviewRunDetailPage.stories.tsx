import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, waitFor, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunDetailPage } from "./ReviewRunDetailPage";
import { manyObservations, reviewJob } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

const COMPLETED_RUN = "11111111-1111-1111-1111-111111111111";
const RUNNING_RUN = "aaaaaaaa-8888-8888-8888-888888888888";
const FAILED_RUN = "bbbbbbbb-8888-8888-8888-888888888888";

const sorted = { requireObservationSort: "ACTIONABILITY" };

// The heading comes from the job endpoint and the rows from the review ones, so both are served from
// one fixture — otherwise the header can name one pull request above rows naming another.
const meta = {
	title: "Workspace admin/Practice reviews/Review details",
	component: ReviewRunDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: reviewHandlers(sorted) },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		jobId: COMPLETED_RUN,
		search: {},
	},
} satisfies Meta<typeof ReviewRunDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompletedWithMixedOutput: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByRole("heading", {
			name: "Cache the workspace member lookup on the review path",
		});
		canvas.getByText("Summary posted");
		await canvas.findByText("A cache miss and a permission failure come back as the same 404");
		await canvas.findByText(/2 issues to tighten in this change/);
		await canvas.findByRole("heading", { name: "How this review ran", level: 3 });
		canvas.getByRole("button", { name: "Copy configuration" });
		canvas.getByText("Tokens read");
		await expect(canvas.queryByText("Configuration snapshot")).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

export const MoreObservationsThanItShows: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: reviewHandlers({
				...sorted,
				observations: manyObservations(64).map((observation) => ({
					...observation,
					agentJobId: COMPLETED_RUN,
				})),
			}),
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: "See all 64 observations" });
	},
};

/**
 * A run that skipped automated review for insufficient evidence completes successfully with no
 * observations, exactly like a review that assessed the work and recorded none: the empty state
 * must distinguish the two.
 */
export const DeclinedForInsufficientEvidence: Story = {
	parameters: {
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", () =>
					HttpResponse.json({
						...reviewJob(COMPLETED_RUN),
						reviewOutcome: "INSUFFICIENT_EVIDENCE",
					}),
				),
				...reviewHandlers({ ...sorted, observations: [], feedback: [] }),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// `findAllByText` resolves on the first match, so asserting a count on it races the second
		// panel's render.
		await waitFor(async () =>
			expect(await canvas.findAllByText("Nothing was assessed")).toHaveLength(2),
		);
		await expect(canvas.queryByText("No observations were recorded")).toBeNull();
		await expect(canvas.queryByText("No feedback")).toBeNull();
		await expect(
			await canvas.findAllByText(/the material it needed was missing, unreadable, out of date/),
		).not.toHaveLength(0);
	},
};

export const InProgressWithoutOutput: Story = {
	args: { jobId: RUNNING_RUN },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Running")).toBeVisible();
		await expect(
			await canvas.findByText("Observations will appear when the review finishes."),
		).toBeVisible();
		await expect(
			await canvas.findByText("Feedback will appear when the review finishes."),
		).toBeVisible();
		await expect(canvas.queryByText("No observations were recorded")).not.toBeInTheDocument();
		await expect(canvas.queryByText("No feedback")).not.toBeInTheDocument();
	},
};

export const FailedWithoutOutput: Story = {
	args: { jobId: FAILED_RUN },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review couldn't be completed")).toBeVisible();
		await expect(
			await canvas.findByText("This review ended before it produced observations or feedback."),
		).toBeVisible();
		// The failure text is on the page rather than behind a disclosure: this is the only screen that
		// can say why a review produced nothing.
		await canvas.findByText(/Cannot compute diff/);
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
	},
};

export const FailedWithPartialOutput: Story = {
	args: { jobId: FAILED_RUN },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", () =>
					HttpResponse.json(reviewJob(FAILED_RUN)),
				),
				...reviewHandlers({
					...sorted,
					feedback: [],
					observations: manyObservations(1).map((observation) => ({
						...observation,
						agentJobId: FAILED_RUN,
					})),
				}),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review output may be incomplete")).toBeVisible();
		await canvas.findByText("A dropped delivery is logged at debug and never counted");
	},
};

export const ReviewOfAConversation: Story = {
	args: { jobId: "33333333-3333-3333-3333-333333333333" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "How should we roll back the pricing migration?" });
		// The heading comes from the job and the rows from a second query, so the rows are awaited.
		await canvas.findByText("The thread ends without naming what was chosen");
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/agents/jobs/:jobId",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(sorted),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load this review");
	},
};
