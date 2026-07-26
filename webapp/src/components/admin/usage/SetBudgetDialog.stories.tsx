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

/** Save in flight — inputs and actions disabled. */
export const Pending: Story = {
	args: { isPending: true },
};

/**
 * Each amount the form must refuse, and the reason it gives — separate stories because each is a
 * different thing on screen. The dialog is portalled, so {@link expectAmountRejected} queries the
 * document rather than the story canvas.
 */
const rejects =
	(typed: string, reason: RegExp): Story["play"] =>
	async ({ args }) =>
		await expectAmountRejected({
			fieldLabel: /monthly budget/i,
			submitLabel: /save budget/i,
			typed,
			reason,
			onSubmit: args.onSubmit,
		});

/** Submitting a cleared field surfaces *why* it was rejected instead of silently doing nothing. */
export const InvalidEmptyValue: Story = { play: rejects("", /enter an amount/i) };

/** Sub-cent precision is rejected in the field rather than by a native browser bubble. */
export const InvalidSubCentValue: Story = { play: rejects("25.005", /two decimal places/i) };

/** A negative amount is rejected with its own reason, and nothing is submitted. */
export const InvalidNegativeValue: Story = { play: rejects("-5", /\$0 or more/i) };
