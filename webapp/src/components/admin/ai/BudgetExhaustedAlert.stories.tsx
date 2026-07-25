import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * Shown on the AI models page whenever the server reports a live pause for the workspace's current
 * month — without it, a workspace admin browsing models has no signal that detection and the mentor
 * are silently paused (that used to be visible only on the separate usage page).
 *
 * Two axes: *whose* cap tripped (`scope`) — the shared-model budget, which the workspace can only
 * route around, or its own provider cap, which it can lift itself — and *why* (`verdict`): the cap
 * was reached (`EXHAUSTED`), or some calls have no price set so spend can't be checked against it
 * (`UNVERIFIABLE`).
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { scope: "own" },
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The workspace's provider cap is reached — theirs to raise or remove. */
export const ProviderCapReached: Story = {
	args: { scope: "own", verdict: "EXHAUSTED" },
};

/**
 * Some calls on the workspace's own models have no price set, so their cap can't be enforced —
 * fixable right here by pricing the model.
 */
export const ProviderCapUnenforceable: Story = {
	args: { scope: "own", verdict: "UNVERIFIABLE" },
};

/**
 * The shared-model budget is reached. Warning, not destructive: the workspace's own provider keeps
 * running, and switching a purpose over is the move they own.
 */
export const SharedBudgetReached: Story = {
	args: { scope: "shared", verdict: "EXHAUSTED" },
};

/** Shared-model calls with no price set — only the host can price a shared model, so the copy says so. */
export const SharedBudgetUnverifiable: Story = {
	args: { scope: "shared", verdict: "UNVERIFIABLE" },
};

/** No explicit verdict — defaults to the cap-reached wording. */
export const Default: Story = {};
