import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import type { AgentConfig, AiSettingsView, AvailableLlmModel } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { AgentRuntimesPage } from "./AgentRuntimesPage";

const availableModel: AvailableLlmModel = {
	id: 20,
	scope: "SHARED",
	displayName: "GPT Test",
	connectionDisplayName: "Shared OpenAI",
	supportsReasoning: false,
	pricingMode: "NO_CHARGE",
};

const config: AgentConfig = {
	id: 10,
	name: "Mentor",
	enabled: true,
	allowInternet: false,
	maxConcurrentJobs: 1,
	timeoutSeconds: 600,
	instanceModelId: availableModel.id,
	createdAt: new Date("2026-07-01T00:00:00Z"),
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

function renderPage(
	configs: AgentConfig[] = [config],
	models: AvailableLlmModel[] = [availableModel],
	aiSettings: AiSettingsView = settings,
) {
	server.use(
		http.get("*/workspaces/demo/agent-configs", () => HttpResponse.json(configs)),
		http.get("*/workspaces/demo/ai-settings", () => HttpResponse.json(aiSettings)),
		http.get("*/workspaces/demo/llm/available-models", () => HttpResponse.json(models)),
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
	);
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<QueryClientProvider client={queryClient}>
			<AgentRuntimesPage workspaceSlug="demo" />
		</QueryClientProvider>,
	);
}

describe("AgentRuntimesPage mentor binding", () => {
	it("shows an explicit unconfigured state instead of implying automatic fallback", async () => {
		renderPage();

		expect(await screen.findByText("Not configured")).toBeTruthy();
		expect(screen.queryByText("Automatic (first available model)")).toBeNull();
	});

	it("keeps an invalid existing mentor binding understandable", async () => {
		const revoked = { ...config, id: 11, name: "Revoked mentor", instanceModelId: 99 };
		renderPage([config, revoked], [availableModel], { ...settings, mentorConfigId: revoked.id });

		expect(await screen.findByText("Revoked mentor (unavailable)")).toBeTruthy();
		expect(
			screen.getByText("The selected configuration cannot run. Choose an available configuration."),
		).toBeTruthy();
	});
});
