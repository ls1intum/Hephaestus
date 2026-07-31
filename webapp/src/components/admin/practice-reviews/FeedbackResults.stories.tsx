import type { Meta, StoryObj } from "@storybook/react-vite";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackResults } from "./FeedbackResults";
import { reviewFeedback } from "./story-mock-data";

const meta = {
	title: "Admin/Practice reviews/Building blocks/Delivery results",
	component: FeedbackResults,
	parameters: {
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
	play: async () => {
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
