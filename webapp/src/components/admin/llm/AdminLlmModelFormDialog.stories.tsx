import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelFormDialog } from "./AdminLlmModelFormDialog";
import type { WorkspaceMultiSelectOption } from "./WorkspaceMultiSelect";

const mockModel: LlmModel = {
	id: 1,
	slug: "gpt-5-eu",
	displayName: "GPT-5 (Azure, EU)",
	upstreamModelId: "gpt-5",
	connectionId: 1,
	connectionDisplayName: "Azure EU",
	modality: "CHAT",
	enabled: true,
	supportsReasoning: true,
	visibility: "GRANTED",
	grantedWorkspaceIds: [1],
	currentPrice: {
		id: 1,
		pricingMode: "PRICED",
		per1mInputUsd: 3,
		per1mOutputUsd: 15,
		currency: "USD",
		effectiveFrom: new Date("2026-05-01T00:00:00Z"),
	},
	createdAt: new Date("2026-05-01T10:00:00Z"),
};

const mockWorkspaces: WorkspaceMultiSelectOption[] = [
	{ id: 1, displayName: "Obsphera", workspaceSlug: "obsphera" },
	{ id: 2, displayName: "Acme Corp", workspaceSlug: "acme" },
];

const meta = {
	component: AdminLlmModelFormDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		editing: null,
		workspaceOptions: mockWorkspaces,
		probedModelIds: ["gpt-5", "gpt-5-mini"],
		isSubmitting: false,
		onSave: fn(),
	},
} satisfies Meta<typeof AdminLlmModelFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AddModel: Story = {};

export const EditModel: Story = {
	args: { editing: mockModel },
};

/** Shared with only the selected workspaces — the multi-select appears once "Selected workspaces" is chosen. */
export const SharedWithSelectedWorkspaces: Story = {
	args: { editing: mockModel },
	play: async () => {
		await expect(await screen.findByText("Selected workspaces")).toBeInTheDocument();
	},
};

export const ValidationError: Story = {
	play: async () => {
		await userEvent.click(await screen.findByRole("button", { name: /add model/i }));
		await expect(await screen.findByText(/display name is required/i)).toBeInTheDocument();
		await expect(await screen.findByText(/upstream model id is required/i)).toBeInTheDocument();
	},
};
