import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AgentBinding } from "@/api/types.gen";
import { expectControlOnScreen, expectPageReflows } from "@/test/reflow";
import { AgentBindingsPage } from "./AgentBindingsPage";
import { mockAvailableModels } from "./story-mock-data";

const detectionBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	instanceModelId: 1,
	enabled: true,
	ready: true,
	timeoutSeconds: 600,
	maxConcurrentJobs: 3,
	allowInternet: false,
};

/**
 * The workspace's AI models page: one card per agent purpose, each binding a model and — behind an
 * "Advanced" disclosure — the run limits that binding runs under.
 *
 * The route above it owns every query and mutation, so each story is just the situation stated as
 * props — no request mocking to arrive at a screen.
 */
const meta = {
	component: AgentBindingsPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "acme",
		bindings: [detectionBinding],
		availableModels: mockAvailableModels,
		// Both purposes are turned on for the workspace, so each card offers a real binding form.
		practicesEnabled: true,
		mentorEnabled: true,
		// The workspace's own providers are a separate panel that fetches for itself; keeping it out
		// of these stories keeps them about the binding cards.
		ownProviderAllowed: false,
		isLoading: false,
		isError: false,
		loadError: null,
		pendingPurposes: new Set(),
		onRetry: fn(),
		onSave: fn(),
		onTurnOff: fn(),
	},
} satisfies Meta<typeof AgentBindingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Practice detection is bound and ready; the mentor is still unbound. */
export const Default: Story = {};

/** The four page queries are still in flight. */
export const Loading: Story = {
	args: { isLoading: true },
};

/** No model has been granted or connected yet — the picker is inert and says why. */
export const NoModelsAvailable: Story = {
	args: { bindings: [], availableModels: [] },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findAllByText(/No models are available yet/)).toBeTruthy();
	},
};

/** The load failed with a 403 — the alert says so and offers no Retry, which could not help. */
export const LoadForbidden: Story = {
	args: {
		isError: true,
		// A faithful RFC 9457 ProblemDetail: the server puts `status` in the BODY, and the generated
		// client throws that body verbatim — so the body is where the alert reads the status from when
		// it decides a 403 cannot be retried away.
		loadError: {
			type: "about:blank",
			title: "Forbidden",
			status: 403,
			detail: "You are not an admin of this workspace.",
			instance: "/workspaces/acme/agents",
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Couldn't load AI models")).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Retry" })).toBeNull();
	},
};

/** A purpose the workspace has not been given at all — the card says who can turn it on. */
export const PurposeDisabledForWorkspace: Story = {
	args: { mentorEnabled: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			await canvas.findByText("Turned off for this workspace. Only your host can turn it on."),
		).toBeInTheDocument();
	},
};

/** A save is in flight for practice detection — only that card's controls are frozen. */
export const SaveInFlight: Story = {
	args: { pendingPurposes: new Set(["PRACTICE_DETECTION" as const]) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const saveButtons = await canvas.findAllByRole("button", { name: "Save" });
		// The mentor card, which has nothing in flight, stays usable.
		await expect(saveButtons[0]).toBeDisabled();
		await expect(saveButtons[1]).toBeEnabled();
	},
};

/** The advanced settings are a disclosure: the trigger reports its own state to assistive tech. */
export const AdvancedDisclosure: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = (await canvas.findAllByRole("button", { name: /Advanced/ }))[0];

		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(trigger);
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
		await expect(await canvas.findByLabelText("Timeout (seconds)")).toBeInTheDocument();
	},
};

/** Clearing a run limit blocks the save and explains itself in place — never as a toast. */
export const InvalidRunLimit: Story = {
	play: async ({ canvasElement, args }) => {
		const canvas = within(canvasElement);
		await userEvent.click((await canvas.findAllByRole("button", { name: /Advanced/ }))[0]);

		const timeout = await canvas.findByLabelText("Timeout (seconds)");
		await userEvent.clear(timeout);
		await userEvent.click(canvas.getAllByRole("button", { name: "Save" })[0]);

		await expect(await canvas.findByText("Enter a number of seconds.")).toBeInTheDocument();
		await expect(timeout).toHaveAttribute("aria-invalid", "true");
		// Field-level validation stays in the field: no toast is raised, and nothing is sent.
		await expect(screen.queryByRole("status")).toBeNull();
		await expect(args.onSave).not.toHaveBeenCalled();
	},
};

/**
 * The AI models page at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px).
 *
 * Nothing here is tabular, so the whole page must reflow to one column with no horizontal scrolling
 * at all — the card headers wrap their "Ready" badge and the descriptions wrap rather than widening
 * the row.
 */
export const MobileReflow: Story = {
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768] },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("Practice detection");
		await expectPageReflows();
		await expectControlOnScreen(canvas.getAllByRole("button", { name: "Save" })[0]);
	},
};
