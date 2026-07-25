import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * The single owner of the four pause banners. Both the AI usage page and the AI models page render
 * this component, so the sentences cannot drift between the two surfaces; `context` picks only the
 * action offered, which is always the remedy the current page cannot perform itself.
 *
 * Two axes decide the copy: *whose* cap tripped (`scope`) — the shared-model budget, which the
 * workspace can only route around, or its own provider cap, which it can lift itself — and *why*
 * (`verdict`): the cap was reached (`EXHAUSTED`), or some calls have no price so spend can't be
 * checked against it (`UNVERIFIABLE`).
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { scope: "own", context: "models", workspaceSlug: "acme" },
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The workspace's provider cap is reached — theirs to raise or remove. */
export const ProviderCapReached: Story = {
	args: { scope: "own", verdict: "EXHAUSTED" },
};

/**
 * Some calls on the workspace's own models have no price, so their cap can't be enforced. On the AI
 * models page the prices are already on screen, so the banner only links to the cap.
 */
export const ProviderCapUnenforceable: Story = {
	args: { scope: "own", verdict: "UNVERIFIABLE", unpricedEventCount: 3 },
};

/**
 * The shared-model budget is reached. Warning, not destructive: the workspace's own provider keeps
 * running, and switching a purpose over is the move they own — already below this banner on the AI
 * models page, so no button here.
 */
export const SharedBudgetReached: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED" },
};

/** Shared-model calls with no price — only the host can price a shared model, so the copy says so. */
export const SharedBudgetUnverifiable: Story = {
	args: { scope: "shared", verdict: "UNVERIFIABLE", unpricedEventCount: 1 },
};

/**
 * The same reached-cap banner on the AI usage page. The cap is edited here, so the action is a
 * button rather than a link — the sentence above it is byte-identical to `ProviderCapReached`.
 */
export const ProviderCapReachedOnUsagePage: Story = {
	args: { scope: "own", verdict: "EXHAUSTED", context: "usage" },
};

/**
 * The shared-model budget reached, seen from the usage page: the one move the workspace admin owns
 * is on the other page, so this is where the link appears.
 */
export const SharedBudgetReachedOnUsagePage: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED", context: "usage" },
};

/** No explicit verdict — defaults to the cap-reached wording. */
export const Default: Story = {
	args: {},
};
