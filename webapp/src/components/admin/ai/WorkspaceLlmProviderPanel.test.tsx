import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import type { WorkspaceLlmConnection, WorkspaceLlmModel } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { WorkspaceLlmProviderPanel } from "./WorkspaceLlmProviderPanel";

const connections: WorkspaceLlmConnection[] = [
	{
		id: 1,
		slug: "openai",
		displayName: "OpenAI production",
		authMode: "BEARER",
		apiProtocol: "openai-responses",
		baseUrl: "https://api.openai.com/v1",
		enabled: true,
		hasApiKey: true,
		apiKeyLast4: "1111",
		createdAt: new Date("2026-07-01T00:00:00Z"),
	},
	{
		id: 2,
		slug: "gpu",
		displayName: "Local GPU",
		authMode: "BEARER",
		apiProtocol: "openai-completions",
		baseUrl: "https://llm.example.test/v1",
		enabled: false,
		hasApiKey: false,
		createdAt: new Date("2026-07-01T00:00:00Z"),
	},
];

function model(id: number, connectionId: number, displayName: string): WorkspaceLlmModel {
	return {
		id,
		connectionId,
		connectionDisplayName:
			connections.find((connection) => connection.id === connectionId)?.displayName ?? "",
		slug: `model-${id}`,
		displayName,
		upstreamModelId: `upstream-${id}`,
		enabled: true,
		supportsReasoning: false,
		pricingMode: "UNPRICED",
		currency: "USD",
		createdAt: new Date("2026-07-01T00:00:00Z"),
	};
}

describe("WorkspaceLlmProviderPanel", () => {
	function renderPanel(workspaceConnectionsAllowed = true) {
		const queryClient = new QueryClient({
			defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
		});
		return render(
			<QueryClientProvider client={queryClient}>
				<WorkspaceLlmProviderPanel
					workspaceSlug="demo"
					workspaceConnectionsAllowed={workspaceConnectionsAllowed}
				/>
			</QueryClientProvider>,
		);
	}

	it("renders every workspace connection and groups each model under its owner", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "GPT shared endpoint"), model(20, 2, "GPU coder")]),
			),
		);
		renderPanel();

		expect(await screen.findByText("OpenAI production")).toBeTruthy();
		expect(screen.getByText("Local GPU")).toBeTruthy();
		expect(await screen.findByText("GPT shared endpoint")).toBeTruthy();
		expect(screen.getByText("GPU coder")).toBeTruthy();
	});

	it("confirms before irreversibly disconnecting a provider", async () => {
		let deleted = false;
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () => HttpResponse.json([])),
			http.delete("*/workspaces/demo/llm/connections/1", () => {
				deleted = true;
				return new HttpResponse(null, { status: 204 });
			}),
		);
		renderPanel();
		fireEvent.click(await screen.findByRole("button", { name: "Disconnect" }));
		expect(deleted).toBe(false);
		const dialog = screen.getByRole("alertdialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Disconnect provider" }));
		await waitFor(() => expect(deleted).toBe(true));
	});

	it("explains the instance policy and hides registration when BYO is disabled", async () => {
		server.use(http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([])));
		renderPanel(false);
		expect(await screen.findByText("New workspace providers and models are disabled")).toBeTruthy();
		expect(screen.queryByRole("button", { name: "Connect provider" })).toBeNull();
	});

	it("does not present a failed model request as an empty catalog", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json({ title: "Unavailable" }, { status: 503 }),
			),
		);
		renderPanel();

		expect(await screen.findByText("Could not load your provider models")).toBeTruthy();
		expect(screen.queryByText("No models yet")).toBeNull();
	});

	it("hides new providers and models while keeping existing entries manageable", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "Existing model")]),
			),
		);
		renderPanel(false);

		expect(await screen.findByText("Existing model")).toBeTruthy();
		expect(screen.queryByRole("button", { name: "Add provider" })).toBeNull();
		expect(screen.queryByRole("button", { name: "Add model" })).toBeNull();
		expect(screen.getByRole("button", { name: "Edit" })).toBeTruthy();
		expect(screen.getByRole("button", { name: "Edit Existing model" })).toBeTruthy();
		expect(screen.getByRole("button", { name: "Delete Existing model" })).toBeTruthy();
	});
});
