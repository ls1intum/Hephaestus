// Container tests for the Outline admin surface. The container drives real generated-client
// requests, so these tests run against MSW (see src/test/setup-msw.ts) with mutable per-test
// state: a mutation handler flips the state the follow-up GET returns, which is exactly how
// the container's invalidate-after-mutate contract becomes observable in the DOM.
// jest-dom matchers and user-event are NOT set up in this repo's vitest, so assertions use
// plain DOM (`.disabled`, `queryByRole`) and `fireEvent`.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { AdminOutlineSettings } from "./AdminOutlineSettings";

vi.mock("sonner", () => ({
	toast: { success: vi.fn(), error: vi.fn() },
}));

function renderContainer() {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<QueryClientProvider client={queryClient}>
			<AdminOutlineSettings workspaceSlug="demo" />
		</QueryClientProvider>,
	);
}

const activeOutlineConnection = {
	id: 7,
	kind: "OUTLINE",
	state: "ACTIVE",
	family: "DOCUMENTATION",
	displayName: "Acme Wiki",
	instanceKey: "9a1b2c3d",
};

const engineering = {
	id: 1,
	collectionId: "col-eng",
	name: "Engineering",
	urlId: "engineering-4nZ3x",
	color: "#4E5C6E",
	state: "ENABLED",
	syncStatus: "COMPLETE",
	documentCount: 12,
	lastSyncedAt: "2026-07-01T00:00:00Z",
	createdAt: "2026-06-01T00:00:00Z",
};

/** Unified `ConnectionSyncStatus` for the connected Outline connection (id 7). */
const healthyStatus = {
	connectionId: 7,
	connectionState: "ACTIVE",
	kind: "OUTLINE",
	health: "HEALTHY",
	webhookRegistered: true,
	lastSuccessfulSyncAt: "2026-07-01T00:00:00Z",
	resourceCounts: { total: 1, errored: 0 },
};

const runningJob = {
	id: 1,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: "2026-07-01T00:00:00Z",
};

const healthyToken = {
	accepted: true,
	name: "Hephaestus mirror",
	last4: "9f2c",
	lastActiveAt: "2026-07-01T00:00:00Z",
};

/** Baseline handlers for an already-connected workspace with a mutable collection list. */
function useConnectedHandlers(collectionsRef: { current: unknown[] }) {
	server.use(
		http.get("*/workspaces/demo/connections", () => HttpResponse.json([activeOutlineConnection])),
		http.get("*/workspaces/demo/connections/7/sync", () => HttpResponse.json(healthyStatus)),
		http.get("*/workspaces/demo/connections/outline/token", () => HttpResponse.json(healthyToken)),
		http.get("*/workspaces/demo/outline/collections", () =>
			HttpResponse.json(collectionsRef.current),
		),
	);
}

beforeEach(() => {
	vi.clearAllMocks();
});

