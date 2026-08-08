import { QueryClientProvider } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { renderWithRouter, testQueryClient } from "@/test/router-harness";
import { tracedArtifacts } from "./story-mock-data";
import { TraceListPage } from "./TraceListPage";

/**
 * Not a story: the page links are built with `search={(previous) => …}`, where `previous` is the
 * *router's* search params rather than the component's prop, so proving they carry the filter takes
 * a router whose location has one — which the Storybook preview's shared router does not.
 */
describe("paging a filtered list", () => {
	function renderAtPage(page: number) {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/trace", () =>
				HttpResponse.json({
					content: tracedArtifacts.slice(0, 2),
					page: { number: page, size: 2, totalElements: 6, totalPages: 3 },
				}),
			),
		);
		return renderWithRouter(
			<QueryClientProvider client={testQueryClient()}>
				<TraceListPage
					workspaceSlug="demo"
					search={{ kind: "scm.issue", page: page || undefined }}
					onSearchChange={vi.fn()}
				/>
			</QueryClientProvider>,
			`/w/demo/reviews?kind=scm.issue${page ? `&page=${page}` : ""}`,
		);
	}

	it("keeps the filter while changing the page", async () => {
		await renderAtPage(0);

		expect((await screen.findByRole("link", { name: "Go to page 2" })).getAttribute("href")).toBe(
			"/w/demo/reviews?kind=scm.issue&page=1",
		);
	});

	it("leaves the first page out of the URL rather than writing page=0", async () => {
		await renderAtPage(1);

		// A `page=0` in a shared link is noise, and the schema's default already means the first page.
		expect((await screen.findByRole("link", { name: "Go to page 1" })).getAttribute("href")).toBe(
			"/w/demo/reviews?kind=scm.issue",
		);
	});
});
