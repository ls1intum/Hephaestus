import type { Meta, StoryObj } from "@storybook/react";
import { fn, screen, userEvent } from "storybook/test";
import type { LlmModel } from "@/api/types.gen";
import {
	expectControlOnScreen,
	expectDialogBodyScrolls,
	expectDialogFitsViewport,
} from "@/test/reflow";
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

/**
 * The workspace directory failed to load. A faithful RFC 9457 ProblemDetail: the server puts `status`
 * in the body, which is what the generated client throws, and `QueryErrorAlert` reads both — a 503 is
 * retryable, so it offers Retry and repeats the server's own reason.
 */
export const WorkspaceListError: Story = {
	args: {
		workspacesError: {
			type: "about:blank",
			title: "Service Unavailable",
			status: 503,
			detail: "The workspace directory is temporarily unavailable.",
		},
		onRetryWorkspaces: fn(),
	},
};

/**
 * A real instance's worth of workspaces at the WCAG 2.2 SC 1.4.10 reflow width (320 px). Two option
 * cards, the picker and the "access is reduced" alert overflow a phone; only the body scrolls, so
 * the title stays pinned and "Save access" stays reachable.
 */
export const MobileReflow: Story = {
	args: {
		// PUBLIC today, so opening on "Selected workspaces" also raises the consequence alert.
		model: { ...model, visibility: "PUBLIC", grantedWorkspaceIds: [] },
		workspaceOptions: Array.from({ length: 14 }, (_, index) => ({
			id: index + 1,
			displayName: `Workspace ${index + 1}`,
			workspaceSlug: `workspace-${index + 1}`,
		})),
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async () => {
		// Narrowing access is what makes this dialog tall: it adds the picker and the consequence
		// alert. Reviewing the "All workspaces" state alone would review the short half of it.
		await userEvent.click(await screen.findByRole("radio", { name: /^Selected workspaces/i }));
		const submit = await screen.findByRole("button", { name: /save access/i });
		await expectDialogFitsViewport();
		await expectDialogBodyScrolls();
		await expectControlOnScreen(submit);
		await expectControlOnScreen(screen.getByRole("button", { name: /^close$/i }));
	},
};
