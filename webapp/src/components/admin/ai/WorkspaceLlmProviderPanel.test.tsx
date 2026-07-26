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
	function renderPanel(ownProviderAllowed = true) {
		const queryClient = new QueryClient({
			defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
		});
		return render(
			<QueryClientProvider client={queryClient}>
				<WorkspaceLlmProviderPanel workspaceSlug="demo" ownProviderAllowed={ownProviderAllowed} />
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

	it("keeps each provider's probe pending independently when two run at once", async () => {
		// Connection 2 answers immediately; connection 1 is left hanging. A single "which row is
		// probing" flag would be cleared by 2 settling and would put row 1 back to idle mid-flight.
		let releaseSlowProbe: (() => void) | undefined;
		const slowProbe = new Promise<void>((resolve) => {
			releaseSlowProbe = resolve;
		});
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/workspaces/demo/llm/models", () => HttpResponse.json([])),
			http.post("*/workspaces/demo/llm/connections/1/probe", async () => {
				await slowProbe;
				return HttpResponse.json({ reachable: true, modelCount: 3 });
			}),
			http.post("*/workspaces/demo/llm/connections/2/probe", () =>
				HttpResponse.json({ reachable: true, modelCount: 1 }),
			),
		);
		renderPanel();

		const cards = await screen.findAllByText(/OpenAI production|Local GPU/);
		const openAiCard = cards[0].closest("[data-slot='card']") as HTMLElement;
		const gpuCard = cards[1].closest("[data-slot='card']") as HTMLElement;

		fireEvent.click(within(openAiCard).getByRole("button", { name: "Test connection" }));
		fireEvent.click(within(gpuCard).getByRole("button", { name: "Test connection" }));

		// The fast one settles first.
		expect(await within(gpuCard).findByText(/1 model available/)).toBeTruthy();

		// The slow one must still read as in flight, and must still refuse a second click.
		const slowButton = within(openAiCard).getByRole("button", { name: "Testing…" });
		expect((slowButton as HTMLButtonElement).disabled).toBe(true);

		releaseSlowProbe?.();
		expect(await within(openAiCard).findByText(/3 models available/)).toBeTruthy();
	});

	it("keeps each model's delete pending independently when two run at once", async () => {
		// Model 10's DELETE hangs; model 20's answers at once. A single "which model is mutating" id
		// is cleared by 20 settling, which re-enables row 10's Delete while its request is still in
		// flight — a second DELETE and a "Could not delete the model" toast for a model that was in
		// fact deleted.
		let releaseSlowDelete: (() => void) | undefined;
		const slowDelete = new Promise<void>((resolve) => {
			releaseSlowDelete = resolve;
		});
		let slowDeleteCalls = 0;
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "Slow model"), model(20, 1, "Fast model")]),
			),
			http.delete("*/workspaces/demo/llm/models/10", async () => {
				slowDeleteCalls += 1;
				await slowDelete;
				return new HttpResponse(null, { status: 204 });
			}),
			http.delete("*/workspaces/demo/llm/models/20", () => new HttpResponse(null, { status: 204 })),
		);
		renderPanel();

		const confirmDelete = async (name: string) => {
			fireEvent.click(await screen.findByRole("button", { name: `Delete ${name}` }));
			const dialog = await screen.findByRole("alertdialog");
			fireEvent.click(within(dialog).getByRole("button", { name: "Delete" }));
		};

		await confirmDelete("Slow model");
		await waitFor(() => expect(slowDeleteCalls).toBe(1));
		await confirmDelete("Fast model");
		// The fast one settles and the list is refetched.
		await waitFor(() => expect(screen.queryByText("Fast model")).not.toBeNull());

		const slowRowDelete = screen.getByRole("button", { name: "Delete Slow model" });
		expect((slowRowDelete as HTMLButtonElement).disabled).toBe(true);

		releaseSlowDelete?.();
		await waitFor(() => expect(slowDeleteCalls).toBe(1));
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

	it("explains the instance policy and hides registration when own-provider connections are disabled", async () => {
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
