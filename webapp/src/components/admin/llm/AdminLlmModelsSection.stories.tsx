import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelsSection } from "./AdminLlmModelsSection";

const mockModels: LlmModel[] = [
	{
		id: 1,
		slug: "gpt-5-eu",
		displayName: "GPT-5 (Azure, EU)",
		upstreamModelId: "gpt-5",
		connectionId: 1,
		connectionDisplayName: "Azure EU",
		modality: "CHAT",
		enabled: true,
		supportsReasoning: true,
		visibility: "PUBLIC",
		grantedWorkspaceIds: [],
		currentPrice: {
			id: 1,
			pricingMode: "PRICED",
			per1mInputUsd: 3,
			per1mOutputUsd: 15,
			currency: "USD",
			effectiveFrom: new Date("2026-05-01T00:00:00Z"),
		},
		createdAt: new Date("2026-05-01T10:00:00Z"),
	},
	{
		id: 2,
		slug: "local-llama",
		displayName: "Local Llama (self-hosted)",
		upstreamModelId: "meta/llama-3-70b",
		connectionId: 1,
		connectionDisplayName: "Azure EU",
		modality: "CHAT",
		enabled: true,
		supportsReasoning: false,
		visibility: "GRANTED",
		grantedWorkspaceIds: [10, 11],
		currentPrice: {
			id: 2,
			pricingMode: "FREE",
			note: "self-hosted, no cost",
			currency: "USD",
			effectiveFrom: new Date("2026-05-01T00:00:00Z"),
		},
		createdAt: new Date("2026-05-01T10:00:00Z"),
	},
	{
		id: 3,
		slug: "unpriced-model",
		displayName: "New model (not priced yet)",
		upstreamModelId: "vendor/new-model",
		connectionId: 1,
		connectionDisplayName: "Azure EU",
		modality: "CHAT",
		enabled: false,
		supportsReasoning: false,
		visibility: "PUBLIC",
		grantedWorkspaceIds: [],
		createdAt: new Date("2026-06-01T10:00:00Z"),
	},
];

const meta = {
	component: AdminLlmModelsSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		connectionDisplayName: "Azure EU",
		models: mockModels,
		mutatingId: null,
		onAdd: fn(),
		onEdit: fn(),
		onDelete: fn(),
	},
} satisfies Meta<typeof AdminLlmModelsSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Mixes priced / free / no-price-set — the instance-admin price framing never says "Unpriced". */
export const Default: Story = {};

export const Empty: Story = {
	args: { models: [] },
};

export const DeleteConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete gpt-5 \(azure, eu\)/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/can't be deleted/i)).toBeInTheDocument();
	},
};
