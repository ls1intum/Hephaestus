import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, screen, userEvent, within } from "storybook/test";
import type { AgentBinding, AiSettingsView, AvailableLlmModel } from "@/api/types.gen";
import { expectControlOnScreen, expectPageReflows } from "@/test/reflow";
import { AgentBindingsPage } from "./AgentBindingsPage";
import { mockAvailableModels } from "./storyMockData";

const detectionBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	instanceModelId: 1,
	enabled: true,
	ready: true,
	timeoutSeconds: 600,
	maxConcurrentJobs: 3,
	allowInternet: false,
};

/** Workspace providers are covered by their own panel's stories, so they stay out of the way here. */
const settings: AiSettingsView = {
	practicesEnabled: true,
	mentorEnabled: true,
	workspaceConnectionsAllowed: false,
	cooldownMinutes: 30,
	deliverToMerged: false,
	runForAllUsers: true,
	skipDrafts: true,
};

const usage = {
	month: "2026-07",
	pricedTotalCostUsd: 0,
	byoTotalCostUsd: 0,
	unpricedEventCount: 0,
	instanceFundedPaused: false,
	byoPaused: false,
	instanceBudgetVerdict: "WITHIN",
	byoBudgetVerdict: "WITHIN",
	byDay: [],
	byJobType: [],
};

function handlers({
	bindings = [detectionBinding],
	models = mockAvailableModels,
}: {
	bindings?: AgentBinding[];
	models?: AvailableLlmModel[];
} = {}) {
	return [
		http.get("*/workspaces/acme/agent-bindings", () => HttpResponse.json(bindings)),
		http.get("*/workspaces/acme/ai-settings", () => HttpResponse.json(settings)),
		http.get("*/workspaces/acme/llm/available-models", () => HttpResponse.json(models)),
		http.get("*/workspaces/acme/llm-usage", () => HttpResponse.json(usage)),
		http.put("*/workspaces/acme/agent-bindings/*", () => HttpResponse.json(detectionBinding)),
	];
}

/**
 * The workspace's AI models page: one card per agent purpose, each binding a model and — behind an
 * "Advanced" disclosure — the run limits that binding runs under.
 */
const meta = {
	component: AgentBindingsPage,
	parameters: { layout: "fullscreen", msw: { handlers: handlers() } },
	tags: ["autodocs"],
	args: { workspaceSlug: "acme" },
} satisfies Meta<typeof AgentBindingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Practice detection is bound and ready; the mentor is still unbound. */
export const Default: Story = {};

/** No model has been granted or connected yet — the picker is inert and says why. */
export const NoModelsAvailable: Story = {
	parameters: { msw: { handlers: handlers({ bindings: [], models: [] }) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findAllByText(/No models are available yet/)).toBeTruthy();
	},
};

/** The load failed with a 403 — the alert says so and offers no Retry, which could not help. */
export const LoadForbidden: Story = {
	parameters: {
		// MSW picks the first matching handler, so this 403 shadows the successful one below it.
		msw: {
			handlers: [
				http.get("*/workspaces/acme/agent-bindings", () =>
					HttpResponse.json(
						// A faithful RFC 9457 ProblemDetail: the server puts `status` in the BODY, and the
						// generated client throws that body verbatim — so the body is where the alert reads
						// the status from when it decides a 403 cannot be retried away.
						{
							type: "about:blank",
							title: "Forbidden",
							status: 403,
							detail: "You are not an admin of this workspace.",
							instance: "/workspaces/acme/agent-bindings",
						},
						{ status: 403 },
					),
				),
				...handlers(),
			],
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Couldn't load AI models")).toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Retry" })).toBeNull();
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
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click((await canvas.findAllByRole("button", { name: /Advanced/ }))[0]);

		const timeout = await canvas.findByLabelText("Timeout (seconds)");
		await userEvent.clear(timeout);
		await userEvent.click(canvas.getAllByRole("button", { name: "Save" })[0]);

		await expect(await canvas.findByText("Enter a number of seconds.")).toBeInTheDocument();
		await expect(timeout).toHaveAttribute("aria-invalid", "true");
		// Field-level validation stays in the field: no toast is raised.
		await expect(screen.queryByRole("status")).toBeNull();
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
		// Wait for the queries to land, so the assertion measures the real page and not a spinner.
		await canvas.findByText("Practice detection");
		await expectPageReflows();
		await expectControlOnScreen(canvas.getAllByRole("button", { name: "Save" })[0]);
	},
};
