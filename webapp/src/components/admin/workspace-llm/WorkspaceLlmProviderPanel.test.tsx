import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http, type PathParams } from "msw";
import { describe, expect, it } from "vitest";

import {
	workspaceListLlmConnectionsQueryKey,
	workspaceListLlmModelsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type {
	UpdateWorkspaceLlmConnectionRequest,
	UpdateWorkspaceLlmModelRequest,
	WorkspaceLlmConnection,
	WorkspaceLlmModel,
} from "@/api/types.gen";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";

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
		dataHandlingTier: "UNDECLARED",
		id,
		connectionId,
		connectionDisplayName:
			connections.find((connection) => connection.id === connectionId)?.displayName ?? "",
		slug: `model-${id}`,
		displayName,
		upstreamModelId: `upstream-${id}`,
		enabled: true,
		pricingMode: "UNPRICED",
		currency: "USD",
		createdAt: new Date("2026-07-01T00:00:00Z"),
	};
}

function renderPanel(ownProviderAllowed = true) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	const rendered = render(
		<QueryClientProvider client={queryClient}>
			<WorkspaceLlmProviderPanel workspaceSlug="demo" ownProviderAllowed={ownProviderAllowed} />
		</QueryClientProvider>,
	);
	return { ...rendered, queryClient };
}

/** A problem body carries its status, which is what the generated client throws. */
const problem = (status: number) => HttpResponse.json({ title: "Not now", status }, { status });
const unavailable = () => problem(503);

const draftNameIn = (dialogName: string) =>
	within(screen.getByRole("dialog", { name: dialogName })).getByLabelText<HTMLInputElement>(
		"Display name",
	).value;

async function confirmDelete(name: string) {
	fireEvent.click(await screen.findByRole("button", { name: `Delete ${name}` }));
	const dialog = await screen.findByRole("alertdialog");
	fireEvent.click(within(dialog).getByRole("button", { name: "Delete model" }));
}

