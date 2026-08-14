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
		await expect(
			canvas.getByText("This was judged against an older version of the practice"),
		).toBeVisible();
	},
};

/**
 * The harder of the two to word, and the one the product owner said he could not follow.
 *
 * "Currentness is unknown" named a property of the record; the reader's question is whether they can
 * trust what they are reading. This says what is missing (the record of which practice text was
 * read), why that leaves the question open, and what to do about it.
 */
export const Unverifiable: Story = {
	args: { currentness: "UNVERIFIABLE" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("We can't tell which version of the practice this was judged against"),
		).toBeVisible();
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
