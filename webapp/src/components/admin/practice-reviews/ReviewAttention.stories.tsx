import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";
import { levelsOpenedBy } from "@/test/detail-stack";

import { awaitingApprovalFeedback } from "./fixtures";
import type { ReviewSectionState } from "./review-states";
import { ReviewAttention } from "./ReviewAttention";

function ready<T>(items: T[], total = items.length): ReviewSectionState<T> {
	return { status: "ready", items, total };
}

/**
 * Only what asks something of an admin: the decisions owed, and one way to work through them oldest
 * first. What failed is counted where it happened, over the range: failed reviews under What the
 * reviews did, results not processed and failed deliveries under Delivery. With nothing owed one
 * line says so, so an empty section never reads as broken.
 */
const meta = {
	component: ReviewAttention,
	parameters: { layout: "padded" },
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		// Twelve waiting: the heading says how many, and the queue walks them all.
		approvals: { state: ready(awaitingApprovalFeedback, 12) },
	},
	argTypes: { approvals: { control: false } },
} satisfies Meta<typeof ReviewAttention>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { name: "Needs you", level: 2 });
		await expect(
			canvas.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent),
		).toEqual(["Awaiting your approval 12"]);
		// One way in: the queue. No rows and no second link to the same feedback.
		await expect(canvas.queryByRole("link", { name: /^See all/u })).not.toBeInTheDocument();
		const [oldest] = awaitingApprovalFeedback;
		if (!oldest) {
			throw new Error("The fixtures hold no feedback awaiting approval");
		}
		const queue = canvas.getByRole("link", { name: "Review 12 pieces of feedback" });
		await expect(levelsOpenedBy(queue)).toEqual([`feedback:${oldest.id}`]);
		await expect(
			new URL(String(queue.getAttribute("href")), window.location.origin).searchParams.get("queue"),
		).toBe("approvals");
		// The rates line of failures is gone: they are counted over the range where they happened.
		await expect(canvas.queryByText(/failed or timed out/u)).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: /^Why last/u })).not.toBeInTheDocument();
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: /^Review \d+ (?:piece|pieces) of feedback$/u });
		await expectNoPageOverflow();
	},
};

/** Nothing is owed: one line says so. */
export const AllClear: Story = {
	args: { approvals: { state: ready([], 0) } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No feedback awaits your approval.")).toBeVisible();
		await expect(canvas.queryByRole("heading", { level: 3 })).not.toBeInTheDocument();
	},
};

/** A few waiting: still one way in, the queue. */
export const FewApprovals: Story = {
	args: { approvals: { state: ready(awaitingApprovalFeedback) } },
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: "Review 3 pieces of feedback" });
	},
};

/** Still loading is not the same as nothing owed. */
export const Loading: Story = {
	args: { approvals: { state: { status: "loading" } } },
	play: async ({ canvas }) => {
		canvas.getByText("Loading what needs you…");
		await expect(canvas.queryByText(/No feedback awaits your approval/u)).not.toBeInTheDocument();
	},
};

/** The approvals could not load: said with a retry, never read as "nothing owed". */
export const LoadError: Story = {
	args: {
		approvals: {
			state: { status: "error", error: { status: 503 }, onRetry: fn() },
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("We could not load the feedback awaiting your approval");
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};
