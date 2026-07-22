import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { InstanceLlmSettings } from "@/api/types.gen";
import { InstanceLlmSettingsCard } from "./InstanceLlmSettingsCard";

const mockSettings: InstanceLlmSettings = {
	allowWorkspaceConnections: true,
	allowedEgressHosts: "api.openai.com\nllm.example.com",
	defaultUnpricedPolicy: "WARN",
};

const meta = {
	component: InstanceLlmSettingsCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		settings: mockSettings,
		isLoading: false,
		isSubmitting: false,
		onSave: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-xl">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof InstanceLlmSettingsCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/** Every host allowed (blank allowlist) and workspace connections turned off. */
export const NoAllowlistByoDisabled: Story = {
	args: {
		settings: { allowWorkspaceConnections: false, defaultUnpricedPolicy: "WARN" },
	},
};

export const Loading: Story = {
	args: { settings: undefined, isLoading: true },
};

/** The Save button is disabled until a field actually changes. */
export const EditsEnableSave: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const saveButton = canvas.getByRole("button", { name: /save settings/i });
		await expect(saveButton).toBeDisabled();
		await userEvent.click(
			canvas.getByRole("switch", { name: /let workspaces add providers and models/i }),
		);
		await expect(saveButton).toBeEnabled();
	},
};
