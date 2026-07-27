import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { LlmConnection, LlmModel } from "@/api/types.gen";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules; observed ~3s alone
// and over the 5s default when the rest of the suite is competing for the box.
vi.setConfig({ testTimeout: 20_000 });

/** The first mount in a file pays the lazy transform of the whole admin layout — seconds, not 1s. */
const TRANSFORM_WAIT = { timeout: 10_000 };

/** The four queries the page opens with. Any test that needs different answers overrides them after. */
function mockPage(connections: LlmConnection[] = [], models: LlmModel[] = []) {
	server.use(
		http.get("*/admin/llm/connections", () => HttpResponse.json(connections)),
		http.get("*/admin/llm/models", () => HttpResponse.json(models)),
		http.get("*/admin/workspaces", () => HttpResponse.json([])),
		// Without this the settings query falls through to a real fetch, errors, and the policy card's
		// switch flips from controlled to uncontrolled mid-test — noise, not a finding.
		http.get("*/admin/llm/settings", () =>
			HttpResponse.json({ allowWorkspaceConnections: true, allowedEgressHosts: "" }),
		),
	);
}

/**
 * The real router, not `Route.options.component`: the gate in `admin.tsx`'s `beforeLoad`, the route's
 * `head` and anything it reads off the URL only exist when the route is matched, and a test that
 * calls the component directly cannot tell a working route from an unreachable one.
 */
async function renderModelsRoute() {
	// One client for the guards and the provider, exactly as `main.tsx` wires it.
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: ["/admin/models"] }),
		context: { queryClient, auth: undefined },
	});
	render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				{/* biome-ignore lint/suspicious/noExplicitAny: the app's router context is wider than this test needs. */}
				<RouterProvider router={router as any} />
			</AuthProvider>
		</QueryClientProvider>,
	);
	return screen.findByRole("heading", { name: "AI models" }, TRANSFORM_WAIT);
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

describe("instance AI models route", () => {
	it("renders before a connection or provider probe has been selected", async () => {
		mockPage();

		expect(await renderModelsRoute()).toBeTruthy();
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
		mockPage(connections, models);
		server.use(
			http.patch("*/admin/llm/connections/1", async () => {
				slowToggleCalls += 1;
				await slowToggle;
				return HttpResponse.json({ ...connections[0], enabled: false });
			}),
			http.patch("*/admin/llm/connections/2", () =>
				HttpResponse.json({ ...connections[1], enabled: false }),
			),
		);

		await renderModelsRoute();

		const confirmTurnOff = async (name: string) => {
			fireEvent.click(await screen.findByRole("switch", { name }, TRANSFORM_WAIT));
			const dialog = await screen.findByRole("alertdialog");
			fireEvent.click(within(dialog).getByRole("button", { name: "Turn off connection" }));
			await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		};

		await confirmTurnOff("Slow provider");
		await waitFor(() => expect(slowToggleCalls).toBe(1));
		await confirmTurnOff("Fast provider");
		// The fast one settles and the list is refetched.
		await waitFor(() =>
			expect(screen.getByRole("switch", { name: "Fast provider" }).getAttribute("aria-busy")).toBe(
				"false",
			),
		);

		// The still-running row reads as busy and refuses input; the settled one is usable again.
		expect(screen.getByRole("switch", { name: "Slow provider" }).getAttribute("aria-busy")).toBe(
			"true",
		);
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
		mockPage(connections, [sharedModel]);
		server.use(
			http.put("*/admin/llm/models/7/sharing", async () => {
				sharingCalls += 1;
				await slowSharing;
				return HttpResponse.json(sharedModel);
			}),
		);

		await renderModelsRoute();

		fireEvent.click(
			await screen.findByRole("button", { name: "Manage access for GPT Test" }, TRANSFORM_WAIT),
		);
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
