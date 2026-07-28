import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { WorkspaceLlmModel } from "@/api/types.gen";
import { expectControlOnScreen, expectDialogFitsViewport, openDialogPopup } from "@/test/reflow";
import { WorkspaceLlmModelsTable } from "./WorkspaceLlmModelsTable";

async function openDeleteConfirm(canvas: ReturnType<typeof within>, name: RegExp) {
	await userEvent.click(canvas.getByRole("button", { name }));
	return await screen.findByRole("alertdialog");
}

const mockModels: WorkspaceLlmModel[] = [
	{
		id: 1,
		slug: "gpt-5-mini",
		displayName: "GPT-5 mini",
		upstreamModelId: "openai/gpt-5-mini",
		connectionId: 1,
		connectionDisplayName: "My OpenAI account",
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
		enabled: false,
		supportsReasoning: false,
		pricingMode: "NO_CHARGE",
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
		mutatingIds: new Set<number>(),
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
	play: async ({ canvas }) => {
		const dialog = await openDeleteConfirm(canvas, /delete gpt-5 mini/i);
		await expect(within(dialog).getByText(/stop working/i)).toBeInTheDocument();
	},
};

/**
 * WCAG 2.2 SC 1.4.10 at 320 px, where `AlertDialogContent`'s `max-w-xs` is exactly the viewport — so
 * only its `calc(100% - 2rem)` clamp keeps a gutter, and this asserts the gutter rather than the max.
 */
export const DeleteConfirmMobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375] },
	},
	play: async ({ canvas }) => {
		await openDeleteConfirm(canvas, /delete gpt-5 mini/i);

		await expectDialogFitsViewport();
		// Layout width, so the `zoom-in-95` enter animation cannot flatter the measurement.
		await expect(openDialogPopup().offsetWidth).toBeLessThanOrEqual(window.innerWidth - 32);
		await expectControlOnScreen(screen.getByRole("button", { name: /^cancel$/i }));
		await expectControlOnScreen(screen.getByRole("button", { name: /^delete$/i }));
	},
};

/**
 * `AlertDialogContent` is its own scroller, so only its height bound keeps a `position: fixed` popup
 * from hanging off the screen with Delete out of reach. The absurd name exercises that bound as
 * behaviour — stays on screen, scrolls, Delete reachable — rather than as emitted CSS.
 */
export const DeleteConfirmLongNameReflow: Story = {
	args: {
		models: [{ ...mockModels[0], displayName: `GPT-5 ${"extremely-long-model-name ".repeat(40)}` }],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvas }) => {
		const popup = await openDeleteConfirm(canvas, /^delete gpt-5 /i);

		await expectDialogFitsViewport();
		// Bounded, not merely fitting: this title overflows, and the overflow scrolls rather than clips.
		await expect(popup.scrollHeight).toBeGreaterThan(popup.clientHeight);
		popup.scrollTop = popup.scrollHeight;
		await expect(popup.scrollTop).toBeGreaterThan(0);

		await expectControlOnScreen(screen.getByRole("button", { name: /^delete$/i }));
	},
};
