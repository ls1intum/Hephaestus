import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import { ConnectionStateNotice } from "./ConnectionStateNotice";

/**
 * The one place a non-ACTIVE connection state is explained.
 *
 * `connectionState` is a wire enum, and the surface used to render it by lowercasing: "Slack is
 * uninstalled.", "Connection is suspended." — a machine token dressed as a sentence, worded
 * differently at each call site for the same fact, and silent on why sync stopped or what to do. Every
 * integration now shares this component, so the states read identically wherever they appear.
 *
 * Severity is graded on consequence, not on enum: SUSPENDED and UNINSTALLED mean *nothing is syncing*
 * and warrant a warning; PENDING resolves on its own and stays plain.
 */
const meta = {
	component: ConnectionStateNotice,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { displayName: "Slack" },
} satisfies Meta<typeof ConnectionStateNotice>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Setup is still finishing. Nothing is owed, so this states the fact and doesn't shout. */
export const Pending: Story = {
	args: { connectionState: "PENDING" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/finishing setup/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/slack is pending/i)).not.toBeInTheDocument();
	},
};

/** The provider suspended the connection — a warning, because sync has stopped. */
export const Suspended: Story = {
	args: { connectionState: "SUSPENDED" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/syncing is paused/i)).toBeInTheDocument();
		await expect(canvas.getByText(/reconnect to resume/i)).toBeInTheDocument();
	},
};

/** The app was removed upstream. Replaces the notorious "Slack is uninstalled." */
export const Uninstalled: Story = {
	args: { connectionState: "UNINSTALLED" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/the app was removed/i)).toBeInTheDocument();
		await expect(canvas.queryByText(/slack is uninstalled/i)).not.toBeInTheDocument();
	},
};

/** The same states, worded for a different integration — one component, one vocabulary. */
export const SuspendedOutline: Story = {
	args: { connectionState: "SUSPENDED", displayName: "Outline" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/outline was suspended by the provider/i)).toBeInTheDocument();
	},
};

/** ACTIVE has nothing to explain, so the notice renders nothing at all. */
export const Active: Story = {
	args: { connectionState: "ACTIVE" },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("alert")).not.toBeInTheDocument();
	},
};

/** No connection at all — also nothing to explain. */
export const NoConnection: Story = {
	args: { connectionState: undefined },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByRole("alert")).not.toBeInTheDocument();
	},
};