describe("AdminOutlineSettings — connect happy path", () => {
	it("connects with server URL + token only (no allow-list field) and flips to the connected state", async () => {
		let connections: unknown[] = [];
		let connectBody: unknown;
		server.use(
			http.get("*/workspaces/demo/connections", () => HttpResponse.json(connections)),
			http.post("*/workspaces/demo/connections", async ({ request }) => {
				connectBody = await request.json();
				connections = [activeOutlineConnection];
				return HttpResponse.json({ connectionId: activeOutlineConnection.id, mode: "LINKED" });
			}),
			http.get("*/workspaces/demo/connections/7/sync", () =>
				HttpResponse.json({
					connectionId: 7,
					connectionState: "ACTIVE",
					kind: "OUTLINE",
					health: "HEALTHY",
					webhookRegistered: true,
					resourceCounts: { total: 0, errored: 0 },
				}),
			),
			http.get("*/workspaces/demo/connections/outline/token", () =>
				HttpResponse.json(healthyToken),
			),
			http.get("*/workspaces/demo/outline/collections", () => HttpResponse.json([])),
		);

		renderContainer();

		// The disconnected form is server URL + token only — the collection allow-list is gone.
		expect(await screen.findByLabelText(/api token/i)).toBeTruthy();
		expect(screen.queryByLabelText(/allow-list/i)).toBeNull();
		expect(screen.queryByLabelText(/collections/i)).toBeNull();

		// The server URL starts EMPTY: a prefilled cloud URL would send a self-hoster's token to
		// Outline Cloud if they only pasted the token. Connect stays disabled until it is typed.
		const serverUrl = screen.getByLabelText(/server url/i) as HTMLInputElement;
		expect(serverUrl.value).toBe("");
		fireEvent.change(screen.getByLabelText(/api token/i), {
			target: { value: "ol_api_secret" },
		});
		expect(
			(screen.getByRole("button", { name: /connect outline/i }) as HTMLButtonElement).disabled,
		).toBe(true);

		fireEvent.change(serverUrl, { target: { value: "https://wiki.acme.dev" } });
		fireEvent.click(screen.getByRole("button", { name: /connect outline/i }));

		await waitFor(() => expect(connectBody).toBeTruthy());
		expect(connectBody).toEqual({
			kind: "OUTLINE",
			userInput: {
				server_url: "https://wiki.acme.dev",
				token: "ol_api_secret",
			},
		});

		// The invalidated connections query refetches → the card flips to connected with status.
		expect(await screen.findByText(/outline connected/i)).toBeTruthy();
		expect(await screen.findByText(/live updates via webhook/i)).toBeTruthy();
		expect(toast.success).toHaveBeenCalledWith("Outline connected");
	});
});

describe("AdminOutlineSettings — add-collection round trip", () => {
	it("registers a picked candidate, disables already-mirrored entries, and shows the new row", async () => {
		const architecture = {
			...engineering,
			id: 2,
			collectionId: "col-arch",
			name: "Architecture Decisions",
			urlId: "adr-9kQ2p",
			syncStatus: "PENDING",
			documentCount: 0,
			lastSyncedAt: undefined,
		};
		const collectionsRef = { current: [engineering] as unknown[] };
		let registerBody: unknown;
		useConnectedHandlers(collectionsRef);
		server.use(
			http.get("*/workspaces/demo/outline/collections/candidates", () =>
				HttpResponse.json([
					{ collectionId: "col-eng", name: "Engineering", alreadyMirrored: true },
					{ collectionId: "col-arch", name: "Architecture Decisions", alreadyMirrored: false },
				]),
			),
			http.post("*/workspaces/demo/outline/collections", async ({ request }) => {
				registerBody = await request.json();
				collectionsRef.current = [engineering, architecture];
				return HttpResponse.json(architecture, { status: 201 });
			}),
		);

		renderContainer();
		expect(await screen.findByText("Engineering")).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /add collection/i }));
		const dialog = await screen.findByRole("dialog");

		// The candidates load lazily on open; the already-mirrored one is checked + disabled.
		// (The base-ui Checkbox renders a role="checkbox" span, so disabled is ARIA, not a DOM prop.)
		const mirrored = await within(dialog).findByRole("checkbox", { name: /engineering/i });
		expect(mirrored.getAttribute("aria-disabled")).toBe("true");
		expect(mirrored.getAttribute("aria-checked")).toBe("true");

		fireEvent.click(within(dialog).getByRole("checkbox", { name: /architecture decisions/i }));
		fireEvent.click(within(dialog).getByRole("button", { name: /add 1 collection/i }));

		await waitFor(() => expect(registerBody).toEqual({ collectionId: "col-arch" }));

		// Invalidation refetches the list → the new row lands in the table and the dialog closed.
		expect(await screen.findByText("Architecture Decisions")).toBeTruthy();
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
	});

	it("surfaces the 502 ProblemDetail inline when the candidates probe cannot reach Outline", async () => {
		const collectionsRef = { current: [] as unknown[] };
		useConnectedHandlers(collectionsRef);
		server.use(
			http.get("*/workspaces/demo/outline/collections/candidates", () =>
				HttpResponse.json(
					{
						type: "about:blank",
						title: "Bad Gateway",
						status: 502,
						detail: "Outline did not respond to collections.list.",
					},
					{ status: 502 },
				),
			),
		);

		renderContainer();
		// Empty list ⇒ both a header button and an empty-state CTA; open via the header one.
		fireEvent.click((await screen.findAllByRole("button", { name: /add collection/i }))[0]);
		const dialog = await screen.findByRole("dialog");

		expect((await within(dialog).findByRole("alert")).textContent).toMatch(
			/outline did not respond to collections\.list/i,
		);
		expect(within(dialog).getByRole("button", { name: /^retry$/i })).toBeTruthy();
	});
});

