import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AgentConfigForm } from "./AgentConfigForm";
import { mockConfigApiKey, mockConfigProxy } from "./storyMockData";

/**
 * Create/edit form for an agent runtime configuration. Owns local form state and
 * validates a credential-mode discriminated union; create mode also requires an API
 * key for direct-auth modes.
 */
const meta = {
	component: AgentConfigForm,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		isPending: false,
		onCreate: fn(),
		onUpdate: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof AgentConfigForm>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Create mode — PROXY default, key field hidden. */
export const Create: Story = {};

/** Edit a PROXY runtime (name locked, key field hidden). */
export const EditProxy: Story = {
	args: { config: mockConfigProxy, onCancel: fn() },
};

/** Edit an API_KEY runtime with a stored key (masked + clear). */
export const EditApiKeyWithStoredKey: Story = {
	args: { config: mockConfigApiKey, onCancel: fn() },
};

export const Saving: Story = {
	args: { config: mockConfigProxy, isPending: true, onCancel: fn() },
};

/**
 * Selecting the "API key" credential mode reveals the key field and auto-enables
 * internet access (exercises handleModeChange + the discriminated union).
 */
export const CreateApiKey: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The credential-mode Select is a base-ui combobox rendered in a portal.
		const modeTrigger = canvas.getByRole("combobox", { name: /credential mode/i });
		await userEvent.click(modeTrigger);
		const apiKeyOption = await screen.findByRole("option", { name: /api key/i });
		await userEvent.click(apiKeyOption);
		// Key field now visible, internet auto-enabled.
		await expect(canvas.getByLabelText(/llm api key/i)).toBeVisible();
		await expect(canvas.getByRole("switch", { name: /allow internet access/i })).toBeChecked();
	},
};

/** Submitting an empty form surfaces the "Name is required" validation error. */
export const ValidationError: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /create model/i }));
		await expect(canvas.getByText(/name is required/i)).toBeVisible();
	},
};
