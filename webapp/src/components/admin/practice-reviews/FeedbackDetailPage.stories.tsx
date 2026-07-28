import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import type { ReviewFeedbackDetail } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackDetailPage } from "./FeedbackDetailPage";
import { reviewFeedbackDetail } from "./story-mock-data";

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
	title: "Admin/Practice reviews/Message details",
	component: FeedbackDetailPage,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
					HttpResponse.json(reviewFeedbackDetail),
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
			suppressionReason: undefined,
			channel: undefined,
		},
	},
} satisfies Meta<typeof FeedbackDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NotDelivered: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Not delivered")).toBeVisible();
		await expect(canvas.getByRole("heading", { name: "Message for Grace Hopper" })).toBeVisible();
		await expect(
			canvas.getByRole("link", { name: "View all findings and feedback for this work" }),
		).toBeVisible();
		await expect(canvas.getByText("The route exposes an internal detection term")).toBeVisible();
		await expect(canvas.getByText("Destination: Alongside the work")).toBeVisible();
		await expect(canvas.getByText("This message was not posted.")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "View Markdown source" })).toBeVisible();
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
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Delivered")).toBeVisible();
		await expect(canvas.getByText("Inline note")).toBeVisible();
		await expect(
			canvas.getByText("server/src/main/java/ReviewController.java:42–44"),
		).toBeVisible();
		await expect(canvas.queryByText("This message was not posted.")).not.toBeInTheDocument();
	},
};
