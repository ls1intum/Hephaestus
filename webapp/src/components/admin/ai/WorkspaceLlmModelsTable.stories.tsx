import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { WorkspaceLlmModel } from "@/api/types.gen";
import { WorkspaceLlmModelsTable } from "./WorkspaceLlmModelsTable";

const mockModels: WorkspaceLlmModel[] = [
	{
		id: 1,
		slug: "gpt-5-mini",
		displayName: "GPT-5 mini",
		upstreamModelId: "openai/gpt-5-mini",
		connectionId: 1,
		connectionDisplayName: "My OpenAI account",
		modality: "CHAT",
		enabled: true,
		supportsReasoning: true,
		pricingMode: "PRICED",
		per1mInputUsd: 0.25,
		currency: "USD",
		createdAt: new Date("2026-06-01T10:00:00Z"),
	},
	{
		id: 2,
		slug: "local-llama",
		displayName: "Local Llama",
		upstreamModelId: "local/llama-3-70b",
		connectionId: 1,
		connectionDisplayName: "My OpenAI account",
		modality: "CHAT",
		enabled: false,
		supportsReasoning: false,
		pricingMode: "FREE",
		priceNote: "self-hosted, no cost",
		currency: "USD",
		createdAt: new Date("2026-06-01T10:00:00Z"),
	},
];

const meta = {
	component: WorkspaceLlmModelsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		models: mockModels,
		mutatingId: null,
		onEdit: fn(),
		onDelete: fn(),
	},
} satisfies Meta<typeof WorkspaceLlmModelsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Empty: Story = {
	args: { models: [] },
};

export const DeleteConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete gpt-5 mini/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/stop working/i)).toBeInTheDocument();
	},
};
