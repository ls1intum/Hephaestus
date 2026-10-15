import type { QueryClient } from "@tanstack/react-query";
import { act, cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";

import {
	getIntegrationCatalogQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type {
	ConnectionSyncStatus,
	ConnectionDetail,
	IntegrationCatalogEntry,
	SyncJob,
	SyncResourceState,
	Workspace,
} from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAt, renderRouteAtWithRouter } from "@/test/router-harness";

vi.setConfig({ testTimeout: 30_000 });

// This route test owns credential HTTP requests; the SSE transport has its own hook suite.
vi.mock("@/hooks/use-sync-events", () => ({ useSyncEvents: () => false }));

function mockConnection(kind: "GITHUB" | "GITLAB", installationId?: number) {
	const listed = workspaceListItem("acme", { providerType: kind });
	const workspace = {
		...listed,
		kind,
		installationId,
		gitlabWebhookRegistered: false,
		hasPersonalAccessToken: installationId == null,
		hasSlackToken: false,
		publicActivityEnabled: false,
		practiceReviewAutoTriggerEnabled: false,
		practiceReviewManualTriggerEnabled: false,
		updatedAt: listed.createdAt,
	} satisfies Workspace;
	const entry = {
		kind,
		displayName: kind === "GITLAB" ? "GitLab" : "GitHub",
		connected: true,
		connectionId: 7,
		connectionState: "ACTIVE",
		credentialsUnreadableSince: installationId == null ? listed.createdAt : undefined,
	} satisfies IntegrationCatalogEntry;
	const detail: Wire<ConnectionDetail> = { id: 7, kind, state: "ACTIVE" };
	const status: ConnectionSyncStatus = {
		connectionId: 7,
		connectionState: "ACTIVE",
		kind,
		health: "HEALTHY",
		resourceCounts: { total: 0, errored: 0, pending: 0, stale: 0 },
		backfillSupported: true,
	} satisfies ConnectionSyncStatus;
	server.use(
		http.get("*/workspaces", () => HttpResponse.json([listed])),
		http.get("*/workspaces/:workspaceSlug", () => HttpResponse.json(workspace)),
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces/:workspaceSlug/connections/catalog", () => HttpResponse.json([entry])),
		http.get("*/workspaces/:workspaceSlug/connections/7", () => HttpResponse.json(detail)),
		http.get("*/workspaces/:workspaceSlug/connections/7/sync", () => HttpResponse.json(status)),
		http.get("*/workspaces/:workspaceSlug/connections/7/sync/resources", () =>
			HttpResponse.json([]),
		),
		http.get("*/workspaces/:workspaceSlug/connections/7/sync/jobs", () =>
			HttpResponse.json({ content: [], totalPages: 0 }),
		),
		http.get("*/workspaces/:workspaceSlug/repositories", () => HttpResponse.json([])),
	);
	return { entry, workspace, detail, status };
}

describe("source-control credential recovery", () => {
	it.each(["GITHUB", "GITLAB"] as const)(
		"replaces an unreadable %s token and restores sync controls",
		async (kind) => {
			const { entry, workspace } = mockConnection(kind);
			const requests: unknown[] = [];
			server.use(
				http.patch("*/workspaces/acme/token", async ({ request }) => {
					requests.push(await request.json());
					entry.credentialsUnreadableSince = undefined;
					return HttpResponse.json(workspace);
				}),
			);
			renderRouteAt("/w/acme/admin/integrations/scm");
			const input = await screen.findByLabelText(
				"New personal access token",
				undefined,
				ROUTE_RENDER_WAIT,
			);
			expect(screen.queryByRole("button", { name: "Sync now" })).toBeNull();

			const user = userEvent.setup();
			await user.type(input, "  replacement-token  ");
			await user.click(screen.getByRole("button", { name: "Replace token" }));

			await screen.findByRole("button", { name: "Sync now" }, ROUTE_RENDER_WAIT);
			expect(requests).toStrictEqual([{ personalAccessToken: "replacement-token" }]);
			expect(screen.queryByText("The stored token cannot be read")).toBeNull();
			await waitFor(() => expect(input).toHaveProperty("value", ""));
		},
	);

	it("shows a refused GitLab token and its calendar expiry, then clears the problem after replacement", async () => {
		const { entry, workspace, detail, status } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		detail.attentionProblem = "CREDENTIAL_REVOKED";
		detail.tokenExpiresAt = "2026-12-05";
		detail.tokenExpiryCheckedAt = "2026-09-30T03:00:00Z";
		status.health = "DEGRADED";
		server.use(
			http.patch("*/workspaces/acme/token", () => {
				detail.attentionProblem = undefined;
				detail.tokenExpiresAt = undefined;
				detail.tokenExpiryCheckedAt = undefined;
				status.health = "HEALTHY";
				return HttpResponse.json(workspace);
			}),
		);
		renderRouteAt("/w/acme/admin/integrations/scm");
		await screen.findByText("GitLab no longer accepts this token", undefined, ROUTE_RENDER_WAIT);
		screen.getByText("The token expires on 5 December 2026.");
		const user = userEvent.setup();
		await user.type(screen.getByLabelText("New personal access token"), "replacement-token");
		await user.click(screen.getByRole("button", { name: "Replace token" }));
		await waitFor(() =>
			expect(screen.queryByText("GitLab no longer accepts this token")).toBeNull(),
		);
		expect(screen.queryByText("The token expires on 5 December 2026.")).toBeNull();
		await screen.findByText("The token expiry is not available yet.");
	});

	it("says a readable GitHub token's expiry is unknown instead of implying it is valid", async () => {
		const { entry } = mockConnection("GITHUB");
		entry.credentialsUnreadableSince = undefined;
		renderRouteAt("/w/acme/admin/integrations/scm");

		await screen.findByRole("button", { name: "Sync now" }, ROUTE_RENDER_WAIT);
		screen.getByText(
			"The token expiry is not available for this connection. Check the token in GitHub and replace it below if it has expired or been revoked.",
		);
		expect(screen.queryByText("The token has no expiry.")).toBeNull();
		expect(screen.queryByText(/The token expires on/u)).toBeNull();
		const user = userEvent.setup();
		await user.type(screen.getByLabelText("New personal access token"), "replacement-token");
		expect(screen.getByRole("button", { name: "Replace token" })).toHaveProperty("disabled", false);
	});

	it("keeps GitLab token replacement available when its expiry cannot load", async () => {
		const { entry, workspace } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		const requests: unknown[] = [];
		server.use(
			http.get("*/workspaces/:workspaceSlug/connections/7", () =>
				HttpResponse.json(
					{ status: 503, title: "Service Unavailable", detail: "GitLab is unavailable." },
					{ status: 503 },
				),
			),
			http.patch("*/workspaces/acme/token", async ({ request }) => {
				requests.push(await request.json());
				return HttpResponse.json(workspace);
			}),
		);
		renderRouteAt("/w/acme/admin/integrations/scm");

		await screen.findByText("We could not load the token expiry.", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("The token has no expiry.")).toBeNull();
		expect(screen.queryByText(/The token expires on/u)).toBeNull();
		const user = userEvent.setup();
		const input = screen.getByLabelText("New personal access token");
		await user.type(input, "replacement-token");
		await user.click(screen.getByRole("button", { name: "Replace token" }));
		await waitFor(() =>
			expect(requests).toStrictEqual([{ personalAccessToken: "replacement-token" }]),
		);
		await waitFor(() => expect(input).toHaveProperty("value", ""));
	});

	it.each(["GITHUB", "GITLAB"] as const)(
		"keeps the %s draft and the unreadable state when replacement fails",
		async (kind) => {
			mockConnection(kind);
			server.use(
				http.patch("*/workspaces/acme/token", () =>
					HttpResponse.json(
						{
							status: 409,
							title: "Conflict",
							detail: "This connection changed. Reload before replacing its token.",
						},
						{ status: 409 },
					),
				),
			);
			renderRouteAt("/w/acme/admin/integrations/scm");
			const input = await screen.findByLabelText(
				"New personal access token",
				undefined,
				ROUTE_RENDER_WAIT,
			);
			const user = userEvent.setup();
			await user.type(input, "replacement-token");
			await user.click(screen.getByRole("button", { name: "Replace token" }));

			await screen.findByText("This connection changed. Reload before replacing its token.");
			expect(input).toHaveProperty("value", "replacement-token");
			screen.getByText("The stored token cannot be read");
			expect(screen.queryByRole("button", { name: "Sync now" })).toBeNull();
		},
	);

	it("isolates a pending token replacement when switching workspaces", async () => {
		const { workspace } = mockConnection("GITHUB");
		const response = deferred();
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme"), workspaceListItem("other")]),
			),
			http.patch("*/workspaces/acme/token", async () => {
				await response.promise;
				return HttpResponse.json(workspace);
			}),
		);
		const { router, queryClient } = renderRouteAtWithRouter("/w/acme/admin/integrations/scm");
		const input = await screen.findByLabelText(
			"New personal access token",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		const user = userEvent.setup();
		await user.type(input, "acme-private-token");
		await user.click(screen.getByRole("button", { name: "Replace token" }));
		await screen.findByRole("button", { name: "Replacing token…" });
		try {
			await act(async () =>
				router.navigate({
					to: "/w/$workspaceSlug/admin/integrations/scm",
					params: { workspaceSlug: "other" },
				}),
			);
			await waitFor(() => {
				expect(screen.getByLabelText("New personal access token")).toHaveProperty(
					"disabled",
					false,
				);
				expect(screen.getByLabelText("New personal access token")).toHaveProperty("value", "");
			});
		} finally {
			response.resolve();
		}
		await waitFor(() =>
			expect(
				queryClient.getQueryState(
					getIntegrationCatalogQueryKey({
						path: { workspaceSlug: "acme" },
					}),
				)?.isInvalidated,
			).toBe(true),
		);
	});

	it("keeps GitHub App connections on the installation recovery path", async () => {
		mockConnection("GITHUB", 123);
		renderRouteAt("/w/acme/admin/integrations/scm");

		const installationLink = await screen.findByRole(
			"link",
			{ name: "Manage installation on GitHub" },
			ROUTE_RENDER_WAIT,
		);
		expect(installationLink.getAttribute("href")).toBe("https://github.com/settings/installations");
		expect(screen.queryByLabelText("New personal access token")).toBeNull();
	});
});

