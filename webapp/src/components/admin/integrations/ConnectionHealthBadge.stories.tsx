import type { Meta, StoryObj } from "@storybook/react";
import { ConnectionHealthBadge } from "./ConnectionHealthBadge";

const meta = {
	component: ConnectionHealthBadge,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { health: "HEALTHY" },
} satisfies Meta<typeof ConnectionHealthBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Healthy: Story = { args: { health: "HEALTHY" } };
export const Pending: Story = { args: { health: "PENDING" } };
export const Degraded: Story = { args: { health: "DEGRADED" } };
export const Failed: Story = { args: { health: "FAILED" } };
export const Suspended: Story = { args: { health: "SUSPENDED" } };
