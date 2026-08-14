import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import { expectNoPageOverflow } from "@/test/reflow";
import { FeedbackResults } from "./FeedbackResults";
import { reviewFeedback } from "./story-mock-data";

const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Delivery results",
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

/**
 * Every outcome, on both places feedback can go.
 *
 * <p>The row's name is the feedback's own opening words. It used to be "Feedback for {person}",
 * computed from the recipient, so a page of twenty-five rows was twenty-five near-identical titles
 * and the only text telling them apart was a clamped preview underneath the link. The person moved
 * to the chips, where it is scanned.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: new RegExp(reviewFeedback[0].bodyPreview) });
		// A conversation row's outcome is refined by its place, and still begins with the stem of the
		// stored state so the Outcome facet remains findable from the row.
		canvas.getByText("Queued for conversation");
		canvas.getByText("Delivered in conversation");
		canvas.getByText("Failed to deliver");
		canvas.getByText("Replaced by newer");
		// A withheld row carries its own precise reason; the badge only says something stopped it.
		canvas.getByText("The work was already merged, so a note on it would arrive too late.");
		await expect(canvas.queryAllByText(/Feedback for/)).toHaveLength(0);
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		within(canvasElement).getByText("Withheld");
		await expectNoPageOverflow();
	},
};

/** Nothing was composed, so the row says that rather than showing an empty title. */
export const WithoutAPreview: Story = {
	args: {
		state: {
			status: "ready",
			feedback: [{ ...reviewFeedback[1], bodyPreview: undefined }],
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		canvas.getByRole("link", { name: "No feedback text was composed" });
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