/** The workspace's monitored paths as the server lists them, and every Stop and Add it receives. */
function serveMonitored(initial: string[]) {
	const monitored = [...initial];
	const commands: string[] = [];
	const command = (request: Request) => {
		const url = new URL(request.url);
		const name = url.searchParams.get("nameWithOwner") ?? "";
		commands.push(`${request.method} ${url.pathname} ${name}`);
		return name;
	};
	server.use(
		http.get("*/workspaces/:workspaceSlug/repositories", () => HttpResponse.json(monitored)),
		http.delete("*/workspaces/:workspaceSlug/repositories", ({ request }) => {
			monitored.splice(monitored.indexOf(command(request)), 1);
			return new HttpResponse(null, { status: 204 });
		}),
		http.post("*/workspaces/:workspaceSlug/repositories", ({ request }) => {
			monitored.push(command(request));
			return new HttpResponse(null, { status: 201 });
		}),
	);
	return { commands, command };
}

describe("monitored repositories", () => {
	it("stops a GitLab repository, still lists it as stopped after a reload, and resumes it only when added", async () => {
		const { entry } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		const { commands } = serveMonitored(["acme/platform/api"]);
		renderRouteAt("/w/acme/admin/integrations/scm");
		const user = userEvent.setup();
		await user.click(
			await screen.findByRole(
				"button",
				{ name: "Stop monitoring acme/platform/api" },
				ROUTE_RENDER_WAIT,
			),
		);
		const dialog = await screen.findByRole("alertdialog");
		const description = within(dialog).getByText(/repository on GitLab is not affected/u);
		expect(description.textContent).toContain("It stays stopped until you add it again below.");
		expect(description.textContent).toContain("If no other workspace on this instance");
		await user.click(within(dialog).getByRole("button", { name: "Stop monitoring" }));

		await screen.findByText("No repositories monitored yet");
		expect(commands).toStrictEqual(["DELETE /workspaces/acme/repositories acme/platform/api"]);

		cleanup();
		renderRouteAt("/w/acme/admin/integrations/scm");
		await screen.findByText("No repositories monitored yet", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("acme/platform/api")).toBeNull();

		await user.type(screen.getByLabelText("Add a repository"), "acme/platform/api");
		await user.click(screen.getByRole("button", { name: "Add repository" }));
		await screen.findByText("acme/platform/api");
		expect(commands).toStrictEqual([
			"DELETE /workspaces/acme/repositories acme/platform/api",
			"POST /workspaces/acme/repositories acme/platform/api",
		]);
	});

	it("keeps a GitLab repository path when adding it back fails, and adds it on retry", async () => {
		const { entry } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		const { commands, command } = serveMonitored([]);
		const failure = deferred();
		server.use(
			http.post(
				"*/workspaces/:workspaceSlug/repositories",
				async ({ request }) => {
					command(request);
					await failure.promise;
					return HttpResponse.json(
						{ status: 503, title: "Service Unavailable", detail: "GitLab is unavailable." },
						{ status: 503 },
					);
				},
				{ once: true },
			),
		);
		renderRouteAt("/w/acme/admin/integrations/scm");
		const input = await screen.findByLabelText("Add a repository", undefined, ROUTE_RENDER_WAIT);
		const user = userEvent.setup();
		await user.type(input, "acme/platform/api");
		try {
			await user.click(screen.getByRole("button", { name: "Add repository" }));
			await waitFor(() =>
				expect(commands).toStrictEqual(["POST /workspaces/acme/repositories acme/platform/api"]),
			);
			expect(input).toHaveProperty("value", "acme/platform/api");
		} finally {
			failure.resolve();
		}

		await screen.findByText("We could not add the repository");
		expect(input.getAttribute("aria-invalid")).toBe("true");
		expect(input).toHaveProperty("value", "acme/platform/api");
		screen.getByText("No repositories monitored yet");
		expect(screen.queryByText("acme/platform/api")).toBeNull();

		await user.click(screen.getByRole("button", { name: "Add repository" }));
		await screen.findByText("acme/platform/api");
		expect(commands).toStrictEqual([
			"POST /workspaces/acme/repositories acme/platform/api",
			"POST /workspaces/acme/repositories acme/platform/api",
		]);
		await waitFor(() => expect(input).toHaveProperty("value", ""));
	});

	it("explains a GitHub Stop with the shared outcome and no GitLab-only promise", async () => {
		const { entry } = mockConnection("GITHUB");
		entry.credentialsUnreadableSince = undefined;
		serveMonitored(["octocat/hello-world"]);
		renderRouteAt("/w/acme/admin/integrations/scm");
		await userEvent
			.setup()
			.click(
				await screen.findByRole(
					"button",
					{ name: "Stop monitoring octocat/hello-world" },
					ROUTE_RENDER_WAIT,
				),
			);

		const dialog = await screen.findByRole("alertdialog");
		const description = within(dialog).getByText(/repository on GitHub is not affected/u);
		expect(description.textContent).toContain("If no other workspace on this instance");
		expect(description.textContent).toContain("To sync it again, add it below.");
		expect(description.textContent).not.toContain("stays stopped");
		expect(description.textContent).not.toContain("permanently");
	});
});

