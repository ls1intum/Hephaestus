import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { listWorkspacesQueryKey } from "@/api/@tanstack/react-query.gen";
import { QUERY_STALE_TIME_MS } from "@/integrations/tanstack-query/root-provider";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// `router.load()` lazily imports each matched route's module, so a case pays its transform cost.
vi.setConfig({ testTimeout: 15_000 });

const DEEP_LINK = "/w/foreign/mentor/thread-1?message=stale";
const WORKSPACE_HOME = "/w/acme";

function listWorkspaces(...slugs: string[]) {
	server.use(
		http.get("*/workspaces", () => HttpResponse.json(slugs.map((slug) => workspaceListItem(slug)))),
	);
}

async function land(url: string, queryClient = new QueryClient()) {
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: [url] }),
		context: { queryClient, auth: undefined },
	});
	await router.load();
	return router.state.location.pathname;
}

describe("workspace route gate", () => {
	it("opens a workspace the account can reach", async () => {
		listWorkspaces("acme");
		expect(await land("/w/acme")).toBe(WORKSPACE_HOME);
	});

	it("returns an inaccessible workspace's deep link to an accessible workspace home", async () => {
		listWorkspaces("acme");
		expect(await land(DEEP_LINK)).toBe(WORKSPACE_HOME);
	});

	it("returns to the home page when no workspace is accessible", async () => {
		listWorkspaces();
		expect(await land(DEEP_LINK)).toBe("/");
	});

	it("keeps the route when the workspace list cannot be fetched", async () => {
		server.use(http.get("*/workspaces", () => HttpResponse.error()));
		expect(await land(DEEP_LINK)).toBe("/w/foreign/mentor/thread-1");
	});

	it("opens a just-created workspace the cache carries before the server lists it", async () => {
		listWorkspaces("acme");
		const queryClient = new QueryClient({
			defaultOptions: { queries: { staleTime: QUERY_STALE_TIME_MS } },
		});
		queryClient.setQueryData(listWorkspacesQueryKey(), [
			workspaceListItem("acme"),
			workspaceListItem("brand-new"),
		]);

		expect(await land("/w/brand-new", queryClient)).toBe("/w/brand-new");
	});
});
