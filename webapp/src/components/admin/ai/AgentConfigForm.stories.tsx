import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { AgentConfigForm } from "./AgentConfigForm";
import { mockConfigApiKey, mockConfigProxy } from "./storyMockData";

/**
 * Create/edit form for an AI model. Always "API key over the proxy": the key is required on create and
 * injected server-side, so there is no credential-mode picker.
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

/** Create mode — the API-key field is always shown. */
export const Create: Story = {};

/** Edit a model (name locked, key kept unless replaced/cleared). */
export const EditProxy: Story = {
	args: { config: mockConfigProxy, onCancel: fn() },
};

/** Edit a model with a stored key (masked placeholder + Clear). */
export const EditApiKeyWithStoredKey: Story = {
	args: { config: mockConfigApiKey, onCancel: fn() },
};

export const Saving: Story = {
	args: { config: mockConfigProxy, isPending: true, onCancel: fn() },
};

/** Submitting an empty form surfaces the "Name is required" validation error. */
export const ValidationError: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /create model/i }));
		await expect(canvas.getByText(/name is required/i)).toBeVisible();
	},
};

/** A name without a key surfaces the "API key is required" error (the key is mandatory on create). */
export const MissingKey: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByLabelText(/^name$/i), "GPT-4o");
		await userEvent.click(canvas.getByRole("button", { name: /create model/i }));
		await expect(canvas.getByText(/api key is required/i)).toBeVisible();
	},
};
