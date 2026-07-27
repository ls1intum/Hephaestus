import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { expectAmountRejected } from "@/test/budget-amount-field";
import { SetBudgetDialog } from "./SetBudgetDialog";

/**
 * Instance-admin dialog to set or remove a workspace's monthly shared-model budget.
 * Open whenever a workspace is passed; `null` keeps it closed.
 */
const meta = {
	component: SetBudgetDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspace: {
			workspaceSlug: "example-workspace",
			displayName: "Example Workspace",
			instanceMonthlyBudgetUsd: 25,
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 0,
			events: 118,
			instanceBudgetVerdict: "EXHAUSTED",
			ownProviderBudgetVerdict: "WITHIN" as const,
			ownProviderPaused: false,
			instancePaused: false,
		},
		isPending: false,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof SetBudgetDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Editing an existing budget — the "Remove budget" action is offered. */
export const WithExistingCap: Story = {};

/** Workspace with no budget — no "Remove budget" action, input starts empty. */
export const Uncapped: Story = {
	args: {
		workspace: {
			workspaceSlug: "sandbox",
			displayName: "Sandbox",
			instanceTotalCostUsd: 0.42,
			ownProviderTotalCostUsd: 0,
			events: 3,
			instanceBudgetVerdict: "WITHIN",
			ownProviderBudgetVerdict: "WITHIN" as const,
			ownProviderPaused: false,
			instancePaused: false,
		},
	},
};

export const Pending: Story = {
	args: { isPending: true },
};

/**
 * One illustration, not three: every amount rule lives on `BudgetAmountDialog`, which this wrapper
 * supplies nothing but copy to. What is worth proving *here* is that the copy arrives — that the
 * rejection is raised against a field labelled "Monthly budget" and a button reading "Save budget",
 * not against this component's own defaults. The dialog is portalled, so
 * {@link expectAmountRejected} queries the document rather than the story canvas.
 */
export const InvalidEmptyValue: Story = {
	play: async ({ args }) =>
		await expectAmountRejected({
			fieldLabel: /monthly budget/i,
			submitLabel: /save budget/i,
			typed: "",
			reason: /enter an amount/i,
			onSubmit: args.onSubmit,
		}),
};