describe("Slack credential recovery", () => {
	function mockSlackConnection(credentialsUnreadableSince?: string) {
		const { entry, workspace } = mockConnection("GITHUB");
		server.use(
			http.get("*/workspaces/:workspaceSlug", () =>
				HttpResponse.json({
					...workspace,
					hasSlackToken: true,
					slackConnectionId: 7,
				}),
			),
			http.get("*/workspaces/:workspaceSlug/connections/catalog", () =>
				HttpResponse.json([
					{ ...entry, kind: "SLACK", displayName: "Slack", credentialsUnreadableSince },
				]),
			),
			http.get("*/workspaces/:workspaceSlug/slack/channels", () => HttpResponse.json([])),
			http.get("*/workspaces/:workspaceSlug/slack/channels/candidates", () =>
				HttpResponse.json([]),
			),
		);
	}

	it("shows an unreadable bot token as unavailable, not as connected", async () => {
		mockSlackConnection("2026-09-20T08:00:00Z");
		renderRouteAt("/w/acme/admin/integrations/slack");

		await screen.findByText("Token unreadable", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("Connected")).toBeNull();
		expect(screen.queryByText(/can post in Slack/u)).toBeNull();
		screen.getByText(/reconnect slack to replace it/iu);
		expect(
			screen.getByRole<HTMLButtonElement>("button", { name: /disconnect slack/iu }).disabled,
		).toBe(false);
	});

	it("reconnects through Slack OAuth for this workspace without disconnecting", async () => {
		mockSlackConnection("2026-09-20T08:00:00Z");
		const vendorUrl = new URL("#slack-authorize", window.location.href).href;
		const response = deferred();
		const initiations: unknown[] = [];
		const requests: Request[] = [];
		const record = ({ request }: { request: Request }) => {
			requests.push(request);
		};
		server.use(
			http.post("*/workspaces/:workspaceSlug/connections", async ({ request, params }) => {
				initiations.push({ workspaceSlug: params.workspaceSlug, body: await request.json() });
				await response.promise;
				return HttpResponse.json({ type: "REDIRECT", vendorUrl });
			}),
		);
		server.events.on("request:start", record);
		try {
			renderRouteAt("/w/acme/admin/integrations/slack");
			const user = userEvent.setup();
			await user.click(
				await screen.findByRole("button", { name: "Reconnect Slack" }, ROUTE_RENDER_WAIT),
			);

			const pending = await screen.findByRole<HTMLButtonElement>("button", {
				name: "Redirecting to Slack…",
			});
			expect(pending.disabled).toBe(true);
			response.resolve();

			await waitFor(() => expect(window.location.href).toBe(vendorUrl));
			expect(initiations).toStrictEqual([
				{ workspaceSlug: "acme", body: { kind: "SLACK", userInput: {} } },
			]);
			expect(
				requests
					.filter((request) => request.method !== "GET")
					.map((request) => `${request.method} ${new URL(request.url).pathname}`),
			).toStrictEqual(["POST /workspaces/acme/connections"]);
		} finally {
			server.events.removeListener("request:start", record);
			window.location.hash = "";
		}
	});

	it("keeps the unreadable connection and says why when Slack OAuth cannot start", async () => {
		mockSlackConnection("2026-09-20T08:00:00Z");
		server.use(
			http.post("*/workspaces/:workspaceSlug/connections", () =>
				HttpResponse.json(
					{ status: 503, title: "Service Unavailable", detail: "Slack is not configured." },
					{ status: 503 },
				),
			),
		);
		renderRouteAt("/w/acme/admin/integrations/slack");
		const user = userEvent.setup();
		await user.click(
			await screen.findByRole("button", { name: "Reconnect Slack" }, ROUTE_RENDER_WAIT),
		);

		await screen.findByText("Slack is not configured.");
		await waitFor(() =>
			expect(
				screen.getByRole<HTMLButtonElement>("button", { name: "Reconnect Slack" }).disabled,
			).toBe(false),
		);
		screen.getByText("Token unreadable");
	});

	it("shows the bot token as connected again once it reads", async () => {
		mockSlackConnection();
		renderRouteAt("/w/acme/admin/integrations/slack");

		await screen.findByText("Connected", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("Token unreadable")).toBeNull();
		screen.getByText(/can post in Slack/iu);
	});
});

