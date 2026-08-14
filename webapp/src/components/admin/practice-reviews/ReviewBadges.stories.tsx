import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";
import { ClaimCurrentnessAlert } from "./ReviewBadges";

const meta = {
	title: "Workspace admin/Practice reviews/Claim currentness alert",
	component: ClaimCurrentnessAlert,
	args: { currentness: "STALE" },
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof ClaimCurrentnessAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Stale: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This result uses older review rules")).toBeVisible();
	},
};

/**
 * Two different sentences, because they answer different questions: stale means we compared and the
 * rules had moved, unknown means we could not compare.
 */
export const Unverifiable: Story = {
	args: { currentness: "UNVERIFIABLE" },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Currentness is unknown")).toBeVisible();
	},
};

/**
 * The common case renders nothing at all.
 *
 * A result on the current rules is the norm, and a banner saying so on every observation would be a line
 * every reader learns to skip — which is what would make the two above invisible when they appear.
 */
export const Current: Story = {
	args: { currentness: "CURRENT" },
	play: async ({ canvasElement }) => {
		await expect(canvasElement.querySelector('[data-slot="alert"]')).toBeNull();
	},
};
