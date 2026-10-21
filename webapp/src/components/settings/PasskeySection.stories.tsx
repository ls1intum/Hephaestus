import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";
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
 * One passkey, not confirmed recently, with the personal protection enrolling turned on. Confirming
 * comes first; every change waits for it, adding a second passkey too, and stays focusable so its
 * reason can be read.
 */
export const Enrolled: Story = {
	args: {
		status: {
			...status,
			protectionEnabled: true,
			credentials: [{ id: "test-credential", label: "Personal device" }],
		},
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
const lastPasskey = [{ id: "test-credential", label: "Personal device" }];

/** The last passkey, confirmed recently, while personal protection is on: it stays, and says why. */
export const LastPasskeyProtected: Story = {
	args: {
		status: { ...status, verified: true, protectionEnabled: true, credentials: lastPasskey },
	},
	play: async ({ canvas, userEvent }) => {
		const remove = canvas.getByRole("button", { name: "Remove Personal device" });
		await expect(remove).toHaveAttribute("aria-disabled", "true");
		await expect(remove).toHaveAccessibleDescription(
			"Turn off personal admin protection before you remove your last passkey.",
		);
		await userEvent.click(remove);
		await expect(screen.queryByRole("alertdialog")).toBeNull();
	},
};

/**
 * The last passkey, confirmed recently, with personal protection off. It can go, after a confirmation
 * that says what stops working.
 */
export const LastPasskeyRemovable: Story = {
	args: { status: { ...status, verified: true, credentials: lastPasskey } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Remove Personal device" }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(dialog).toHaveTextContent(/This is your last passkey\./u);
		await expect(dialog).toHaveTextContent(/until you add and verify a new one/u);
		await userEvent.click(within(dialog).getByRole("button", { name: "Remove last passkey" }));
		await waitFor(async () => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
	},
};

/** The last passkey during recovery: recovery ends with a verified passkey, so this one stays. */
export const LastPasskeyDuringRecovery: Story = {
	args: {
		status: { ...status, verified: true, recoveryRequired: true, credentials: lastPasskey },
	},
	play: async ({ canvas }) => {
		const remove = canvas.getByRole("button", { name: "Remove Personal device" });
		await expect(remove).toHaveAttribute("aria-disabled", "true");
		await expect(remove).toHaveAccessibleDescription(/Recovery is active\./u);
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
