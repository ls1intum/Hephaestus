import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { WorkspaceLlmModel } from "@/api/types.gen";
import { WorkspaceLlmModelFormDialog } from "./WorkspaceLlmModelFormDialog";

const mockModel: WorkspaceLlmModel = {
	id: 1,
	slug: "gpt-5-mini",
	displayName: "GPT-5 mini",
	upstreamModelId: "openai/gpt-5-mini",
	connectionId: 1,
	connectionDisplayName: "My OpenAI account",
	modality: "CHAT",
	enabled: true,
	supportsReasoning: true,
	contextWindow: 128_000,
	maxOutputTokens: 16_000,
	pricingMode: "PRICED",
	per1mInputUsd: 0.25,
	per1mOutputUsd: 2,
	currency: "USD",
	createdAt: new Date("2026-06-01T10:00:00Z"),
};

const meta = {
	component: WorkspaceLlmModelFormDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		editing: null,
		isSubmitting: false,
		onCreate: fn(),
		onUpdate: fn(),
	},
} satisfies Meta<typeof WorkspaceLlmModelFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AddModel: Story = {};

export const EditModel: Story = {
	args: { editing: mockModel },
};

export const FreeModel: Story = {
	args: { editing: { ...mockModel, pricingMode: "FREE", priceNote: "self-hosted, no cost" } },
};

/** Submitting without a display name or upstream id surfaces validation. */
export const ValidationError: Story = {
	play: async () => {
		// Dialog renders in a portal → query the document. `toBeInTheDocument` (not `toBeVisible`):
		// the popup's enter transition can still be animating opacity when this assertion runs.
		await userEvent.click(await screen.findByRole("button", { name: /add model/i }));
		await expect(await screen.findByText(/display name is required/i)).toBeInTheDocument();
		await expect(await screen.findByText(/upstream model id is required/i)).toBeInTheDocument();
	},
};
