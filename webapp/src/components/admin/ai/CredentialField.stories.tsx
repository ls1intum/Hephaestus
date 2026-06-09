import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { CredentialField } from "./CredentialField";

/**
 * Credential input for direct-auth modes. Renders nothing in PROXY mode; the reveal
 * toggle only un-masks what the admin is typing (the API never returns a stored key).
 */
const meta = {
	component: CredentialField,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		mode: "API_KEY",
		hasStoredKey: false,
		value: "",
		onChange: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-md">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof CredentialField>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Fresh API_KEY config — no stored key yet. */
export const Empty: Story = {};

/** In-progress key typed by the admin. */
export const WithInput: Story = {
	args: { value: "sk-test-1234567890" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const input = canvas.getByLabelText(/llm api key/i);
		await expect(input).toHaveAttribute("type", "password");
		await userEvent.click(canvas.getByRole("button", { name: /show key/i }));
		await expect(input).toHaveAttribute("type", "text");
		await expect(canvas.getByRole("button", { name: /hide key/i })).toBeInTheDocument();
	},
};

/** A key is already stored — masked placeholder + "keep current" hint + clear action. */
export const StoredKeyMasked: Story = {
	args: { hasStoredKey: true, onClear: fn() },
};

/** OAUTH mode behaves like API_KEY for the key input. */
export const OAuthMode: Story = {
	args: { mode: "OAUTH", hasStoredKey: true, onClear: fn() },
};

/** PROXY mode renders nothing (key lives on the internal proxy). */
export const ProxyHidden: Story = {
	args: { mode: "PROXY" },
};

/** Validation error surfaced. */
export const WithError: Story = {
	args: { error: "API key is required for direct authentication." },
};
