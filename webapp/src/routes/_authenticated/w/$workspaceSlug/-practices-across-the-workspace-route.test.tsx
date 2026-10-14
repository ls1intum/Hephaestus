import { focusManager } from "@tanstack/react-query";
import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import {
	LOADING_STATUS,
	SLOW_LOAD_SHORT,
} from "@/components/practices-across-the-workspace/across-workspace-copy";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { ACROSS_WORKSPACE } from "@/stories/practices-across-the-workspace-story-data";
import { deferred, sleep } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: ROUTE_RENDER_WAIT.timeout });

const PAGE = "/w/acme/practices-across-the-workspace";
const PROFILE = "/w/acme/practice-profile";
const GROUP_OPEN = `${PAGE}?detail=practice-group:review-ready-work`;

/** Every request the page made, so a test can count the reads. */
let requests: { url: URL }[] = [];

beforeEach(() => {
	requests = [];
	server.events.on("request:start", ({ request }) => {
		requests.push({ url: new URL(request.url) });
	});
	server.use(
		http.get("*/workspaces", () =>
			HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
		),
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("MEMBER")),
		),
		http.get("*/workspaces/:workspaceSlug/practices/workspace-overview", () =>
			HttpResponse.json(ACROSS_WORKSPACE),
		),
	);
});

afterEach(() => {
	server.events.removeAllListeners();
});

const overviewReads = () =>
	requests.filter((request) => request.url.pathname.endsWith("/practices/workspace-overview"));

/** The reader's own profile reads, which this page never makes: the profile makes them. */
const profileReads = () =>
	requests.filter(({ url }) =>
		[
			"/practice-groups",
			"/practice-groups/standings",
			"/practices/standings",
			"/practice-profile/overview",
			"/practices/feedback/practice-page",
		].some((path) => url.pathname.endsWith(path)),
	);

async function renderPage(path = PAGE) {
	const { router } = renderRouteAtWithRouter(path);
	await screen.findByRole(
		"heading",
		{ level: 1, name: "Practices across the workspace" },
		ROUTE_RENDER_WAIT,
	);
	await screen.findByRole(
		"button",
		{ name: "Open group Packaging work for review" },
		ROUTE_RENDER_WAIT,
	);
	return router;
}

describe("Practices across the workspace", () => {
	it("lists each group with its practices and results from one read, and none of the reader's own", async () => {
		await renderPage();
		const table = within(screen.getByRole("table", { name: "All practice groups" }));
		const packaging = table
			.getAllByRole("row")
			.find((row) => row.textContent.startsWith("Packaging work for review"));
		expect(packaging?.textContent).toContain("5 practices");
		expect(packaging?.textContent).toContain("Met 43");
		// A practice in no group has no row.
		expect(screen.queryByText("Sign your commits")).toBeNull();
		expect(overviewReads()).toHaveLength(1);
		expect(profileReads()).toStrictEqual([]);
	});

	it("opens a group over the page with each practice's developers and results", async () => {
		const router = await renderPage();
		await userEvent.click(
			screen.getByRole("button", { name: "Open group Packaging work for review" }),
		);

		await waitFor(() => {
			expect(router.state.location.search).toMatchObject({
				detail: "practice-group:review-ready-work",
			});
		});
		expect(router.state.location.pathname).toBe(PAGE);
		const level = within(
			await screen.findByRole("dialog", { name: "Packaging work for review" }, ROUTE_RENDER_WAIT),
		);
		await level.findByRole(
			"table",
			{ name: "Practices of Packaging work for review" },
			ROUTE_RENDER_WAIT,
		);
		const diff = level
			.getAllByRole("row")
			.find((row) => row.textContent.startsWith("Keep the diff reviewable in one sitting"));
		expect(diff?.textContent).toContain("21 developers");
		expect(diff?.textContent).toContain("Not met 9");
		// The level asks for nothing of its own, and none of the reader's reads: the page's one read
		// carries every practice.
		expect(overviewReads()).toHaveLength(1);
		expect(profileReads()).toStrictEqual([]);

		router.history.back();
		await waitFor(() => {
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]);
		});
	});

	it("goes from the group to the same group in the reader's profile", async () => {
		const { router } = renderRouteAtWithRouter(GROUP_OPEN);
		await userEvent.click(
			await screen.findByRole(
				"button",
				{ name: "See your own standing: Packaging work for review" },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() => {
			expect(router.state.location.pathname).toBe(PROFILE);
		});
		expect(router.state.location.search).toMatchObject({
			detail: "practice-group:review-ready-work",
		});
	});

	it("goes from a practice to the same practice in the reader's profile, over its group", async () => {
		const { router } = renderRouteAtWithRouter(GROUP_OPEN);
		await userEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Open in your Practice profile Scope the change to one concern" },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() => {
			expect(router.state.location.pathname).toBe(PROFILE);
		});
		expect(router.state.location.search).toMatchObject({
			detail: ["practice-group:review-ready-work", "practice:scope-to-one-concern"],
		});
	});

	it("drops a level kind this page does not open", async () => {
		const { router } = renderRouteAtWithRouter(
			`${PAGE}?detail=practice-group:review-ready-work&detail=own-group:review-ready-work&detail=practice:scope-to-one-concern`,
		);
		await screen.findByRole("dialog", { name: "Packaging work for review" }, ROUTE_RENDER_WAIT);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			"practice-group:review-ready-work",
		]);
		expect(profileReads()).toStrictEqual([]);
	});

	it("says it could not load the practice groups when the overview fails, in the open level too", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/workspace-overview", () =>
				HttpResponse.json({ title: "Internal Server Error" }, { status: 500 }),
			),
		);
		const { router } = renderRouteAtWithRouter(GROUP_OPEN);

		const level = within(
			await screen.findByRole("dialog", { name: "Practice group" }, ROUTE_RENDER_WAIT),
		);
		await level.findByText("We could not load the practice groups", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("table", { name: "All practice groups" })).toBeNull();
		// The address still names the group, so the level stays open over the failure.
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			"practice-group:review-ready-work",
		]);
	});

	it("reads the overview once and not again on a refocus", async () => {
		await renderPage();
		act(() => {
			focusManager.setFocused(false);
			focusManager.setFocused(true);
		});
		await sleep(100);

		expect(overviewReads()).toHaveLength(1);
		focusManager.setFocused(undefined);
	});

	it("says once that it counts, then that the counts take long", async () => {
		const counted = deferred();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/workspace-overview", async () => {
				await counted.promise;
				return HttpResponse.json(ACROSS_WORKSPACE);
			}),
		);
		renderRouteAtWithRouter(PAGE);

		// One message in the live region, which the slow wording does not replace.
		const status = await screen.findByText(
			LOADING_STATUS,
			{ selector: "[role=status]" },
			ROUTE_RENDER_WAIT,
		);
		await screen.findByText(SLOW_LOAD_SHORT, undefined, ROUTE_RENDER_WAIT);
		expect(status.textContent).toBe(LOADING_STATUS);
		// The rest of the note waits behind the (i) beside it.
		screen.getByRole("button", { name: "About the wait" });

		counted.resolve();
		await screen.findByRole(
			"button",
			{ name: "Open group Packaging work for review" },
			ROUTE_RENDER_WAIT,
		);
		await waitFor(() => {
			expect(screen.queryByText(SLOW_LOAD_SHORT)).toBeNull();
			expect(screen.queryByText(LOADING_STATUS)).toBeNull();
			expect(screen.queryByRole("button", { name: "About the wait" })).toBeNull();
		});
	});
});
