import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { WorkspaceLlmConnection } from "@/api/types.gen";
import { expectDialogFitsViewport } from "@/test/reflow";
import { WorkspaceLlmConnectionFormDialog } from "./WorkspaceLlmConnectionFormDialog";

const mockConnection: WorkspaceLlmConnection = {
	id: 1,
	slug: "my-openai",
	displayName: "My OpenAI account",
	authMode: "BEARER",
	apiProtocol: "openai-completions",
	baseUrl: "https://api.openai.com/v1",
	enabled: true,
	hasApiKey: true,
	apiKeyLast4: "ab12",
	createdAt: new Date("2026-06-01T10:00:00Z"),
};

const meta = {
	component: WorkspaceLlmConnectionFormDialog,
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
} satisfies Meta<typeof WorkspaceLlmConnectionFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Connect your own AI provider — the glossary's verbatim copy moment appears in the description. */
export const Connect: Story = {};

/** Editing an already-connected provider; the key field shows the masked placeholder. */
export const Edit: Story = {
	args: { editing: mockConnection },
};

export const Submitting: Story = {
	args: { isSubmitting: true },
};

/**
 * Reviewed at the WCAG 2.2 SC 1.4.10 reflow width (320 px). The create form is the taller of the two
 * variants — preset, protocol checkbox, base URL, auth mode, key, and the active switch — and a
 * `position: fixed` popup that outgrows the viewport hangs off both ends at once with no way to
 * scroll it back. `AdminLlmModelFormDialog`, the tallest of these dialogs, carries the full proof
 * that `DialogBody` scrolls the middle rather than the whole popup.
 */
export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async () => {
		await screen.findByRole("button", { name: /^connect provider$/i });
		await expectDialogFitsViewport();
	},
};

/** Submitting without a display name surfaces validation. */
export const ValidationError: Story = {
	play: async () => {
		// Dialog renders in a portal → query the document. `toBeInTheDocument` (not `toBeVisible`):
		// the popup's enter transition can still be animating opacity when this assertion runs.
		await userEvent.click(await screen.findByRole("button", { name: /^connect provider$/i }));
		await expect(await screen.findByText(/display name is required/i)).toBeInTheDocument();
	},
};
