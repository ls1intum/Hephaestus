import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import {
	adminGetLlmSettingsQueryKey,
	adminListLlmConnectionsQueryKey,
	adminListLlmModelsQueryKey,
	adminListWorkspacesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { LlmConnection } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { Route } from "./admin.models";

const AdminLlmPage = Route.options.component;

// `preload()` lazily transforms the whole (heavy) route module; observed ~3s alone and over the 5s
// default when the rest of the suite is competing for the box.
vi.setConfig({ testTimeout: 15_000 });

function seededClient() {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	queryClient.setQueryData(adminListLlmConnectionsQueryKey(), []);
	queryClient.setQueryData(adminListLlmModelsQueryKey(), []);
	queryClient.setQueryData(adminListWorkspacesQueryKey(), []);
	queryClient.setQueryData(adminGetLlmSettingsQueryKey(), {
		allowWorkspaceConnections: true,
		allowedEgressHosts: "",
	});
	return queryClient;
}

async function renderPage(queryClient: QueryClient) {
	if (!AdminLlmPage) throw new Error("Admin LLM route must have a component");
	await (AdminLlmPage as typeof AdminLlmPage & { preload: () => Promise<unknown> }).preload();
	return render(
		<QueryClientProvider client={queryClient}>
			<AdminLlmPage />
		</QueryClientProvider>,
	);
}

/** A connection with no models on it turns off without a confirm, so every fixture here has one. */
function model(id: number, connectionId: number, displayName: string) {
	return {
		id,
		connectionId,
		connectionDisplayName: `Connection ${connectionId}`,
		slug: `model-${id}`,
		displayName,
		upstreamModelId: `upstream-${id}`,
		enabled: true,
		visibility: "PUBLIC" as const,
		grantedWorkspaceIds: [],
		supportsReasoning: false,
		createdAt: new Date("2026-07-01T00:00:00Z"),
	};
}

function connection(id: number, displayName: string): LlmConnection {
	return {
		id,
		slug: `connection-${id}`,
		displayName,
		authMode: "BEARER",
		apiProtocol: "openai-responses",
		baseUrl: `https://provider-${id}.example.test/v1`,
		enabled: true,
		hasApiKey: true,
		apiKeyLast4: "1111",
		createdAt: new Date("2026-07-01T00:00:00Z"),
	};
}

describe("AdminLlmPage", () => {
	it("renders before a connection or provider probe has been selected", async () => {
		await renderPage(seededClient());

		expect(screen.getByRole("heading", { name: "AI models" })).toBeTruthy();
	});

	it("keeps each connection's toggle pending independently when two run at once", async () => {
		// The confirm closes on click and the PATCH runs in the background, so a second row can be
		// toggled while the first request is still out — nothing on this page holds the admin still
		// between the two. Connection 1's PATCH hangs, connection 2's answers at once. A single
		// "which connection is mutating" id is cleared by 2 settling, which re-enables row 1's switch
		// while its request is in flight — a second PATCH for a connection that is already off.
		let releaseSlowToggle: (() => void) | undefined;
		const slowToggle = new Promise<void>((resolve) => {
			releaseSlowToggle = resolve;
		});
		let slowToggleCalls = 0;
		const connections = [connection(1, "Slow provider"), connection(2, "Fast provider")];
		const models = [model(11, 1, "Slow model"), model(12, 2, "Fast model")];
		server.use(
			http.get("*/admin/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/admin/llm/models", () => HttpResponse.json(models)),
			http.get("*/admin/workspaces", () => HttpResponse.json([])),
			// Without this the settings query falls through to a real fetch, errors, and the policy
			// card's switch flips from controlled to uncontrolled mid-test — noise, not a finding.
			http.get("*/admin/llm/settings", () =>
				HttpResponse.json({ allowWorkspaceConnections: true, allowedEgressHosts: "" }),
			),
			http.patch("*/admin/llm/connections/1", async () => {
				slowToggleCalls += 1;
				await slowToggle;
				return HttpResponse.json({ ...connections[0], enabled: false });
			}),
			http.patch("*/admin/llm/connections/2", () =>
				HttpResponse.json({ ...connections[1], enabled: false }),
			),
		);

		const queryClient = seededClient();
		queryClient.setQueryData(adminListLlmConnectionsQueryKey(), connections);
		queryClient.setQueryData(adminListLlmModelsQueryKey(), models);
		await renderPage(queryClient);

		const confirmTurnOff = async (name: string) => {
			fireEvent.click(await screen.findByRole("switch", { name: `Turn off ${name}` }));
			const dialog = await screen.findByRole("alertdialog");
			fireEvent.click(within(dialog).getByRole("button", { name: "Turn off connection" }));
			await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		};

		await confirmTurnOff("Slow provider");
		await waitFor(() => expect(slowToggleCalls).toBe(1));
		await confirmTurnOff("Fast provider");
		// The fast one settles and the list is refetched.
		await waitFor(() =>
			expect(
				screen.getByRole("switch", { name: "Turn off Fast provider" }).getAttribute("aria-busy"),
			).toBe("false"),
		);

		// The still-running row reads as busy and refuses input; the settled one is usable again.
		expect(
			screen.getByRole("switch", { name: "Turn off Slow provider" }).getAttribute("aria-busy"),
		).toBe("true");
		expect(
			(screen.getByRole("button", { name: "Delete Slow provider" }) as HTMLButtonElement).disabled,
		).toBe(true);
		expect(
			(screen.getByRole("button", { name: "Delete Fast provider" }) as HTMLButtonElement).disabled,
		).toBe(false);

		releaseSlowToggle?.();
		await waitFor(() => expect(slowToggleCalls).toBe(1));
	});

	it("lets the access dialog be dismissed while its save is still in flight", async () => {
		// `fetch` has no timeout of its own, so against a provider that accepts the connection and
		// never answers, a dialog that refuses to close while `isPending` is a trap: Escape, the close
		// button and Cancel are all inert while the popup holds focus, and nothing releases it.
		let releaseSlowSharing: (() => void) | undefined;
		const slowSharing = new Promise<void>((resolve) => {
			releaseSlowSharing = resolve;
		});
		let sharingCalls = 0;
		const connections = [connection(1, "Shared OpenAI")];
		const sharedModel = model(7, 1, "GPT Test");
		server.use(
			http.get("*/admin/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/admin/llm/models", () => HttpResponse.json([sharedModel])),
			http.get("*/admin/workspaces", () => HttpResponse.json([])),
			http.get("*/admin/llm/settings", () =>
				HttpResponse.json({ allowWorkspaceConnections: true, allowedEgressHosts: "" }),
			),
			http.put("*/admin/llm/models/7/sharing", async () => {
				sharingCalls += 1;
				await slowSharing;
				return HttpResponse.json(sharedModel);
			}),
		);

		const queryClient = seededClient();
		queryClient.setQueryData(adminListLlmConnectionsQueryKey(), connections);
		queryClient.setQueryData(adminListLlmModelsQueryKey(), [sharedModel]);
		await renderPage(queryClient);

		fireEvent.click(await screen.findByRole("button", { name: "Manage access for GPT Test" }));
		const dialog = await screen.findByRole("dialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Save access" }));
		await waitFor(() => expect(sharingCalls).toBe(1));

		fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
		// The request was not cancelled, so the row it belongs to stays disabled — dismissing the
		// dialog gives focus back without opening the door to a second save.
		expect(
			(screen.getByRole("button", { name: "Manage access for GPT Test" }) as HTMLButtonElement)
				.disabled,
		).toBe(true);

		releaseSlowSharing?.();
		await waitFor(() => expect(sharingCalls).toBe(1));
	});
});