describe("WorkspaceLlmProviderPanel", () => {
	it("renders every workspace connection and groups each model under its owner", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "GPT shared endpoint"), model(20, 2, "GPU coder")]),
			),
		);
		renderPanel();

		await screen.findByText("OpenAI production");
		screen.getByText("Local GPU");
		await screen.findByText("GPT shared endpoint");
		screen.getByText("GPU coder");
	});

	it("keeps each provider's probe pending independently when two run at once", async () => {
		// A single "which row is probing" flag would be cleared by the fast one and put the slow row
		// back to idle mid-flight.
		const slowProbe = deferred<undefined>();
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json(connections)),
			http.get("*/workspaces/demo/llm/models", () => HttpResponse.json([])),
			http.post("*/workspaces/demo/llm/connections/1/probe", async () => {
				await slowProbe.promise;
				return HttpResponse.json({ reachable: true, modelCount: 3 });
			}),
			http.post("*/workspaces/demo/llm/connections/2/probe", () =>
				HttpResponse.json({ reachable: true, modelCount: 1 }),
			),
		);
		renderPanel();

		const openAiCard = await screen.findByRole("region", { name: "OpenAI production" });
		const gpuCard = screen.getByRole("region", { name: "Local GPU" });

		fireEvent.click(
			within(openAiCard).getByRole("button", { name: "Test connection to OpenAI production" }),
		);
		fireEvent.click(within(gpuCard).getByRole("button", { name: "Test connection to Local GPU" }));

		await within(gpuCard).findByText(/1 model available/u);

		const slowButton = within(openAiCard).getByRole<HTMLButtonElement>("button", {
			name: "Testing… OpenAI production",
		});
		expect(slowButton.disabled).toBe(true);

		slowProbe.resolve(undefined);
		await within(openAiCard).findByText(/3 models available/u);
	});

	it("keeps each model's delete pending independently when two run at once", async () => {
		// A single "which model is mutating" id would be cleared by the fast one, re-enabling the slow
		// row's Delete mid-flight: a second DELETE, and a failure toast for a model that was deleted.
		const slowDelete = deferred<undefined>();
		let slowDeleteCalls = 0;
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "Slow model"), model(20, 1, "Fast model")]),
			),
			http.delete("*/workspaces/demo/llm/models/10", async () => {
				slowDeleteCalls += 1;
				await slowDelete.promise;
				return new HttpResponse(null, { status: 204 });
			}),
			http.delete("*/workspaces/demo/llm/models/20", () => new HttpResponse(null, { status: 204 })),
		);
		renderPanel();

		await confirmDelete("Slow model");
		await waitFor(() => expect(slowDeleteCalls).toBe(1));
		await confirmDelete("Fast model");
		await waitFor(() => expect(screen.queryByText("Fast model")).not.toBeNull());

		const slowRowDelete = screen.getByRole<HTMLButtonElement>("button", {
			name: "Delete Slow model",
		});
		expect(slowRowDelete.disabled).toBe(true);

		slowDelete.resolve(undefined);
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
		fireEvent.click(await screen.findByRole("button", { name: "Disconnect OpenAI production" }));
		expect(deleted).toBe(false);
		const dialog = screen.getByRole("alertdialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Disconnect provider" }));
		await waitFor(() => expect(deleted).toBe(true));
	});

	it("explains the instance policy and hides registration when own-provider connections are disabled", async () => {
		server.use(http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([])));
		renderPanel(false);
		await screen.findByText("New workspace providers and models are disabled");
		expect(screen.queryByRole("button", { name: "Add connection" })).toBeNull();
	});

	it("does not present a failed model request as an empty catalog", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json({ title: "Unavailable" }, { status: 503 }),
			),
		);
		renderPanel();

		await screen.findByText("We could not load your provider models");
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

		await screen.findByText("Existing model");
		expect(screen.queryByRole("button", { name: "Add provider" })).toBeNull();
		expect(screen.queryByRole("button", { name: "Add model" })).toBeNull();
		screen.getByRole("button", { name: "Edit OpenAI production" });
		screen.getByRole("button", { name: "Edit Existing model" });
		screen.getByRole("button", { name: "Delete Existing model" });
	});

	it("keeps a provider being edited through a failed refresh of the providers, and saves the draft", async () => {
		const connectionReads: number[] = [];
		let modelReads = 0;
		const saved: UpdateWorkspaceLlmConnectionRequest[] = [];
		server.use(
			http.get(
				"*/workspaces/demo/llm/connections",
				() => {
					connectionReads.push(200);
					return HttpResponse.json(connections);
				},
				{ once: true },
			),
			http.get(
				"*/workspaces/demo/llm/connections",
				() => {
					connectionReads.push(503);
					return unavailable();
				},
				{ once: true },
			),
			http.get("*/workspaces/demo/llm/connections", () => {
				connectionReads.push(200);
				return HttpResponse.json(connections);
			}),
			http.get("*/workspaces/demo/llm/models", () => {
				modelReads += 1;
				return HttpResponse.json([model(10, 1, "GPT shared endpoint")]);
			}),
			http.patch<PathParams, UpdateWorkspaceLlmConnectionRequest>(
				"*/workspaces/demo/llm/connections/1",
				async ({ request }) => {
					const body = await request.json();
					saved.push(body);
					return HttpResponse.json({ ...connections[0], displayName: body.displayName });
				},
			),
		);
		const { queryClient } = renderPanel();
		await screen.findByText("GPT shared endpoint");
		fireEvent.click(screen.getByRole("button", { name: "Edit OpenAI production" }));
		const dialog = await screen.findByRole("dialog", { name: "Edit connection" });
		fireEvent.change(within(dialog).getByLabelText("Display name"), {
			target: { value: "OpenAI staging draft" },
		});

		await act(async () => {
			await queryClient.invalidateQueries({
				queryKey: workspaceListLlmConnectionsQueryKey({ path: { workspaceSlug: "demo" } }),
				exact: true,
			});
		});
		expect(connectionReads).toStrictEqual([200, 503]);
		// Both the page's error and a warning in the dialog offer Retry, so the screen has caught up
		// with the failed read once one is there.
		await screen.findByRole("button", { name: "Retry" });
		expect(draftNameIn("Edit connection")).toBe("OpenAI staging draft");
		const modelReadsBeforeRetry = modelReads;
		fireEvent.click(
			within(screen.getByRole("dialog", { name: "Edit connection" })).getByRole("button", {
				name: "Retry",
			}),
		);

		await waitFor(() => expect(connectionReads).toStrictEqual([200, 503, 200]));
		await waitFor(() => expect(screen.queryByRole("button", { name: /^Retry/u })).toBeNull());
		expect(draftNameIn("Edit connection")).toBe("OpenAI staging draft");
		expect(modelReads).toBe(modelReadsBeforeRetry);
		fireEvent.click(
			within(screen.getByRole("dialog", { name: "Edit connection" })).getByRole("button", {
				name: "Save changes",
			}),
		);
		await waitFor(() => expect(saved).toHaveLength(1));
		expect(saved[0]).toMatchObject({ displayName: "OpenAI staging draft" });
	});

	it("keeps a model being edited through a failed refresh of the models, and saves the draft", async () => {
		const modelReads: number[] = [];
		let connectionReads = 0;
		const saved: UpdateWorkspaceLlmModelRequest[] = [];
		const models = [model(10, 1, "GPT shared endpoint")];
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => {
				connectionReads += 1;
				return HttpResponse.json([connections[0]]);
			}),
			http.get(
				"*/workspaces/demo/llm/models",
				() => {
					modelReads.push(200);
					return HttpResponse.json(models);
				},
				{ once: true },
			),
			http.get(
				"*/workspaces/demo/llm/models",
				() => {
					modelReads.push(503);
					return unavailable();
				},
				{ once: true },
			),
			http.get("*/workspaces/demo/llm/models", () => {
				modelReads.push(200);
				return HttpResponse.json(models);
			}),
			http.patch<PathParams, UpdateWorkspaceLlmModelRequest>(
				"*/workspaces/demo/llm/models/10",
				async ({ request }) => {
					const body = await request.json();
					saved.push(body);
					return HttpResponse.json({ ...model(10, 1, "GPT shared endpoint"), ...body });
				},
			),
		);
		const { queryClient } = renderPanel();
		fireEvent.click(await screen.findByRole("button", { name: "Edit GPT shared endpoint" }));
		const dialog = await screen.findByRole("dialog", { name: "Edit model" });
		fireEvent.change(within(dialog).getByLabelText("Display name"), {
			target: { value: "GPT draft name" },
		});

		await act(async () => {
			await queryClient.invalidateQueries({
				queryKey: workspaceListLlmModelsQueryKey({ path: { workspaceSlug: "demo" } }),
				exact: true,
			});
		});
		expect(modelReads).toStrictEqual([200, 503]);
		// Both the page's error and a warning in the dialog offer Retry, so the screen has caught up
		// with the failed read once one is there.
		await screen.findByRole("button", { name: "Retry" });
		expect(draftNameIn("Edit model")).toBe("GPT draft name");
		const connectionReadsBeforeRetry = connectionReads;
		fireEvent.click(
			within(screen.getByRole("dialog", { name: "Edit model" })).getByRole("button", {
				name: "Retry",
			}),
		);

		await waitFor(() => expect(modelReads).toStrictEqual([200, 503, 200]));
		await waitFor(() => expect(screen.queryByRole("button", { name: /^Retry/u })).toBeNull());
		expect(draftNameIn("Edit model")).toBe("GPT draft name");
		expect(connectionReads).toBe(connectionReadsBeforeRetry);
		fireEvent.click(
			within(screen.getByRole("dialog", { name: "Edit model" })).getByRole("button", {
				name: "Save changes",
			}),
		);
		await waitFor(() => expect(saved).toHaveLength(1));
		expect(saved[0]).toMatchObject({ displayName: "GPT draft name" });
	});

	it.each([
		{
			read: "providers",
			status: 403,
			refusal: /Ask a workspace admin or instance admin for access/u,
			path: "*/workspaces/demo/llm/connections",
			queryKey: () => workspaceListLlmConnectionsQueryKey({ path: { workspaceSlug: "demo" } }),
			dialogName: "Edit connection",
			earlierName: "OpenAI production",
			laterName: "OpenAI renamed elsewhere",
			earlier: () => HttpResponse.json([connections[0]]),
			later: () =>
				HttpResponse.json([{ ...connections[0], displayName: "OpenAI renamed elsewhere" }]),
			other: http.get("*/workspaces/demo/llm/models", () => HttpResponse.json([])),
		},
		{
			read: "models",
			status: 404,
			refusal: /It may have been deleted or moved/u,
			path: "*/workspaces/demo/llm/models",
			queryKey: () => workspaceListLlmModelsQueryKey({ path: { workspaceSlug: "demo" } }),
			dialogName: "Edit model",
			earlierName: "GPT shared endpoint",
			laterName: "GPT renamed elsewhere",
			earlier: () => HttpResponse.json([model(10, 1, "GPT shared endpoint")]),
			later: () => HttpResponse.json([model(10, 1, "GPT renamed elsewhere")]),
			other: http.get("*/workspaces/demo/llm/connections", () =>
				HttpResponse.json([connections[0]]),
			),
		},
	])(
		"closes a form when the $read read is refused, and opens it afresh only from a later authorized read",
		async ({
			status,
			refusal,
			path,
			queryKey,
			dialogName,
			earlierName,
			laterName,
			earlier,
			later,
			other,
		}) => {
			const served: number[] = [];
			server.use(
				other,
				http.get(
					path,
					() => {
						served.push(200);
						return earlier();
					},
					{ once: true },
				),
				http.get(
					path,
					() => {
						served.push(status);
						return problem(status);
					},
					{ once: true },
				),
				http.get(
					path,
					() => {
						served.push(503);
						return unavailable();
					},
					{ once: true },
				),
				http.get(path, () => {
					served.push(200);
					return later();
				}),
			);
			const { queryClient } = renderPanel();
			fireEvent.click(await screen.findByRole("button", { name: `Edit ${earlierName}` }));
			const dialog = await screen.findByRole("dialog", { name: dialogName });
			fireEvent.change(within(dialog).getByLabelText("Display name"), {
				target: { value: "Draft that must not return" },
			});
			const refresh = async () => {
				await act(async () => {
					await queryClient.invalidateQueries({ queryKey: queryKey(), exact: true });
				});
			};

			await refresh();
			expect(served).toStrictEqual([200, status]);
			await screen.findByText(refusal);
			expect(screen.queryByRole("dialog")).toBeNull();
			expect(screen.queryByText(earlierName)).toBeNull();

			await refresh();
			expect(served).toStrictEqual([200, status, 503]);
			// The query settles before its observers hear of it; the screen has caught up once the
			// refusal it was showing is gone.
			await waitFor(() => expect(screen.queryByText(refusal)).toBeNull());
			expect(screen.queryByRole("dialog")).toBeNull();
			expect(screen.queryByText(earlierName)).toBeNull();
			expect(screen.queryByDisplayValue("Draft that must not return")).toBeNull();

			await refresh();
			expect(served).toStrictEqual([200, status, 503, 200]);
			const editFresh = await screen.findByRole("button", { name: `Edit ${laterName}` });
			expect(screen.queryByRole("dialog")).toBeNull();
			fireEvent.click(editFresh);
			expect(draftNameIn(dialogName)).toBe(laterName);
		},
	);

	it("hides the panel for a refused read even while another read only failed", async () => {
		server.use(
			http.get("*/workspaces/demo/llm/connections", () => HttpResponse.json([connections[0]])),
			http.get("*/workspaces/demo/llm/models", () =>
				HttpResponse.json([model(10, 1, "GPT shared endpoint")]),
			),
		);
		const { queryClient } = renderPanel();
		await screen.findByText("GPT shared endpoint");
		server.use(
			http.get("*/workspaces/demo/llm/connections", unavailable, { once: true }),
			http.get("*/workspaces/demo/llm/models", () => problem(403), { once: true }),
		);
		await act(async () => {
			await Promise.all([
				queryClient.invalidateQueries({
					queryKey: workspaceListLlmConnectionsQueryKey({ path: { workspaceSlug: "demo" } }),
					exact: true,
				}),
				queryClient.invalidateQueries({
					queryKey: workspaceListLlmModelsQueryKey({ path: { workspaceSlug: "demo" } }),
					exact: true,
				}),
			]);
		});

		await screen.findByText(/Ask a workspace admin or instance admin for access/u);
		expect(screen.queryByText("GPT shared endpoint")).toBeNull();
		expect(screen.queryByText("We could not refresh your AI providers")).toBeNull();
	});

	it("shows the panel's shape while the providers are read, then the empty state", async () => {
		const held = deferred<Response>();
		server.use(http.get("*/workspaces/demo/llm/connections", async () => held.promise));
		renderPanel();

		await screen.findByText("Loading your AI providers…");
		expect(screen.queryByText("Connect an OpenAI-compatible provider")).toBeNull();
		held.resolve(HttpResponse.json([]));

		await screen.findByText("Connect an OpenAI-compatible provider");
		expect(screen.queryByText("Loading your AI providers…")).toBeNull();
	});
});
