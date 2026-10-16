import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { ROUTER_SEARCH } from "@/lib/router-search";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";
import { deferred } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

import {
	adminHandlers,
	failingUntilRecovered,
	rowOf,
	RUNNING_COUNTS,
	SCAN_ID,
	scanPage,
	wireScan,
} from "./-scan-server";

// Route mounting loads the admin layout and lazy modules; allow time for the first render.
vi.setConfig({ testTimeout: 15_000 });

const LIST = "/w/acme/admin/practices/scans";

describe("scans list route", () => {
	it("shows a table skeleton while the first page loads, then the scans", async () => {
		const held = deferred();
		server.use(
			...adminHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/scans", async () => {
				await held.promise;
				return HttpResponse.json(scanPage([wireScan()]));
			}),
		);

		renderRouteAtWithRouter(LIST);

		const table = await screen.findByRole(
			"table",
			{ name: "Scans, newest first" },
			ROUTE_RENDER_WAIT,
		);
		expect(table.getAttribute("aria-busy")).toBe("true");
		expect(within(table).queryByRole("link")).toBeNull();
		expect(within(table).queryByText("0")).toBeNull();

		held.resolve();
		await within(table).findByRole("link", { name: "ls1intum/hephaestus" }, ROUTE_RENDER_WAIT);
		expect(table.getAttribute("aria-busy")).toBe("false");
	});

	it("shows a real scan row with its version, requester, status and counts", async () => {
		const requests: URL[] = [];
		server.use(
			...adminHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/scans", ({ request }) => {
				requests.push(new URL(request.url));
				return HttpResponse.json(
					scanPage([
						wireScan({ status: "RUNNING", completedAt: null, counts: RUNNING_COUNTS }),
						wireScan({
							id: "a0000000-0000-4000-8000-000000000005",
							status: "CANCELLED",
							requestedBy: null,
							origin: "SCHEDULE",
						}),
					]),
				);
			}),
		);

		renderRouteAtWithRouter(LIST);

		const table = await screen.findByRole(
			"table",
			{ name: "Scans, newest first" },
			ROUTE_RENDER_WAIT,
		);
		const [running, cancelled] = await within(table).findAllByRole(
			"link",
			{ name: "ls1intum/hephaestus" },
			ROUTE_RENDER_WAIT,
		);
		expect(running?.getAttribute("href")).toBe(`${LIST}/${SCAN_ID}`);
		const runningRow = rowOf(running);
		const cancelledRow = rowOf(cancelled);
		const runningCells = within(runningRow)
			.getAllByRole("cell")
			.map((cell) => cell.textContent);
		expect(runningCells.slice(1, 7)).toStrictEqual([
			"main · 4f2a9c1",
			"Ada Lovelace",
			"Running",
			"2",
			"1",
			"0",
		]);
		within(cancelledRow).getByText("Scheduled");
		within(cancelledRow).getByText("Cancelled");
		within(cancelledRow).getByText("4 of 6");
		expect(requests.at(-1)?.searchParams.get("size")).toBe("20");
	});

	it("keeps an error and its Retry in place of the table, then loads on retry", async () => {
		const list = failingUntilRecovered(scanPage([wireScan()]));
		server.use(
			...adminHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/scans", list.respond),
		);

		renderRouteAtWithRouter(LIST);

		await screen.findByText("We could not load scans", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("table")).toBeNull();
		expect(screen.queryByText("No scans yet")).toBeNull();

		list.recover();
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));
		await screen.findByRole("link", { name: "ls1intum/hephaestus" }, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("We could not load scans")).toBeNull();
	});

	it("points to repository schedules when there is no scan", async () => {
		server.use(
			...adminHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/scans", () =>
				HttpResponse.json(scanPage([])),
			),
		);

		renderRouteAtWithRouter(LIST);

		const title = await screen.findByRole("heading", { name: "No scans yet" }, ROUTE_RENDER_WAIT);
		const empty = title.closest("[data-slot=empty]");
		expect(empty?.textContent).toContain(
			"No scans yetScans appear here after a repository schedule starts one.",
		);
		expect(screen.queryByRole("link", { name: "Start scan" })).toBeNull();
		expect(screen.getByRole("link", { name: "Open review settings" }).getAttribute("href")).toBe(
			"/w/acme/admin/practices/review",
		);
	});
});

describe("scan routes under the admin gate", () => {
	it.each([LIST, `${LIST}/${SCAN_ID}`])("redirects a MEMBER away from %s", async (url) => {
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
			),
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json(ownMembership("MEMBER")),
			),
		);
		const router = createRouter({
			...ROUTER_SEARCH,
			routeTree,
			history: createMemoryHistory({ initialEntries: [url] }),
			context: { queryClient: new QueryClient(), auth: undefined },
		});
		await router.load();
		await waitFor(() => expect(router.state.location.pathname).toBe("/w/acme/practice-profile"));
	});
});