describe("Outline connection drafts", () => {
	it("does not carry a server URL or token into another workspace", async () => {
		mockConnection("GITHUB");
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme"), workspaceListItem("other")]),
			),
			http.get("*/workspaces/:workspaceSlug/connections", () => HttpResponse.json([])),
		);
		const { router } = renderRouteAtWithRouter("/w/acme/admin/integrations/outline");
		const input = await screen.findByLabelText("API token", undefined, ROUTE_RENDER_WAIT);
		const user = userEvent.setup();
		await user.type(screen.getByLabelText("Server URL"), "https://outline.acme.test");
		await user.type(input, "private-workspace-token");

		await act(async () =>
			router.navigate({
				to: "/w/$workspaceSlug/admin/integrations/outline",
				params: { workspaceSlug: "other" },
			}),
		);

		await waitFor(() => {
			expect(screen.getByLabelText("API token")).toHaveProperty("value", "");
			expect(screen.getByLabelText("Server URL")).toHaveProperty("value", "");
		});
	});
});

describe("integration job history", () => {
	it.each([
		{ integration: "scm", kind: "GITHUB" },
		{ integration: "slack", kind: "SLACK" },
		{ integration: "outline", kind: "OUTLINE" },
	] as const)(
		"resets $integration pagination without showing the previous workspace's jobs",
		async ({ integration, kind }) => {
			const { entry } = mockConnection("GITHUB");
			const response = deferred();
			const requestedPages: (string | null)[] = [];
			const job = {
				id: 1,
				createdAt: "2026-09-07T12:00:00Z",
				status: "SUCCEEDED",
				type: "RECONCILIATION",
				trigger: "MANUAL",
				cancelRequested: false,
				itemsProcessed: 123_456,
			} satisfies Wire<SyncJob>;
			server.use(
				http.get("*/workspaces", () =>
					HttpResponse.json([workspaceListItem("acme"), workspaceListItem("other")]),
				),
				http.get("*/workspaces/:workspaceSlug/connections/catalog", () =>
					HttpResponse.json([{ ...entry, kind }]),
				),
				http.get("*/workspaces/:workspaceSlug/connections", () =>
					HttpResponse.json([{ id: 7, kind: "OUTLINE", state: "SUSPENDED" }]),
				),
				http.get("*/workspaces/other/connections/7/sync/jobs", async ({ request }) => {
					requestedPages.push(new URL(request.url).searchParams.get("page"));
					await response.promise;
					return HttpResponse.json({ content: [], totalPages: 0 });
				}),
				http.get("*/workspaces/acme/connections/7/sync/jobs", () =>
					HttpResponse.json({ content: [job], totalPages: 2 }),
				),
			);
			const { router } = renderRouteAtWithRouter(`/w/acme/admin/integrations/${integration}`);
			await screen.findByText("123,456", undefined, ROUTE_RENDER_WAIT);
			await userEvent.setup().click(screen.getByRole("button", { name: "Go to next page" }));
			try {
				await act(async () =>
					router.navigate({ to: `/w/other/admin/integrations/${integration}` }),
				);
				await waitFor(() => expect(requestedPages).toStrictEqual(["0"]));
				expect(screen.queryByText("123,456")).toBeNull();
			} finally {
				response.resolve();
			}
			await screen.findByText("No sync jobs yet");
		},
	);
});

