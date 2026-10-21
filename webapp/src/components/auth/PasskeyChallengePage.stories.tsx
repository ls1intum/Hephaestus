import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import { PasskeyChallengePage, type PasskeyChallengeState } from "./PasskeyChallengePage";

const ready = {
	status: "ready",
	step: "confirm",
	justAdded: false,
	recovering: false,
	supported: true,
} satisfies PasskeyChallengeState;

const meta = {
	component: PasskeyChallengePage,
	tags: ["autodocs"],
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
	args: { state: ready, onAdd: fn(), onConfirm: fn(), onLeave: fn() },
} satisfies Meta<typeof PasskeyChallengePage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** An enrolled administrator: one primary action and a way out. */
export const Confirm: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Confirm with your passkey" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Confirm with passkey" })).toBeEnabled();
		await expect(canvas.queryByRole("button", { name: "Add passkey" })).toBeNull();
		await expect(canvas.getByRole("button", { name: "Not now" })).toBeEnabled();
		// One exit: the brand mark is not a second way out that behaves differently.
		await expect(canvas.queryByRole("link", { name: /home/iu })).toBeNull();
		await expect(canvas.getByText("Cannot use your passkey?")).toBeVisible();
	},
};

/** No passkey yet: adding comes first, and the page says a confirmation follows. */
export const AddFirst: Story = {
	args: { state: { ...ready, step: "add" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Add a passkey to continue" })).toBeVisible();
		await expect(canvas.getByRole("list", { name: "Steps" })).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Add passkey" })).toBeEnabled();
		// Nothing to recover before any passkey exists.
		await expect(canvas.queryByText("Cannot use your passkey?")).toBeNull();
	},
};

/** The passkey was saved in this visit; confirming with it is the remaining step. */
export const JustAdded: Story = {
	args: { state: { ...ready, justAdded: true } },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("Your passkey is saved. Confirm with it once to continue."),
		).toBeVisible();
		canvas.getByText("(done)");
	},
};

export const AfterRecovery: Story = {
	args: { state: { ...ready, step: "add", recovering: true } },
};

export const WaitingForPasskey: Story = {
	args: { state: { ...ready, pending: "confirm" } },
	play: async ({ canvas }) => {
		// Still focusable while the browser prompt is open, but it refuses a second press.
		const waiting = canvas.getByRole("button", { name: /Waiting for your passkey/u });
		await expect(waiting).toHaveAttribute("aria-disabled", "true");
		await expect(waiting).not.toHaveAttribute("disabled");
	},
};

/** A closed, timed-out or declined prompt keeps the same retry path. */
export const PromptCanceled: Story = {
	args: {
		state: {
			...ready,
			error: "The passkey check did not finish. Try again when you’re ready.",
		},
	},
	play: async ({ canvas }) => {
		// One announcer: the persistent live region, with no second alert role nested inside it.
		await expect(canvas.queryByRole("alert")).toBeNull();
		await expect(canvas.getByText("Not confirmed yet").closest("[aria-live]")).toHaveAttribute(
			"aria-live",
			"assertive",
		);
		await expect(canvas.getByRole("button", { name: "Confirm with passkey" })).toBeEnabled();
	},
};

export const Unsupported: Story = {
	args: { state: { ...ready, supported: false } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This browser cannot use passkeys")).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "Confirm with passkey" })).toBeNull();
		await expect(canvas.getByRole("button", { name: "Not now" })).toBeVisible();
	},
};

export const Loading: Story = { args: { state: { status: "loading" } } };

export const LoadError: Story = {
	args: {
		state: { status: "error", message: "Check your connection, then try again.", onRetry: fn() },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Try again" })).toBeEnabled();
	},
};
