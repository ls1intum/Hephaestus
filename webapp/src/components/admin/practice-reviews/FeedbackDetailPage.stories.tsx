import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import type { ReviewFeedbackDetail } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackDetailPage } from "./FeedbackDetailPage";
import { reviewFeedbackDetail, workspacePractices } from "./story-mock-data";

const deliveredFeedbackDetail = {
	...reviewFeedbackDetail,
	id: "33333333-3333-3333-3333-333333333333",
	body: "## What worked\n\nThe controller stays focused on HTTP concerns.",
	deliveryState: "DELIVERED",
	deliveredAt: new Date("2026-07-28T13:43:00Z"),
	recipient: { id: 7, login: "ada", name: "Ada Lovelace" },
	subject: { id: 7, login: "ada", name: "Ada Lovelace" },
	suppressionReason: undefined,
	placements: [
		{
			id: "77777777-7777-7777-7777-777777777777",
			placementType: "INLINE",
			anchorPath: "server/src/main/java/ReviewController.java",
			anchorStartLine: 42,
			anchorEndLine: 44,
		},
	],
} satisfies ReviewFeedbackDetail;

const meta = {
	title: "Workspace admin/Practice reviews/Feedback details",
	component: FeedbackDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
					HttpResponse.json(reviewFeedbackDetail),
				),
				// The observations behind the feedback name their practice, and a practice name is a
				// link with the practice's own prose behind it.
				http.get("*/workspaces/:workspaceSlug/practices", () =>
					HttpResponse.json(workspacePractices),
				),
			],
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		feedbackId: reviewFeedbackDetail.id,
		search: {
			agentJobId: reviewFeedbackDetail.agentJobId,
			deliveryState: undefined,
			withheldFamily: undefined,
			channel: undefined,
		},
	},
} satisfies Meta<typeof FeedbackDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NotDelivered: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Withheld text is badged where it is shown *and* traced under Delivery, and the trace is the
		// only place the reason sentence appears.
		await expect(await canvas.findAllByText("Withheld")).toHaveLength(2);
		canvas.getByRole("link", { name: "See everything reviewed on this work" });
		// The parent review is a link in the line under the title, not a UUID in a drawer.
		canvas.getByRole("link", { name: "in a review" });
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
		canvas.getByText("The work moved on");
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
		await expectNoPageOverflow();
	},
};

export const Delivered: Story = {
	args: { feedbackId: deliveredFeedbackDetail.id },
	parameters: {
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
					HttpResponse.json(deliveredFeedbackDetail),
				),
				http.get("*/workspaces/:workspaceSlug/practices", () =>
					HttpResponse.json(workspacePractices),
				),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Exactly one "Delivered": the trace's terminal step. The composed text carries no badge,
		// because reaching the developer is the ordinary case.
		await expect(await canvas.findAllByText("Delivered")).toHaveLength(1);
		canvas.getByText(/As an inline note on the work/);
		canvas.getByText(/As an inline note:/);
		await expect(
			canvas.getByText("server/src/main/java/ReviewController.java:42–44"),
		).toBeVisible();
	},
};

/**
 * The composed text and the Markdown it was written in, as two views of one thing.
 *
 * The source used to sit in a "View Markdown source" accordion below the card — a second control in
 * a second place that pushed the page down and showed the same words twice.
 */
export const RenderedAndSource: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		await canvas.findByRole("heading", { level: 4, name: "What could improve" });
		await userEvent.click(
			canvas.getByRole("button", { name: /Show the Markdown that was composed/ }),
		);
		await expect(canvasElement.querySelector("pre")?.textContent).toContain(
			"## What could improve",
		);
		await userEvent.click(
			canvas.getByRole("button", { name: /Show the feedback as the developer sees it/ }),
		);
		await canvas.findByRole("heading", { level: 4, name: "What could improve" });
	},
};
