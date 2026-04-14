import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AgentConfigList } from "./AgentConfigList";
import { mockAgentConfigs } from "./storyMockData";

/**
 * Card list of workspace agent configurations with inline edit and delete actions.
 */
const meta = {
	component: AgentConfigList,
	tags: ["autodocs"],
	args: {
		configs: mockAgentConfigs,
		isLoading: false,
		editingConfigId: "new",
		deletingConfigId: null,
		onCreateNew: fn(),
		onEdit: fn(),
		onDelete: fn(),
	},
} satisfies Meta<typeof AgentConfigList>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Editing: Story = {
	args: {
		editingConfigId: mockAgentConfigs[1].id,
	},
};

export const Empty: Story = {
	args: {
		configs: [],
	},
};
