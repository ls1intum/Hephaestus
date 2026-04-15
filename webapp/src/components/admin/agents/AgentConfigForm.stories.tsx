import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { fn } from "storybook/test";
import { AgentConfigForm } from "./AgentConfigForm";
import { mockAgentConfigs, mockAgentRunners } from "./storyMockData";
import {
	createConfigDraftFromConfig,
	createEmptyConfigDraft,
	createEmptyRunnerDraft,
	createRunnerDraftFromRunner,
} from "./utils";

/**
 * Form for creating and editing review-agent runtime configurations.
 */
const meta = {
	component: AgentConfigForm,
	tags: ["autodocs"],
	args: {
		mode: "create",
		title: "Runner",
		submitLabel: "Save runner",
		draft: createEmptyRunnerDraft(),
		configDraft: null,
		errors: {},
		existingHasCredential: false,
		isSaving: false,
		runners: mockAgentRunners,
		onReset: fn(),
		onSubmit: fn().mockResolvedValue(undefined),
		onRunnerDraftChange: fn(),
		onConfigDraftChange: fn(),
	},
	render: (args) => {
		const [runnerDraft, setRunnerDraft] = useState(args.draft);
		const [configDraft, setConfigDraft] = useState(args.configDraft);
		return (
			<AgentConfigForm
				{...args}
				draft={runnerDraft}
				configDraft={configDraft}
				onRunnerDraftChange={setRunnerDraft}
				onConfigDraftChange={setConfigDraft}
			/>
		);
	},
} satisfies Meta<typeof AgentConfigForm>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Create: Story = {};

export const Edit: Story = {
	args: {
		mode: "edit",
		draft: createRunnerDraftFromRunner(mockAgentRunners[1]),
		existingHasCredential: true,
		submitLabel: "Update runner",
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
			...createEmptyRunnerDraft(),
			agentType: "PI",
			credentialMode: "API_KEY",
			llmProvider: "OPENAI",
			allowInternet: false,
		},
	},
};

export const ConfigBinding: Story = {
	args: {
		title: "Agent config",
		submitLabel: "Save config",
		draft: null,
		configDraft: createConfigDraftFromConfig(mockAgentConfigs[0]),
		existingHasCredential: false,
	},
};

export const ConfigValidationErrors: Story = {
	args: {
		title: "Agent config",
		submitLabel: "Save config",
		draft: null,
		configDraft: createEmptyConfigDraft(),
		errors: {
			name: "Config name is required",
			runnerId: "Select a runner",
		},
		existingHasCredential: false,
	},
};