describe("canceled sync recovery on the integration overview", () => {
	const stopping: Wire<SyncJob> = {
		id: 91,
		type: "RECONCILIATION",
		status: "RUNNING",
		trigger: "MANUAL",
		cancelRequested: true,
		createdAt: "2026-09-01T00:00:00Z",
	};

	it.each([
		{ returned: stopping, httpStatus: 200, message: "GitLab: Still stopping; no new sync started" },
		{
			returned: { ...stopping, id: 92, status: "PENDING", cancelRequested: false },
			httpStatus: 202,
			message: "GitLab: Sync queued",
		},
		{
			returned: { ...stopping, id: 93, cancelRequested: false },
			httpStatus: 200,
			message: "GitLab: Sync in progress",
		},
	] satisfies { returned: Wire<SyncJob>; httpStatus: number; message: string }[])(
		"checks the server and describes its actual response: $message",
		async ({ returned, message, httpStatus }) => {
			const { entry } = mockConnection("GITLAB");
			entry.credentialsUnreadableSince = undefined;
			const requests: unknown[] = [];
			const response = deferred();
			const announced = vi.spyOn(toast, "message");
			server.use(
				http.get("*/workspaces/acme/connections/7/sync", () =>
					HttpResponse.json({
						connectionId: 7,
						connectionState: "ACTIVE",
						kind: "GITLAB",
						health: "HEALTHY",
						resourceCounts: { total: 1, errored: 0, pending: 0, stale: 0 },
						backfillSupported: true,
						activeJob: stopping,
					} satisfies Wire<ConnectionSyncStatus>),
				),
				http.post("*/workspaces/acme/connections/7/sync/jobs", async ({ request }) => {
					requests.push(await request.json());
					await response.promise;
					return HttpResponse.json(returned, { status: httpStatus });
				}),
			);
			try {
				renderRouteAt("/w/acme/admin/integrations");
				const user = userEvent.setup();
				await user.click(
					await screen.findByRole("button", { name: "Check and retry sync" }, ROUTE_RENDER_WAIT),
				);
				await waitFor(() => expect(requests).toStrictEqual([{ type: "RECONCILIATION" }]));
				expect(screen.getByRole("button", { name: "Checking…" })).toHaveProperty("disabled", true);
				response.resolve();
				await waitFor(() => expect(announced).toHaveBeenCalledWith(message));
			} finally {
				response.resolve();
				announced.mockRestore();
			}
		},
	);

	it.each(["BACKFILL", "INITIAL"] as const)(
		"does not replay a stopping %s as a reconciliation",
		async (type) => {
			const { entry } = mockConnection("GITLAB");
			entry.credentialsUnreadableSince = undefined;
			server.use(
				http.get("*/workspaces/acme/connections/7/sync", () =>
					HttpResponse.json({
						connectionId: 7,
						connectionState: "ACTIVE",
						kind: "GITLAB",
						health: "HEALTHY",
						resourceCounts: { total: 1, errored: 0, pending: 0, stale: 0 },
						backfillSupported: true,
						activeJob: { ...stopping, type },
					} satisfies Wire<ConnectionSyncStatus>),
				),
			);
			renderRouteAt("/w/acme/admin/integrations");
			const stoppingButton = await screen.findByRole(
				"button",
				{ name: "Stopping…" },
				ROUTE_RENDER_WAIT,
			);
			expect(stoppingButton).toHaveProperty("disabled", true);
			expect(screen.queryByRole("button", { name: /check and retry/iu })).toBeNull();
		},
	);
});

