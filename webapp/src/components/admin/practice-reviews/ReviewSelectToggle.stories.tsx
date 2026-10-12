import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { ReviewSelectToggle } from "./ReviewSelectToggle";

const REVIEW_ID = "11111111-1111-1111-1111-111111111111";

/**
 * A row's "Track". It reads and writes the address, so a story starts the router at the address it
 * shows: pressed is the selection the URL holds, and a press writes the next one.
 */
const meta = {
	component: ReviewSelectToggle,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		selection: { kind: "review", agentJobId: REVIEW_ID },
		name: "review of Member-facing review activity",
	},
} satisfies Meta<typeof ReviewSelectToggle>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NotSelected: Story = {
	play: async ({ canvas, userEvent }) => {
		const toggle = canvas.getByRole("button", {
			name: "Track review of Member-facing review activity",
		});
		await expect(toggle).toHaveAttribute("aria-pressed", "false");
		await userEvent.click(toggle);
		// The address now holds the review, which the toggle reads back as pressed.
		await expect(toggle).toHaveAttribute("aria-pressed", "true");
	},
};

/** The selection in the address is this row's: a press clears it. */
export const Selected: Story = {
	parameters: { router: { initialUrl: `/?agentJobId=${REVIEW_ID}&selected=true` } },
	play: async ({ canvas, userEvent }) => {
		const toggle = canvas.getByRole("button", {
			name: "Track review of Member-facing review activity",
		});
		await expect(toggle).toHaveAttribute("aria-pressed", "true");
		await userEvent.click(toggle);
		await expect(toggle).toHaveAttribute("aria-pressed", "false");
	},
};

/**
 * A result names its review too, but the review is not the selection: a row of that review
 * stays unpressed.
 */
export const AnotherKindSelected: Story = {
	parameters: {
		router: {
			initialUrl: `/?agentJobId=${REVIEW_ID}&practiceResultId=22222222-2222-2222-2222-222222222222&selected=true`,
		},
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("button", { name: "Track review of Member-facing review activity" }),
		).toHaveAttribute("aria-pressed", "false");
	},
};
