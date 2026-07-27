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
 * `AlertDialogContent`'s `max-w-xs` is 20rem — exactly a 320 px viewport — so a width bound alone
 * would let the popup run edge to edge here, and past the edge as soon as the root font size grew.
 * A `calc(100% - 2rem)` clamp is what keeps a 1rem gutter at any width.
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
 * The same confirmation with a display name long enough to outgrow the screen, still at 320 px.
 *
 * `AlertDialogContent` has no `DialogBody` equivalent — the whole popup is the scroller — so its
 * height bound is the only thing standing between tall content and a `position: fixed` popup that
 * hangs off both edges with its buttons out of reach. No model name in the product is anywhere near
 * this long; the fixture is deliberately absurd so the bound is exercised as behaviour (does the
 * popup stay on screen, does it scroll, is Delete still reachable) rather than read back off the
 * emitted CSS, which would pass just as well over a popup nobody could use.
 */
export const DeleteConfirmLongNameReflow: Story = {
	args: {
		models: [{ ...mockModels[0], displayName: `GPT-5 ${"extremely-long-model-name ".repeat(40)}` }],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /^delete gpt-5 /i }));
		const popup = await screen.findByRole("alertdialog");

		// It stays inside the viewport…
		await expectDialogFitsViewport();
		// …because it is bounded, not because the content happened to fit: this title does not.
		await expect(popup.scrollHeight).toBeGreaterThan(popup.clientHeight);
		// …and the overflow scrolls rather than clips, so the footer can be reached at all.
		popup.scrollTop = popup.scrollHeight;
		await expect(popup.scrollTop).toBeGreaterThan(0);

		await expectControlOnScreen(screen.getByRole("button", { name: /^delete$/i }));
	},
};
