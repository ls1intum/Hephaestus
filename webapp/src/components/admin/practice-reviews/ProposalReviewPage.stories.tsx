import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { ProposalReviewPage } from "./ProposalReviewPage";
import { longFeedbackDetail, workspacePractices } from "./story-mock-data";

const feedback = {
	...longFeedbackDetail,
	deliveryState: "AWAITING_APPROVAL" as const,
	deliveredAt: undefined,
	placements: [{ id: "proposal-summary", placementType: "SUMMARY" as const }],
};

const meta = {
	title: "Workspace admin/Practice reviews/Proposal review",
	component: ProposalReviewPage,
	parameters: {
		layout: "padded",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		feedback,
		practices: workspacePractices,
		onApprove: fn(),
		onReject: fn(),
	},
} satisfies Meta<typeof ProposalReviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Ready: Story = {
	play: async ({ canvas, args }) => {
		await expect(canvas.getByRole("heading", { name: /Feedback for/ })).toBeVisible();
		await expect(canvas.getByText("As a summary comment on the work")).toBeVisible();
		await expect(
			canvas.getByRole("link", {
				name: "A cache miss and a permission failure come back as the same 404",
			}),
		).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Approve and send" }));
		await expect(args.onApprove).toHaveBeenCalledWith(feedback.id);
		await expectNoPageOverflow();
	},
};

export const RejectingWithContext: Story = {
	play: async ({ canvas, args }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Reject feedback" }));
		const popover = await screen.findByRole("dialog");
		await userEvent.click(within(popover).getByText("Missing important context"));
		await userEvent.type(
			within(popover).getByLabelText("Note"),
			"The fallback path is not represented in the review.",
		);
		await userEvent.click(within(popover).getByRole("button", { name: "Reject feedback" }));
		await expect(args.onReject).toHaveBeenCalledWith(
			feedback.id,
			"MISSING_CONTEXT",
			"The fallback path is not represented in the review.",
		);
	},
};

export const Deciding: Story = {
	args: { isDeciding: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: /Approve and send/ })).toBeDisabled();
		await expect(canvas.getByRole("button", { name: "Reject feedback" })).toBeDisabled();
	},
};
