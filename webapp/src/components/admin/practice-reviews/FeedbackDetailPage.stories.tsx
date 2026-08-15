import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackDetailPage } from "./FeedbackDetailPage";
import { longFeedbackDetail, reviewFeedbackDetail } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

const meta = {
	title: "Workspace admin/Practice reviews/Feedback details",
	component: FeedbackDetailPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: reviewHandlers() },
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
		// Twice, and deliberately: once on the card around the text — so a body that never reached
		// anybody cannot be quoted as though it had — and once as the trace's terminal step.
		await expect(await canvas.findAllByText("Withheld")).toHaveLength(2);
		canvas.getByRole("link", { name: "See everything reviewed on this work" });
		canvas.getByRole("link", { name: "in a review" });
		await expect(canvas.queryByText("Technical details")).not.toBeInTheDocument();
		canvas.getByText("Policy kept it quiet");
		canvas.getByText("Found while reviewing past work, which is measured but never sent.");
		await expectNoPageOverflow();
	},
};

export const Delivered: Story = {
	args: { feedbackId: "99999999-6666-6666-6666-666666666666" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// Exactly one "Delivered": the trace's terminal step. The composed text carries no badge,
		// because reaching the developer is the ordinary case.
		await expect(await canvas.findAllByText("Delivered")).toHaveLength(1);
		canvas.getByText(/As an inline note on the work/);
		canvas.getByText("server/src/main/resources/application.yml:118–120");
	},
};

/**
 * A note of the length the composer really produces. Nothing on this page truncates it: the cut an
 * operator sees in the delivery list is a list preview and stops there.
 */
export const LongFeedback: Story = {
	args: { feedbackId: longFeedbackDetail.id },
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText(/2 issues to tighten in this change/);
		// The end of the note as well as its opening, so nothing between them was dropped — including
		// the quoted code, which is the part a preview cannot carry.
		canvas.getByText(/without HTTP\./);
		canvas.getByText(/repository\.findVisible/);
		canvas.getByRole("link", {
			name: "A cache miss and a permission failure come back as the same 404",
		});
		canvas.getByRole("link", {
			name: "Three of the new tests are named after the method they call",
		});
		canvas.getByText("What this feedback is about");
		canvas.getByText("Supporting this feedback");
		await expectNoPageOverflow();
	},
};

export const QueuedForConversation: Story = {
	args: { feedbackId: "11111111-4444-4444-4444-444444444444" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findAllByText("Queued for conversation");
		canvas.getByText("How should we roll back the pricing migration?");
	},
};

export const RenderedAndSource: Story = {
	args: { feedbackId: "44444444-4444-4444-4444-444444444444" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		await canvas.findByRole("link", { name: "See the feedback this replaced" });
		await canvas.findByText(/2 issues to tighten in this change/);
		await userEvent.click(canvas.getByRole("button", { name: "Source" }));
		await expect(canvasElement.querySelector("pre")?.textContent).toContain("```java");
		await userEvent.click(canvas.getByRole("button", { name: "Rendered" }));
		await canvas.findByText(/2 issues to tighten in this change/);
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load this feedback");
	},
};
