import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, within } from "storybook/test";
import { AdminReviewCycleSettings } from "./AdminReviewCycleSettings";

const meta = {
	component: AdminReviewCycleSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<QueryClientProvider client={new QueryClient()}>
				<Story />
			</QueryClientProvider>
		),
	],
	args: { workspaceSlug: "demo", onSaved: fn() },
} satisfies Meta<typeof AdminReviewCycleSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Unset day/time falls back to Tuesday / 09:00. */
export const Default: Story = {};

export const Prefilled: Story = { args: { day: 5, time: "17:30" } };

export const InvalidTime: Story = {
	args: { time: "25:00" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Time must be in HH:mm format.")).toBeVisible();
		await expect(canvas.getByRole("button", { name: /save/i })).toBeDisabled();
	},
};
