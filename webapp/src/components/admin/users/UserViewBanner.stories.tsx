import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent } from "storybook/test";

import { expectNoPageOverflow } from "@/stories/reflow";

import { UserViewBanner } from "./UserViewBanner";

const meta = {
	component: UserViewBanner,
	tags: ["autodocs"],
	args: { name: "Sam", workspace: "Engineering", unclaimed: false, onExit: fn() },
} satisfies Meta<typeof UserViewBanner>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args, canvas }) => {
		await expect(
			canvas.getByRole("region", { name: "Viewing Sam in Engineering (read-only)" }),
		).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Exit user view" }));
		await expect(args.onExit).toHaveBeenCalledOnce();
	},
};

/** Roster sync created the account; the person has not signed in, and the banner says so. */
export const UnclaimedAccount: Story = {
	args: { unclaimed: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/has not signed in yet/u)).toBeVisible();
	},
};

export const LongName: Story = {
	args: {
		name: "Maximilian Alexander von Hohenzollern-Sigmaringen-Weiler",
		workspace: "Research Platform Engineering — Northern Europe",
	},
};

export const Reflow: Story = {
	args: { unclaimed: true },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: expectNoPageOverflow,
};
