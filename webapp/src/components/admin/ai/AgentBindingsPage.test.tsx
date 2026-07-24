import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import type { AgentBinding, AiSettingsView, AvailableLlmModel } from "@/api/types.gen";
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

const settings: AiSettingsView = {
	runForAllUsers: false,
	skipDrafts: true,
	deliverToMerged: false,
	cooldownMinutes: 15,
	practicesEnabled: true,
	mentorEnabled: true,
	workspaceConnectionsAllowed: false,
};

function renderPage(bindings: AgentBinding[] = [detectionBinding], captured?: { body?: unknown }) {
	server.use(
		http.get("*/workspaces/demo/agent-bindings", () => HttpResponse.json(bindings)),
		http.get("*/workspaces/demo/ai-settings", () => HttpResponse.json(settings)),
		http.get("*/workspaces/demo/llm/available-models", () => HttpResponse.json([model])),
		http.get("*/workspaces/demo/llm-usage", () =>
			HttpResponse.json({
				month: "2026-07",
				pricedTotalCostUsd: 0,
				byoTotalCostUsd: 0,
				unpricedEventCount: 0,
				usagePaused: false,
				verdict: "WITHIN",
				byDay: [],
				byJobType: [],
			}),
		),
		http.put("*/workspaces/demo/agent-bindings/PRACTICE_DETECTION", async ({ request }) => {
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
});
