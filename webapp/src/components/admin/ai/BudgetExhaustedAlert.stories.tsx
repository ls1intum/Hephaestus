import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * Two axes decide the copy: *whose* cap tripped (`scope`) and *why* (`verdict`) — the cap was
 * reached, or some calls have no price so spend can't be checked against it.
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { scope: "own", context: "models", workspaceSlug: "acme" },
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ProviderCapReached: Story = {
	args: { scope: "own", verdict: "EXHAUSTED" },
};

/** On the AI models page the prices are already on screen, so the banner only links to the cap. */
export const ProviderCapUnenforceable: Story = {
	args: { scope: "own", verdict: "UNVERIFIABLE", unpricedEventCount: 3 },
};

/** Warning, not destructive: the workspace's own provider keeps running. */
export const SharedBudgetReached: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED" },
};

export const SharedBudgetUnverifiable: Story = {
	args: { scope: "shared", verdict: "UNVERIFIABLE", unpricedEventCount: 1 },
};

/** The cap is edited on the usage page, so the action there is a button rather than a link. */
export const ProviderCapReachedOnUsagePage: Story = {
	args: { scope: "own", verdict: "EXHAUSTED", context: "usage" },
};

export const SharedBudgetReachedOnUsagePage: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED", context: "usage" },
};
