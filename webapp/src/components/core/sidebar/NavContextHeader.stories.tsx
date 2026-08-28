import type { Meta, StoryObj } from "@storybook/react";
import { Link } from "@tanstack/react-router";
import { expect } from "storybook/test";

import { SidebarProvider } from "@/components/ui/sidebar";

import { NavContextHeader } from "./NavContextHeader";

const meta = {
	component: NavContextHeader,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		title: "Mentor",
		backLink: <Link to="/" />,
	},
	decorators: [
		(Story) => (
			<SidebarProvider className="min-h-0 w-[16rem] rounded-lg border border-border bg-sidebar p-2">
				<Story />
			</SidebarProvider>
		),
	],
} satisfies Meta<typeof NavContextHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const LongTitle: Story = {
	args: { title: "A context whose name is far too long to fit in the sidebar" },
	play: async ({ canvas }) => {
		const button = canvas.getByRole("link");
		await expect(button.scrollWidth).toBeLessThanOrEqual(button.clientWidth);
	},
};