/** The answer every sync resource and job read gets until the test chooses another. */
type SyncReads =
	| { status: 200; resources: Wire<SyncResourceState>[]; jobs: Wire<SyncJob>[] }
	| { status: 403 | 503 };

const SYNC_READ_FAILURE = {
	403: "You are not allowed to read this connection.",
	503: "The sync state is temporarily unavailable.",
} as const;

/** Serves connection 7's resources and jobs, counting each read. A held answer waits for `hold`. */
function serveSyncReads(initial: SyncReads) {
	const requests = { resources: 0, jobs: 0 };
	const current = { reads: initial, hold: Promise.resolve() };
	const failed = (status: 403 | 503) =>
		HttpResponse.json(
			{ status, title: "Sync read failed", detail: SYNC_READ_FAILURE[status] },
			{ status },
		);
	server.use(
		http.get("*/workspaces/acme/connections/7/sync/resources", async () => {
			requests.resources += 1;
			await current.hold;
			const { reads } = current;
			return reads.status === 200 ? HttpResponse.json(reads.resources) : failed(reads.status);
		}),
		http.get("*/workspaces/acme/connections/7/sync/jobs", async () => {
			requests.jobs += 1;
			await current.hold;
			const { reads } = current;
			return reads.status === 200
				? HttpResponse.json({ content: reads.jobs, totalPages: 1 })
				: failed(reads.status);
		}),
	);
	return {
		requests,
		answer: (reads: SyncReads, hold = Promise.resolve()) => {
			current.reads = reads;
			current.hold = hold;
		},
	};
}

