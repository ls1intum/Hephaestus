import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelAccessDialog } from "./AdminLlmModelAccessDialog";

const model: LlmModel = {
	id: 7,
	slug: "gpt-5",
	displayName: "GPT-5",
	upstreamModelId: "gpt-5",
	connectionId: 1,
	connectionDisplayName: "OpenAI production",
	enabled: true,
	supportsReasoning: true,
	visibility: "GRANTED",
	grantedWorkspaceIds: [10],
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

const meta = {
	component: AdminLlmModelAccessDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		model,
		workspaceOptions: [
			{ id: 10, displayName: "Teaching team", workspaceSlug: "teaching" },
			{ id: 11, displayName: "Research team", workspaceSlug: "research" },
		],
		isSubmitting: false,
		onSave: fn(),
	},
} satisfies Meta<typeof AdminLlmModelAccessDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const SelectedWorkspaces: Story = {};

export const WorkspaceListError: Story = {
	args: { isWorkspaceError: true, onRetryWorkspaces: fn() },
};
