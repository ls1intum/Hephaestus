import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackResults } from "./FeedbackResults";
import { reviewFeedback } from "./story-mock-data";

const meta = {
	title: "Admin/Practice reviews/Building blocks/Delivery results",
	component: FeedbackResults,
	parameters: {
		a11y: { test: "error" },
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: { workspaceSlug: "demo", state: { status: "ready", feedback: reviewFeedback } },
} satisfies Meta<typeof FeedbackResults>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const firstFeedback = within(within(canvasElement).getAllByRole("listitem")[0]);
		await expect(firstFeedback.getByText("Message for Ada Lovelace")).toBeVisible();
		await expect(firstFeedback.getByText(reviewFeedback[0].bodyPreview)).toBeVisible();
		await expect(firstFeedback.getByText("Delivered")).toBeVisible();
		await expect(firstFeedback.getByText("Alongside the work")).toBeVisible();
		await expect(firstFeedback.getByText("2 findings")).toBeVisible();
		await expect(firstFeedback.getByText(/PR #1420/)).toBeVisible();
		await expectNoPageOverflow();
	},
};
export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
};
export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	parameters: { chromatic: { viewports: [1440] } },
};
export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true } },
	parameters: { chromatic: { viewports: [1440] } },
};
