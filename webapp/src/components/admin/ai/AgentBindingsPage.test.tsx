import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import type { AgentBinding, AvailableLlmModel } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { AgentBindingsPage } from "./AgentBindingsPage";

const model: AvailableLlmModel = {
	id: 20,
	scope: "SHARED",
	displayName: "GPT Test",
	connectionDisplayName: "Shared OpenAI",
	supportsReasoning: false,
	pricingMode: "NO_CHARGE",
};

const detectionBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	instanceModelId: 20,
	enabled: true,
	ready: false,
	timeoutSeconds: 600,
	maxConcurrentJobs: 1,
	allowInternet: false,
};

/** Whether a purpose may run at all is a property of the workspace, not of the AI config. */
const workspace = { practicesEnabled: true, mentorEnabled: true };

/** Registering providers of your own is an instance-level permission, asked for separately. */
const llmSettings = { ownProviderAllowed: false };

function renderPage(bindings: AgentBinding[] = [detectionBinding], captured?: { body?: unknown }) {
	server.use(
		http.get("*/workspaces/demo/agents", () => HttpResponse.json(bindings)),
		http.get("*/workspaces/demo/llm/settings", () => HttpResponse.json(llmSettings)),
		http.get("*/workspaces/demo/llm/available-models", () => HttpResponse.json([model])),
		http.get("*/workspaces/demo", () => HttpResponse.json(workspace)),
		http.get("*/workspaces/demo/llm/usage", () =>
			HttpResponse.json({
				month: "2026-07",
				instanceTotalCostUsd: 0,
				ownProviderTotalCostUsd: 0,
				unpricedEventCount: 0,
				instancePaused: false,
				ownProviderPaused: false,
				instanceBudgetVerdict: "WITHIN",
				ownProviderBudgetVerdict: "WITHIN",
				byDay: [],
				byJobType: [],
			}),
		),
		http.put("*/workspaces/demo/agents/PRACTICE_DETECTION", async ({ request }) => {
			if (captured) captured.body = await request.json();
			return HttpResponse.json({ ...detectionBinding, ready: true });
		}),
	);
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<QueryClientProvider client={queryClient}>
			<AgentBindingsPage workspaceSlug="demo" />
		</QueryClientProvider>,
	);
}

describe("AgentBindingsPage", () => {
	it("renders an assignment card for each purpose", async () => {
		renderPage();
		expect(await screen.findByText("Practice detection")).toBeTruthy();
		expect(screen.getByText("Mentor")).toBeTruthy();
	});

	it("shows a Not ready badge when the bound model cannot run", async () => {
		renderPage();
		expect(await screen.findByText("Not ready")).toBeTruthy();
	});

	it("saves the bound model id when the admin clicks Save", async () => {
		const captured: { body?: unknown } = {};
		renderPage([detectionBinding], captured);

		const saveButtons = await screen.findAllByRole("button", { name: "Save" });
		fireEvent.click(saveButtons[0]);

		await waitFor(() => expect(captured.body).toBeDefined());
		expect(captured.body).toMatchObject({ instanceModelId: 20, enabled: true });
	});

	it("exposes the advanced settings as a disclosure", async () => {
		renderPage();

		const trigger = (await screen.findAllByRole("button", { name: /Advanced/ }))[0];
		expect(trigger.getAttribute("aria-expanded")).toBe("false");
		expect(screen.queryByLabelText("Timeout (seconds)")).toBeNull();

		fireEvent.click(trigger);

		expect(trigger.getAttribute("aria-expanded")).toBe("true");
		// The panel the trigger claims to control is the one that actually holds the fields.
		const panelId = trigger.getAttribute("aria-controls");
		expect(panelId).toBeTruthy();
		const timeout = screen.getByLabelText("Timeout (seconds)");
		expect(panelId && document.getElementById(panelId)?.contains(timeout)).toBe(true);
	});

	it("refuses to save a cleared timeout instead of sending a zero", async () => {
		const captured: { body?: unknown } = {};
		renderPage([detectionBinding], captured);

		fireEvent.click((await screen.findAllByRole("button", { name: /Advanced/ }))[0]);
		fireEvent.change(screen.getByLabelText("Timeout (seconds)"), { target: { value: "" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(await screen.findByText("Enter a number of seconds.")).toBeTruthy();
		expect(screen.getByLabelText("Timeout (seconds)").getAttribute("aria-invalid")).toBe("true");
		// Nothing was sent — the old code PUT `timeoutSeconds: 0` here.
		await waitFor(() => expect(captured.body).toBeUndefined());
	});

	it("rejects a timeout below the floor and only saves once it is corrected", async () => {
		const captured: { body?: unknown } = {};
		renderPage([detectionBinding], captured);

		fireEvent.click((await screen.findAllByRole("button", { name: /Advanced/ }))[0]);
		const timeout = screen.getByLabelText("Timeout (seconds)");
		fireEvent.change(timeout, { target: { value: "5" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(await screen.findByText("Enter a whole number of seconds, 30 or more.")).toBeTruthy();
		expect(captured.body).toBeUndefined();

		fireEvent.change(timeout, { target: { value: "45" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		await waitFor(() => expect(captured.body).toBeDefined());
		expect(captured.body).toMatchObject({ timeoutSeconds: 45 });
	});

	it("reopens the advanced disclosure when the field that blocked the save is inside it", async () => {
		renderPage();

		fireEvent.click((await screen.findAllByRole("button", { name: /Advanced/ }))[0]);
		fireEvent.change(screen.getByLabelText("Max concurrent runs"), { target: { value: "0" } });
		// Collapse it again, so the invalid field is out of sight when Save is pressed.
		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		expect(screen.queryByLabelText("Max concurrent runs")).toBeNull();

		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(await screen.findByText("Enter a whole number of runs, 1 or more.")).toBeTruthy();
	});
});