describe("AdminOutlineSettings — pause / resume", () => {
	it("pauses via the row menu and reflects the refetched PAUSED state, then resumes", async () => {
		const pausedRow = { ...engineering, state: "PAUSED" };
		const collectionsRef = { current: [engineering] as unknown[] };
		const patchBodies: unknown[] = [];
		useConnectedHandlers(collectionsRef);
		server.use(
			http.patch(
				"*/workspaces/demo/outline/collections/:collectionId",
				async ({ request, params }) => {
					const body = (await request.json()) as { state: string };
					patchBodies.push({ collectionId: params.collectionId, ...body });
					const next = body.state === "PAUSED" ? pausedRow : engineering;
					collectionsRef.current = [next];
					return HttpResponse.json(next);
				},
			),
		);

		renderContainer();
		expect(await screen.findByText("Engineering")).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /actions for engineering/i }));
		fireEvent.click(await screen.findByRole("menuitem", { name: /^pause$/i }));

		await waitFor(() =>
			expect(patchBodies).toContainEqual({ collectionId: "col-eng", state: "PAUSED" }),
		);
		// Invalidation refetches the list → the badge flips to Paused.
		expect(await screen.findByText("Paused")).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /actions for engineering/i }));
		fireEvent.click(await screen.findByRole("menuitem", { name: /^resume$/i }));

		await waitFor(() =>
			expect(patchBodies).toContainEqual({ collectionId: "col-eng", state: "ENABLED" }),
		);
		expect(await screen.findByText("Mirroring")).toBeTruthy();
	});
});

describe("AdminOutlineSettings — remove with confirm", () => {
	it("states the erase in the confirm copy, deletes, and refetches to the empty state", async () => {
		const collectionsRef = { current: [engineering] as unknown[] };
		let deletedId: string | undefined;
		useConnectedHandlers(collectionsRef);
		server.use(
			http.delete("*/workspaces/demo/outline/collections/:collectionId", ({ params }) => {
				deletedId = String(params.collectionId);
				collectionsRef.current = [];
				return new HttpResponse(null, { status: 204 });
			}),
		);

		renderContainer();
		expect(await screen.findByText("Engineering")).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /actions for engineering/i }));
		fireEvent.click(await screen.findByRole("menuitem", { name: /remove & erase/i }));

		const dialog = await screen.findByRole("alertdialog");
		// Nothing is deleted until the confirm — and the copy must state the mirror erase.
		expect(deletedId).toBeUndefined();
		expect(dialog.textContent).toMatch(
			/permanently erases all 12 mirrored documents\s+from Hephaestus/i,
		);

		fireEvent.click(within(dialog).getByRole("button", { name: /remove & erase/i }));

		await waitFor(() => expect(deletedId).toBe("col-eng"));
		// Invalidation refetches the (now empty) list → empty state replaces the table.
		expect(await screen.findByText(/no collections mirrored yet/i)).toBeTruthy();
		expect(toast.success).toHaveBeenCalledWith(
			"Collection removed and its mirrored documents erased",
		);
	});
});

describe("AdminOutlineSettings — sync now", () => {
	it("fires the reconcile trigger and confirms with a toast", async () => {
		const collectionsRef = { current: [engineering] as unknown[] };
		let syncRequestBody: unknown;
		useConnectedHandlers(collectionsRef);
		server.use(
			http.post("*/workspaces/demo/connections/7/sync/jobs", async ({ request }) => {
				syncRequestBody = await request.json();
				return HttpResponse.json(runningJob);
			}),
		);

		renderContainer();
		fireEvent.click(await screen.findByRole("button", { name: /sync now/i }));

		await waitFor(() => expect(syncRequestBody).toEqual({ type: "RECONCILIATION" }));
		await waitFor(() => expect(toast.success).toHaveBeenCalledWith("Sync started"));
	});
});

