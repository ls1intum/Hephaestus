import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, waitFor, within } from "storybook/test";
import { mockJobCompleted, mockJobRunning } from "@/components/admin/ai/story-mock-data";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunDetailPage } from "./ReviewRunDetailPage";
import { reviewFeedback, reviewObservations, workspacePractices } from "./story-mock-data";

const rawFailure = "Cannot compute diff: all resolution strategies failed for commit 27f4e88c.";
const failedJob = {
	...mockJobRunning,
	id: "job-failed-review",
	status: "FAILED",
	completedAt: new Date("2026-05-20T12:03:00Z"),
	errorMessage: rawFailure,
} as const;

const previewObservations = [reviewObservations[1], reviewObservations[0]];

const page = (content: unknown[], totalElements = content.length) => ({
	content,
	page: { number: 0, size: 5, totalElements, totalPages: Math.ceil(totalElements / 5) },
});

const handlers = (
	job = mockJobCompleted,
	feedback: unknown[] = reviewFeedback,
	observations: unknown[] = previewObservations,
	observationTotal = observations.length,
) => [
	http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", () => HttpResponse.json(job)),
	http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(workspacePractices)),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
		HttpResponse.json(page(feedback)),
	),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", ({ request }) => {
		if (new URL(request.url).searchParams.get("sort") !== "ACTIONABILITY") {
			return HttpResponse.json({ detail: "Expected actionability order" }, { status: 400 });
		}
		return HttpResponse.json(page(observations, observationTotal));
	}),
];

const meta = {
	title: "Workspace admin/Practice reviews/Review details",
	component: ReviewRunDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: handlers(mockJobCompleted, reviewFeedback.slice(0, 2), previewObservations, 30),
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		jobId: mockJobCompleted.id,
		search: {},
	},
} satisfies Meta<typeof ReviewRunDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CompletedWithMixedOutput: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByRole("heading", { name: mockJobCompleted.target.title }),
		).toBeVisible();
		await expect(canvas.getByText("Summary posted")).toBeVisible();
		await expect(await canvas.findByText(reviewFeedback[0].bodyPreview)).toBeVisible();
		await expect(await canvas.findByText(reviewObservations[1].title)).toBeVisible();
		await expect(canvas.getByRole("link", { name: "See all 30 observations" })).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * A run that skipped automated review for insufficient evidence completes successfully with no
 * findings, exactly like a review that assessed the work and found none: the empty state must
 * distinguish the two.
 */
export const DeclinedForInsufficientEvidence: Story = {
	parameters: {
		msw: {
			handlers: handlers(
				{
					...mockJobCompleted,
					id: "job-insufficient-evidence",
					reviewOutcome: "INSUFFICIENT_EVIDENCE",
				},
				[],
				[],
				0,
			),
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
			await canvas.findAllByText(/required evidence was missing, unreadable, out of date/),
		).not.toHaveLength(0);
	},
};

export const InProgressWithoutOutput: Story = {
	args: { jobId: mockJobRunning.id },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers(mockJobRunning, [], []) },
	},
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
	args: { jobId: failedJob.id },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers(failedJob, [], []) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review couldn't be completed")).toBeVisible();
		await expect(
			await canvas.findByText("This review ended before it produced observations or feedback."),
		).toBeVisible();
		// The failure text is on the page. It used to be inside a collapsed "Technical details"
		// accordion, together with the model and token counts — so the one screen that could say why a
		// review produced nothing hid the answer behind a drawer labelled for technicians.
		await canvas.findByText(rawFailure);
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
	},
};

export const FailedWithPartialOutput: Story = {
	args: { jobId: failedJob.id },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers(failedJob, [], [reviewObservations[0]]) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review output may be incomplete")).toBeVisible();
		await expect(await canvas.findByText(reviewObservations[0].title)).toBeVisible();
	},
};

/**
 * How the review ran, on the page rather than in a drawer.
 *
 * The model and the token counts are what an operator checks when a review costs more than it should
 * or answers worse than it used to. The configuration snapshot is not rendered at all: it is a
 * machine artefact, so the useful action on it is to put it where a machine can read it.
 */
export const HowTheRunWent: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "How this review ran", level: 3 });
		canvas.getByRole("button", { name: "Copy configuration" });
		canvas.getByText("Tokens read");
		await expect(canvas.queryByText("Configuration snapshot")).not.toBeInTheDocument();
	},
};
