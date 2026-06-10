import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { CredentialField } from "./CredentialField";

/**
 * API-key input for a model. The reveal toggle only un-masks what the admin is typing (the API never
 * returns a stored key — the proxy injects it server-side).
 */
const meta = {
	component: CredentialField,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
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

/** Fresh model — no stored key yet. */
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

/** Validation error surfaced. */
export const WithError: Story = {
	args: { error: "An API key is required." },
};
