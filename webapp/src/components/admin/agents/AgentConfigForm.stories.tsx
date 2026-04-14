import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { fn } from "storybook/test";
import { AgentConfigForm } from "./AgentConfigForm";
import { mockAgentConfigs } from "./storyMockData";
import { createDraftFromConfig, createEmptyDraft } from "./utils";

/**
 * Form for creating and editing review-agent runtime configurations.
 */
const meta = {
	component: AgentConfigForm,
	tags: ["autodocs"],
	args: {
		mode: "create",
		draft: createEmptyDraft(),
		errors: {},
		existingHasCredential: false,
		isSaving: false,
		onSubmit: fn(),
		onReset: fn(),
		onDraftChange: fn(),
	},
	render: (args) => {
		const [draft, setDraft] = useState(args.draft);
		return <AgentConfigForm {...args} draft={draft} onDraftChange={setDraft} />;
	},
} satisfies Meta<typeof AgentConfigForm>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Create: Story = {};

export const Edit: Story = {
	args: {
		mode: "edit",
		draft: createDraftFromConfig(mockAgentConfigs[1]),
		existingHasCredential: true,
	},
};

export const ValidationErrors: Story = {
	args: {
		errors: {
			name: "Name is required",
			llmApiKey: "Direct credential modes require a credential or API key.",
			allowInternet: "Direct credential modes require internet access.",
		},
		draft: {
			...createEmptyDraft(),
			agentType: "PI",
			credentialMode: "API_KEY",
			llmProvider: "OPENAI",
			allowInternet: false,
		},
	},
};
