import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { mockJobCompleted, mockJobRunning } from "@/components/admin/ai/story-mock-data";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunDetailPage } from "./ReviewRunDetailPage";
import { reviewFeedback, reviewFindings } from "./story-mock-data";

const rawFailure = "Cannot compute diff: all resolution strategies failed for commit 27f4e88c.";
const failedJob = {
	...mockJobRunning,
	id: "job-failed-review",
	status: "FAILED",
	completedAt: new Date("2026-05-20T12:03:00Z"),
	errorMessage: rawFailure,
} as const;

const previewFindings = [reviewFindings[1], reviewFindings[0]];

const page = (content: unknown[], totalElements = content.length) => ({
	content,
	page: { number: 0, size: 5, totalElements, totalPages: Math.ceil(totalElements / 5) },
});

const handlers = (
	job = mockJobCompleted,
	feedback: unknown[] = reviewFeedback,
	findings: unknown[] = previewFindings,
	findingTotal = findings.length,
) => [
	http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", () => HttpResponse.json(job)),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
		HttpResponse.json(page(feedback)),
	),
	http.get("*/workspaces/:workspaceSlug/practices/reviews/findings", ({ request }) => {
		if (new URL(request.url).searchParams.get("sort") !== "ACTIONABILITY") {
			return HttpResponse.json({ detail: "Expected actionability order" }, { status: 400 });
		}
		return HttpResponse.json(page(findings, findingTotal));
	}),
];

const meta = {
	title: "Admin/Practice reviews/Review details",
	component: ReviewRunDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: handlers(mockJobCompleted, reviewFeedback, previewFindings, 30) },
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
		await expect(canvas.getByText("Summary comment: Delivered")).toBeVisible();
		await expect(await canvas.findByText(reviewFeedback[0].bodyPreview)).toBeVisible();
		await expect(await canvas.findByText(reviewFindings[1].title)).toBeVisible();
		await expect(canvas.getByRole("link", { name: "View all 30 findings" })).toBeVisible();
		await expectNoPageOverflow();
	},
};

/**
 * A run that declined for want of evidence completes successfully and produces no findings — exactly
 * like a clean review. The empty state must say so, or a refusal reads as a clean bill of health.
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
		await expect(await canvas.findAllByText("Nothing was assessed")).toHaveLength(2);
		await expect(canvas.queryByText("No findings were recorded")).toBeNull();
		await expect(canvas.queryByText("No messages")).toBeNull();
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
			await canvas.findByText("Findings will appear when the review finishes."),
		).toBeVisible();
		await expect(
			await canvas.findByText("Feedback will appear when the review finishes."),
		).toBeVisible();
		await expect(canvas.queryByText("No findings were recorded")).not.toBeInTheDocument();
		await expect(canvas.queryByText("No messages")).not.toBeInTheDocument();
	},
};

export const FailedWithoutOutput: Story = {
	args: { jobId: failedJob.id },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers(failedJob, [], []) },
	},
	play: async ({ canvasElement, userEvent }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review couldn't be completed")).toBeVisible();
		await expect(
			await canvas.findByText("This review ended before it produced findings or feedback."),
		).toBeVisible();
		await expect(canvas.queryByText(rawFailure)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Technical details" }));
		await expect(await canvas.findByText(rawFailure)).toBeVisible();
	},
};

export const FailedWithPartialOutput: Story = {
	args: { jobId: failedJob.id },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: handlers(failedJob, [], [reviewFindings[0]]) },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Review output may be incomplete")).toBeVisible();
		await expect(await canvas.findByText(reviewFindings[0].title)).toBeVisible();
		await expect(canvas.queryByText(rawFailure)).not.toBeInTheDocument();
	},
};
