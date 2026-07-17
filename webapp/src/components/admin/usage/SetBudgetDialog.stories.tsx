import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SetBudgetDialog } from "./SetBudgetDialog";

/**
 * Instance-admin dialog to set or remove a workspace's monthly LLM budget cap.
 * Open whenever a workspace is passed; `null` keeps it closed.
 */
const meta = {
	component: SetBudgetDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspace: {
			workspaceId: 1,
			workspaceSlug: "obsphera",
			displayName: "Obsphera",
			monthlyBudgetUsd: 25,
			costUsd: 25.0142,
			events: 118,
			overBudget: true,
		},
		isPending: false,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof SetBudgetDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Editing an existing cap — the "Remove cap" action is offered. */
export const WithExistingCap: Story = {};

/** Uncapped workspace — no "Remove cap" action, input starts empty. */
export const Uncapped: Story = {
	args: {
		workspace: {
			workspaceId: 3,
			workspaceSlug: "sandbox",
			displayName: "Sandbox",
			costUsd: 0.42,
			events: 3,
			overBudget: false,
		},
	},
};

/** Save in flight — inputs and actions disabled. */
export const Pending: Story = {
	args: { isPending: true },
};
