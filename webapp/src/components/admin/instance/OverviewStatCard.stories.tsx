import type { Meta, StoryObj } from "@storybook/react";
import { Building2 } from "lucide-react";
import { OverviewStatCard } from "./OverviewStatCard";

const meta = {
	component: OverviewStatCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		label: "Workspaces",
		value: 12,
		hint: "9 active",
		icon: Building2,
		to: "/admin/workspaces",
	},
} satisfies Meta<typeof OverviewStatCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Zero: Story = {
	args: { value: 0, hint: "None created yet" },
};

export const Loading: Story = {
	args: { isLoading: true },
};
