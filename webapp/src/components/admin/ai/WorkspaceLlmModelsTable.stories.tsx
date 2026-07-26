import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { WorkspaceLlmModel } from "@/api/types.gen";
import { expectControlOnScreen, expectDialogFitsViewport, openDialogPopup } from "@/test/reflow";
import { WorkspaceLlmModelsTable } from "./WorkspaceLlmModelsTable";

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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete gpt-5 mini/i }));
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/stop working/i)).toBeInTheDocument();
	},
};

/**
 * The delete confirmation at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * `AlertDialogContent`'s `max-w-xs` is 20rem — exactly a 320 px viewport — and it had no
 * `calc(100% - 2rem)` clamp of its own, so the popup ran edge to edge here and past the edge as
 * soon as the root font size was bumped. It now keeps a 1rem gutter at any width.
 */
export const DeleteConfirmMobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete gpt-5 mini/i }));
		await screen.findByRole("alertdialog");

		await expectDialogFitsViewport();
		// A real 1rem gutter on each side, not merely "no wider than the screen". Layout width, so the
		// `zoom-in-95` enter animation cannot flatter the measurement.
		await expect(openDialogPopup().offsetWidth).toBeLessThanOrEqual(window.innerWidth - 32);
		await expectControlOnScreen(screen.getByRole("button", { name: /^cancel$/i }));
		await expectControlOnScreen(screen.getByRole("button", { name: /^delete$/i }));
	},
};

/**
 * The same confirmation with a long, wrapping display name, still at 320 px.
 *
 * `AlertDialogContent` has no `DialogBody` equivalent — the whole popup is the scroller — so its
 * `max-h-[calc(100svh-2rem)]` is the only thing standing between tall content and a `position:
 * fixed` popup that hangs off both edges with its buttons out of reach. No confirm in the product
 * is tall enough to overflow a 568 px viewport, which is exactly why nothing measured the bound; so
 * this pins the bound itself rather than waiting for content that would trip it.
 */
export const DeleteConfirmLongNameReflow: Story = {
	args: {
		models: [{ ...mockModels[0], displayName: `GPT-5 ${"extremely-long-model-name ".repeat(4)}` }],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /^delete gpt-5 /i }));
		const popup = await screen.findByRole("alertdialog");

		await expectDialogFitsViewport();

		// A height bound at most the viewport less its 1rem gutters, and an overflow that scrolls
		// rather than clips. Drop either and a confirm taller than the screen becomes unreachable.
		const style = getComputedStyle(popup);
		await expect(style.maxHeight).not.toBe("none");
		await expect(Number.parseFloat(style.maxHeight)).toBeLessThanOrEqual(window.innerHeight - 32);
		await expect(style.overflowY).toBe("auto");

		await expectControlOnScreen(screen.getByRole("button", { name: /^delete$/i }));
	},
};