describe("AdminOutlineSettings — token lifecycle", () => {
	it("surfaces a rejected token as a destructive alert instead of a healthy-looking card", async () => {
		const collectionsRef = { current: [engineering] as unknown[] };
		useConnectedHandlers(collectionsRef);
		server.use(
			http.get("*/workspaces/demo/connections/outline/token", () =>
				HttpResponse.json({ accepted: false }),
			),
		);

		renderContainer();

		expect(await screen.findByText(/outline no longer accepts this token/i)).toBeTruthy();
		expect(screen.queryByText(/outline accepts this token/i)).toBeNull();
	});

	it("names the token and its last4 when Outline lets the key list itself", async () => {
		const collectionsRef = { current: [engineering] as unknown[] };
		useConnectedHandlers(collectionsRef);

		renderContainer();

		expect(await screen.findByText(/outline accepts this token/i)).toBeTruthy();
		expect(screen.getByText(/Hephaestus mirror/)).toBeTruthy();
		expect(screen.getByText(/…9f2c/)).toBeTruthy();
	});
});

describe("AdminOutlineSettings — a running sync is polled, not left as dead pixels", () => {
	it("keeps refetching the status while a job is active, and stops once it settles", async () => {
		const collectionsRef = { current: [engineering] as unknown[] };
		let statusReads = 0;
		useConnectedHandlers(collectionsRef);
		server.use(
			http.get("*/workspaces/demo/connections/7/sync", () => {
				statusReads += 1;
				// The reconcile is running on the first read and finished by the second.
				return HttpResponse.json({
					...healthyStatus,
					activeJob: statusReads < 2 ? runningJob : undefined,
				});
			}),
		);

		renderContainer();

		expect(await screen.findByText(/sync in progress/i)).toBeTruthy();
		// No user interaction, no invalidation — only the refetchInterval can clear this.
		await waitFor(() => expect(screen.queryByText(/sync in progress/i)).toBeNull(), {
			timeout: 8000,
		});
		expect(statusReads).toBeGreaterThanOrEqual(2);
	}, 10000);
});

describe("AdminOutlineSettings — Outline not enabled on this instance", () => {
	it("turns the initiate 400 (no strategy for the kind) into a clear 'not available here' hint", async () => {
		// A deployment with HEPHAESTUS_INTEGRATION_OUTLINE_ENABLED off has no OutlineConnectionStrategy
		// bean, so ConnectionController.initiate rejects the kind with exactly this 400. The card must
		// explain that instead of only echoing the developer-facing message, so an admin does not keep
		// retrying a connect that can never succeed.
		server.use(
			http.get("*/workspaces/demo/connections", () => HttpResponse.json([])),
			http.post("*/workspaces/demo/connections", () =>
				HttpResponse.json(
					{
						type: "about:blank",
						title: "Invalid request",
						status: 400,
						detail: "No ConnectionStrategy registered for kind=OUTLINE",
					},
					{ status: 400 },
				),
			),
		);

		renderContainer();

		fireEvent.change(await screen.findByLabelText(/server url/i), {
			target: { value: "https://wiki.acme.dev" },
		});
		fireEvent.change(screen.getByLabelText(/api token/i), { target: { value: "ol_api_secret" } });
		fireEvent.click(screen.getByRole("button", { name: /connect outline/i }));

		// The raw ProblemDetail is still shown; the hint is derived from it and added below.
		expect(await screen.findByText(/outline may not be enabled on this instance/i)).toBeTruthy();
		expect(screen.getByText(/no connectionstrategy registered for kind=outline/i)).toBeTruthy();
	});
});

describe("AdminOutlineSettings — collections plane is gated on the connection", () => {
	it("does not render the collections section while disconnected", async () => {
		server.use(http.get("*/workspaces/demo/connections", () => HttpResponse.json([])));

		renderContainer();
		expect(await screen.findByRole("button", { name: /connect outline/i })).toBeTruthy();
		expect(screen.queryByText(/mirrored collections/i)).toBeNull();
	});
});
