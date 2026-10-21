import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";
import { PasskeySection } from "./PasskeySection";

const status = {
	protectionEnabled: false,
	recoveryRequired: false,
	instanceAdminRequired: true,
	workspaceAdminRequired: false,
	verified: false,
	credentials: [],
};
const meta = {
	component: PasskeySection,
	tags: ["autodocs"],
	args: {
		status,
		supported: true,
		onRetry: fn(),
		onRegister: fn(),
		onVerify: fn(),
		onProtection: fn(),
		onRemove: fn(),
		onCreateRecoveryCodes: fn(),
		onRecover: fn(),
	},
} satisfies Meta<typeof PasskeySection>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Empty: Story = {
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("No passkeys yet. Register a passkey to protect admin access."),
		).toBeVisible();
	},
};
export const Loading: Story = { args: { loading: true } };
export const Error: Story = {
	args: { status: undefined, error: "We could not load your passkeys. Try again." },
};
export const Recovery: Story = { args: { status: { ...status, recoveryRequired: true } } };
/**
 * One passkey, not confirmed recently. Confirming comes first; every change waits for it, adding a
 * second passkey too, and stays focusable so its reason can be read.
 */
export const Enrolled: Story = {
	args: {
		status: { ...status, credentials: [{ id: "test-credential", label: "Personal device" }] },
	},
	play: async ({ args, canvas, userEvent }) => {
		const verify = canvas.getByRole("button", { name: "Verify passkey" });
		const register = canvas.getByRole("button", { name: "Register passkey" });
		await expect(verify).toBeEnabled();
		await expect(register).toHaveAttribute("aria-disabled", "true");
		await expect(register).toHaveAccessibleDescription(
			/Confirm it’s you with one of your passkeys/u,
		);
		await expect(canvas.getByRole("button", { name: "Remove Personal device" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await userEvent.click(verify);
		await expect(args.onVerify).toHaveBeenCalledOnce();
	},
};
/** Confirmed recently: no confirm step, and adding, removing and new codes are open. */
export const Verified: Story = {
	args: {
		status: {
			...status,
			verified: true,
			protectionEnabled: true,
			credentials: [
				{ id: "test-credential", label: "Personal device" },
				{ id: "second-credential", label: "Phone" },
			],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: "Verify passkey" })).toBeNull();
		await expect(canvas.getByRole("button", { name: "Register passkey" })).toBeEnabled();
		await expect(canvas.getByRole("button", { name: "Remove Phone" })).toBeEnabled();
		await expect(canvas.getByRole("button", { name: "Create new recovery codes" })).toBeEnabled();
	},
};
/** New recovery codes, shown once, with a way to copy them all. */
export const RecoveryCodesShown: Story = {
	args: {
		status: {
			...status,
			verified: true,
			credentials: [{ id: "test-credential", label: "Personal device" }],
		},
		recoveryCodes: ["k3Jx-0001", "k3Jx-0002", "k3Jx-0003"],
	},
	play: async ({ canvas }) => {
		const list = canvas.getByRole("list", { name: "Recovery codes" });
		await expect(within(list).getAllByRole("listitem")).toHaveLength(3);
		await expect(canvas.getByRole("button", { name: "Copy codes" })).toBeVisible();
	},
};
export const Pending: Story = { args: { pending: true, pendingAction: "register" } };
export const Unsupported: Story = { args: { supported: false } };

export const LongCredentialName: Story = {
	args: {
		status: {
			protectionEnabled: true,
			recoveryRequired: false,
			instanceAdminRequired: true,
			workspaceAdminRequired: false,
			verified: true,
			credentials: [
				{
					id: "long",
					label:
						"Personal workstation with a hardware security key and a long device name for admin access",
				},
			],
		},
	},
};
