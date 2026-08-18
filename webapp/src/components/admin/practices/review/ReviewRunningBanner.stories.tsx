import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { ReviewRunningBanner } from "./ReviewRunningBanner";

const readyModel = { binding: { purpose: "PRACTICE_REVIEW", enabled: true, ready: true } as const };

/**
 * The standing answer to "is anything actually being reviewed here", above three tabs of settings
 * that are all inert if the answer is no. One story per tone, because each tone is a different
 * sentence and a different next step — and because the icon and the headline have to carry the state
 * on their own, which a reader can only check by seeing them side by side.
 */
const meta = {
	title: "Workspace admin/Practices/Review/Running banner",
	component: ReviewRunningBanner,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		running: { enabled: true, model: { ...readyModel, isLoading: false, isError: false } },
	},
} satisfies Meta<typeof ReviewRunningBanner>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The only quiet, affirming state on the page: everything is on and a review can start. */
export const Running: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Reviews are running");
	},
};

/** Reviews are on, but the model is still being looked up — so nothing is claimed yet. */
export const Checking: Story = {
	args: { running: { enabled: true, model: { isLoading: true, isError: false } } },
};

/** The lookup failed. Unknown is not the same as ready, and the banner refuses to round it up. */
export const Unconfirmed: Story = {
	args: { running: { enabled: true, model: { isLoading: false, isError: true } } },
};

/** Reviews are on and no model can run them, which is the state that silently produces nothing. */
export const Blocked: Story = {
	args: { running: { enabled: true, model: { isLoading: false, isError: false } } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Reviews can't start");
	},
};

/** The switch is off, so every setting under it is a plan rather than a behaviour. */
export const Off: Story = {
	args: {
		running: { enabled: false, model: { ...readyModel, isLoading: false, isError: false } },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Reviews are off");
	},
};
