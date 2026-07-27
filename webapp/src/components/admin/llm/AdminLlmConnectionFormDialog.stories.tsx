import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { LlmConnection } from "@/api/types.gen";
import { expectDialogFitsViewport } from "@/test/reflow";
import { AdminLlmConnectionFormDialog } from "./AdminLlmConnectionFormDialog";

const mockConnection: LlmConnection = {
	id: 1,
	slug: "openai-production",
	displayName: "OpenAI production",
	authMode: "BEARER",
	apiProtocol: "openai-responses",
	baseUrl: "https://openai-production.example.com/openai",
	enabled: true,
	hasApiKey: true,
	apiKeyLast4: "ab12",
	createdAt: new Date("2026-05-01T10:00:00Z"),
};

const meta = {
	component: AdminLlmConnectionFormDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		editing: null,
		isSubmitting: false,
		onCreate: fn(),
		onUpdate: fn(),
		onProbe: fn(),
		isProbing: false,
		onProbed: fn(),
	},
} satisfies Meta<typeof AdminLlmConnectionFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AddConnection: Story = {};

export const EditConnection: Story = {
	args: { editing: mockConnection },
};

export const Probing: Story = {
	args: { isProbing: true },
};

/** "Test & fetch models" never gates saving — a 4xx/unreachable probe is an amber note, not a blocker. */
export const DiscoveryUnsupported: Story = {
	args: {
		onProbe: fn((_request, callbacks) =>
			callbacks.onSuccess({ reachable: false, models: [], message: "Connection timed out" }),
		),
	},
	play: async () => {
		await userEvent.type(await screen.findByLabelText("Base URL"), "https://example.com");
		await userEvent.click(screen.getByRole("button", { name: /test & fetch models/i }));
		await expect(await screen.findByText(/discovery unsupported/i)).toBeInTheDocument();
		await expect(screen.getByRole("button", { name: /save inactive connection/i })).toBeEnabled();
	},
};

export const ValidationError: Story = {
	play: async () => {
		await userEvent.click(await screen.findByRole("button", { name: /save inactive connection/i }));
		await expect(await screen.findByText(/display name is required/i)).toBeInTheDocument();
	},
};

/**
 * At the WCAG 2.2 SC 1.4.10 reflow width (320 px).
 *
 * Preset, endpoint, auth mode, credential and the probe result come to ~900 px. Without
 * `DialogBody` the popup is unbounded and `position: fixed`, so it hangs off the top and the bottom
 * of every phone at once and neither the title nor "Save inactive connection" can be scrolled back
 * into view. `AdminLlmModelFormDialog` — taller still, past 1000 px — carries the full proof that
 * only the body scrolls; what matters here is that this one stays inside the shared bound.
 */
export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async () => {
		// The dialog is a portal, so it is on `document`, not in the story canvas.
		await screen.findByRole("button", { name: /save inactive connection/i });
		await expectDialogFitsViewport();
	},
};
