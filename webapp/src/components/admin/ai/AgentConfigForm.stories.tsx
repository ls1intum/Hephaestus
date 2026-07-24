import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { AgentConfigForm } from "./AgentConfigForm";
import {
	mockAvailableModels,
	mockConfigApiKey,
	mockConfigBoundOwn,
	mockConfigBoundShared,
} from "./storyMockData";

/**
 * Create/edit form for an agent's model binding — a picker over the workspace's available models
 * (shared or its own provider), never a raw provider/API-key form.
 */
const meta = {
	component: AgentConfigForm,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		availableModels: mockAvailableModels,
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

/** Create mode — a model must be picked before saving. */
export const Create: Story = {};

/** Editing a config bound to a shared (instance catalog) model. */
export const EditBoundToShared: Story = {
	args: { config: mockConfigBoundShared, onCancel: fn() },
};

/** Editing a config bound to the workspace's own provider. */
export const EditBoundToOwnProvider: Story = {
	args: { config: mockConfigBoundOwn, onCancel: fn() },
};

/** A config from before the model catalog: the legacy hint replaces the old editable fields. */
export const LegacyUnbound: Story = {
	args: { config: mockConfigApiKey, onCancel: fn() },
};

export const Saving: Story = {
	args: { config: mockConfigBoundShared, isPending: true, onCancel: fn() },
};

/** Submitting an empty create form surfaces both "Name is required" and "Select a model". */
export const ValidationError: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /create configuration/i }));
		await expect(canvas.getByText(/name is required/i)).toBeVisible();
		// Exact text: the trigger's "Select a model…" placeholder would otherwise also match /select a model/i.
		await expect(canvas.getByText("Select a model.")).toBeVisible();
	},
};