/** Reads the connection's resources and jobs again through their own keys, as the page's poll does. */
async function refreshSyncReads(queryClient: QueryClient) {
	const path = { workspaceSlug: "acme", connectionId: 7 };
	await act(async () => {
		await Promise.all([
			queryClient.invalidateQueries({ queryKey: listConnectionSyncResourcesQueryKey({ path }) }),
			queryClient.invalidateQueries({ queryKey: listConnectionSyncJobsQueryKey({ path }) }),
		]);
	});
}

async function pressEveryRetry(user: ReturnType<typeof userEvent.setup>) {
	for (const retry of screen.getAllByRole("button", { name: "Retry" })) {
		await user.click(retry);
	}
}

function syncedRepository(id: number, name: string, issues: number): Wire<SyncResourceState> {
	const syncedAt = "2026-10-15T08:00:00Z";
	return {
		id,
		externalId: name,
		name,
		type: "REPOSITORY",
		counts: [{ key: "issues", label: "Issues", count: issues, lastSyncedAt: syncedAt }],
		state: "SYNCED",
		lastSyncedAt: syncedAt,
		itemCount: issues,
	};
}

describe("sync evidence through a failed refresh", () => {
	const ledger = syncedRepository(1, "acme/platform/ledger", 3141);
	const billing = syncedRepository(2, "acme/platform/billing", 2718);
	const warned: Wire<SyncJob> = {
		id: 41,
		type: "RECONCILIATION",
		trigger: "SCHEDULED",
		status: "SUCCEEDED_WITH_WARNINGS",
		cancelRequested: false,
		createdAt: "2026-10-15T02:00:00Z",
		startedAt: "2026-10-15T02:00:00Z",
		finishedAt: "2026-10-15T02:03:20Z",
		itemsProcessed: 40,
		itemsTotal: 52,
		errorSummary: "12 issues skipped: their sub-issues did not sync",
		progress: {
			phase: "issues",
			currentStep: "Sub-issue sync stopped after 40 of 52 issues.",
			unitsCompleted: 40,
			unitsTotal: 52,
		},
	};
	const later: Wire<SyncJob> = {
		id: 42,
		type: "RECONCILIATION",
		trigger: "MANUAL",
		status: "SUCCEEDED",
		cancelRequested: false,
		createdAt: "2026-10-15T09:00:00Z",
		itemsProcessed: 77_777,
	};

	it("keeps the loaded repositories and an open job detail through a failed refresh, then retries", async () => {
		const { entry } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		const reads = serveSyncReads({ status: 200, resources: [ledger, billing], jobs: [warned] });
		const { queryClient } = renderRouteAtWithRouter("/w/acme/admin/integrations/scm");
		const user = userEvent.setup();
		const search = await screen.findByRole(
			"searchbox",
			{ name: "Search repositories" },
			ROUTE_RENDER_WAIT,
		);
		await user.type(search, "ledger");
		await user.click(await screen.findByRole("button", { name: "Show details for job 41" }));
		screen.getByText("Sub-issue sync stopped after 40 of 52 issues.");
		const alertsBefore = screen.queryAllByRole("alert").length;
		const requestsBefore = { ...reads.requests };

		reads.answer({ status: 503 });
		await refreshSyncReads(queryClient);
		await waitFor(() => expect(screen.queryAllByRole("alert")).toHaveLength(alertsBefore + 2));

		expect(reads.requests).toStrictEqual({
			resources: requestsBefore.resources + 1,
			jobs: requestsBefore.jobs + 1,
		});
		screen.getByText("acme/platform/ledger");
		expect(screen.queryByText("acme/platform/billing")).toBeNull();
		expect(screen.getByRole("searchbox", { name: "Search repositories" })).toHaveProperty(
			"value",
			"ledger",
		);
		screen.getByText("1 of 2 repositories");
		screen.getByText("Sub-issue sync stopped after 40 of 52 issues.");
		screen.getByRole("button", { name: "Show error for job 41" });
		screen.getByText("The repository sync state may be out of date");
		screen.getByText("The job history may be out of date");
		expect(screen.queryByText("We could not load the repository sync state")).toBeNull();
		expect(screen.queryByText("We could not load the job history")).toBeNull();

		const response = deferred();
		reads.answer(
			{
				status: 200,
				resources: [syncedRepository(1, "acme/platform/ledger", 3200), billing],
				jobs: [later, warned],
			},
			response.promise,
		);
		try {
			await pressEveryRetry(user);
			await waitFor(() =>
				expect(reads.requests).toStrictEqual({
					resources: requestsBefore.resources + 2,
					jobs: requestsBefore.jobs + 2,
				}),
			);
			const retrying = screen.getAllByRole("button", { name: "Retrying…" });
			expect(retrying.map((button) => button.hasAttribute("disabled"))).toStrictEqual([true, true]);
			screen.getByText("acme/platform/ledger");
			screen.getByText("Sub-issue sync stopped after 40 of 52 issues.");
		} finally {
			response.resolve();
		}

		await screen.findByText("77,777");
		screen.getByText("3,200");
		screen.getByText("Sub-issue sync stopped after 40 of 52 issues.");
		expect(screen.queryByText("The repository sync state may be out of date")).toBeNull();
		expect(screen.queryByText("The job history may be out of date")).toBeNull();
	});

	it("does not bring back refused sync evidence when a later read fails", async () => {
		const { entry } = mockConnection("GITLAB");
		entry.credentialsUnreadableSince = undefined;
		const reads = serveSyncReads({ status: 200, resources: [ledger], jobs: [warned] });
		const { queryClient } = renderRouteAtWithRouter("/w/acme/admin/integrations/scm");
		await screen.findByText("acme/platform/ledger", undefined, ROUTE_RENDER_WAIT);
		await screen.findByText("40/52");

		reads.answer({ status: 403 });
		await refreshSyncReads(queryClient);
		await screen.findByText("You do not have access to the repository sync state");
		screen.getByText("You do not have access to the job history");
		expect(screen.queryByText("acme/platform/ledger")).toBeNull();
		expect(screen.queryByText("40/52")).toBeNull();

		reads.answer({ status: 503 });
		await refreshSyncReads(queryClient);
		await screen.findByText("We could not load the repository sync state");
		screen.getByText("We could not load the job history");
		expect(screen.queryByText("acme/platform/ledger")).toBeNull();
		expect(screen.queryByText("40/52")).toBeNull();
		expect(screen.queryByText("The repository sync state may be out of date")).toBeNull();
		expect(screen.queryByText("The job history may be out of date")).toBeNull();

		reads.answer({
			status: 200,
			resources: [syncedRepository(3, "acme/platform/console", 1618)],
			jobs: [later],
		});
		await pressEveryRetry(userEvent.setup());
		await screen.findByText("acme/platform/console");
		await screen.findByText("77,777");
		expect(screen.queryByText("acme/platform/ledger")).toBeNull();
		expect(screen.queryByText("40/52")).toBeNull();
	});
});
