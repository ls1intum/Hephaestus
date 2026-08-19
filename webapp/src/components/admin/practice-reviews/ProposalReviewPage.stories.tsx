import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { type FeedbackProposal, ProposalReviewPage } from "./ProposalReviewPage";

const proposal = {
	id: "feedback-42",
	practiceNames: ["Make failures actionable"],
	recipientName: "Alex Morgan",
	body: "The retry currently catches every exception and continues without recording why the operation failed. Consider catching the expected timeout explicitly and returning an error that names the affected workspace. This preserves the original cause while giving the caller an actionable next step.",
	artifact: {
		label: "MR !184",
		title: "Add resilient workspace synchronization",
		repositoryName: "hephaestus/course-project",
		url: "https://gitlab.example.com/hephaestus/course-project/-/merge_requests/184",
	},
	placement: "Merge request comment",
	evidence: [
		{
			id: "evidence-1",
			practiceName: "Make failures actionable",
			excerpt: "catch (Exception ignored) { continue; }",
		},
		{
			id: "evidence-2",
			practiceName: "Preserve observable failures",
			excerpt:
				"The timeout test verifies that synchronization continues, but does not assert an observable failure.",
		},
	],
} satisfies FeedbackProposal;

const meta = {
	title: "Workspace/Practice reviews/Proposal review page",
	component: ProposalReviewPage,
	tags: ["autodocs"],
	args: {
		proposal,
		onApprove: fn(),
		onReject: fn(),
	},
} satisfies Meta<typeof ProposalReviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Ready: Story = {
	play: async ({ canvas, args }) => {
		await expect(
			canvas.getByRole("heading", { name: "Review feedback for Alex Morgan" }),
		).toBeVisible();
		await expect(canvas.getByText("Merge request comment")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Approve and send" }));
		await expect(args.onApprove).toHaveBeenCalledWith("feedback-42");
	},
};

export const Rejecting: Story = {
	play: async ({ canvas, args }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Reject proposal" }));
		const dialog = await screen.findByRole("alertdialog");
		await userEvent.click(within(dialog).getByText("It is missing important context"));
		await userEvent.click(within(dialog).getByRole("button", { name: "Reject proposal" }));
		await expect(args.onReject).toHaveBeenCalledWith("feedback-42", "MISSING_CONTEXT");
	},
};

export const Deciding: Story = {
	args: { isDeciding: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Approve and send" })).toBeDisabled();
		await expect(canvas.getByRole("button", { name: "Reject proposal" })).toBeDisabled();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};
