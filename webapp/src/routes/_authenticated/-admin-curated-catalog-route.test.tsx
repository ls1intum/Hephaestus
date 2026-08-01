import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const status = (overrides: Record<string, unknown> = {}) => ({
	etag: "tag-1",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	retired: false,
	updatedAt: "2026-07-30T12:00:00Z",
	...overrides,
});

const areaDefinition = {
	name: "Packaging work",
	description: "Make a change cheap to review",
	displayOrder: 0,
	icon: "Package",
	color: "sky",
};
const practiceDefinition = {
	name: "Say what changed and why",
	artifactType: "PULL_REQUEST",
	triggerEvents: ["PullRequestCreated"],
	criteria: "Our own criteria",
	whyItMatters: "Reviewers need context",
};

function mockCatalog(overrides: Record<string, unknown> = {}) {
	server.use(
		http.get("*/admin/practice-catalog", () =>
			HttpResponse.json({
				summary: {
					total: 2,
					updatesChangingDetection: 1,
					updatesChangingWordingOnly: 0,
					editedHere: 1,
					yours: 0,
					retired: 0,
					noLongerShipped: 0,
				},
				areas: [{ slug: "packaging", definition: areaDefinition, status: status() }],
				practices: [
					{
						slug: "describe-what-and-why",
						name: practiceDefinition.name,
						artifactType: "PULL_REQUEST",
						areaSlug: "packaging",
						status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
					},
				],
				...overrides,
			}),
		),
	);
}

describe("instance catalog routes", () => {
	it("says how the catalog stands in one line", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		await screen.findByText(/2 entries follow Hephaestus by default/, undefined, ROUTE_RENDER_WAIT);
		expect(screen.getByText(/1 update waiting \(1 would change detection\)/)).toBeTruthy();
	});

	it("opens the practice editor from the catalog", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		fireEvent.click(await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT));

		await screen.findByRole("heading", { name: "Create curated practice" }, ROUTE_RENDER_WAIT);
	});

	it("opens the area editor from the catalog", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		fireEvent.click(await screen.findByRole("button", { name: "Add area" }, ROUTE_RENDER_WAIT));

		await screen.findByRole("heading", { name: "Add an area" }, ROUTE_RENDER_WAIT);
	});

	it("says what no longer offering an area would withhold before doing it", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		server.use(
			http.patch("*/admin/practice-catalog/areas/:slug/status", ({ request }) => {
				ifMatch = request.headers.get("if-match");
				return HttpResponse.json({
					slug: "packaging",
					definition: areaDefinition,
					status: status({ offered: false, retired: true }),
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole("button", { name: "Retire Packaging work" }, ROUTE_RENDER_WAIT),
		);
		const confirmation = screen.getByRole("alertdialog");
		expect(within(confirmation).getByText(/1 practice filed under it/)).toBeTruthy();
		fireEvent.click(within(confirmation).getByRole("button", { name: "Retire area" }));

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
	});

	it("sends the entry's tag when it stops offering a practice", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		let body: unknown;
		server.use(
			http.patch("*/admin/practice-catalog/practices/:slug/status", async ({ request }) => {
				ifMatch = request.headers.get("if-match");
				body = await request.json();
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ offered: false, retired: true }),
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Retire Say what changed and why" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Retire practice" }));

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
		expect(body).toEqual({ status: "RETIRED" });
	});

	it("shows the Hephaestus version before it is taken", async () => {
		mockCatalog();
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					shipped: { ...practiceDefinition, criteria: "The definition Hephaestus ships now" },
					status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
				}),
			),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		fireEvent.click(
			await screen.findByRole("button", { name: "Show the Hephaestus version" }, ROUTE_RENDER_WAIT),
		);

		// Accepting an update must never mean accepting text nobody can read.
		await screen.findByText("The definition Hephaestus ships now", undefined, ROUTE_RENDER_WAIT);
	});

	it("confirms and sends the entry's tag before using the Hephaestus version", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					shipped: { ...practiceDefinition, criteria: "Hephaestus criteria" },
					status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
				}),
			),
			http.delete("*/admin/practice-catalog/practices/:slug/override", ({ request }) => {
				ifMatch = request.headers.get("if-match");
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: { ...practiceDefinition, criteria: "Hephaestus criteria" },
					status: status(),
				});
			}),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		fireEvent.click(
			await screen.findByRole("button", { name: "Use the Hephaestus version" }, ROUTE_RENDER_WAIT),
		);
		fireEvent.click(
			within(screen.getByRole("alertdialog", { name: "Use the Hephaestus version?" })).getByRole(
				"button",
				{ name: "Use the Hephaestus version" },
			),
		);

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
	});

	it("preserves the draft and refreshes the tag after an edit conflict", async () => {
		mockCatalog();
		let latest = false;
		let updates = 0;
		let retriedIfMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ etag: latest ? "tag-2" : "tag-1" }),
				}),
			),
			http.put("*/admin/practice-catalog/practices/:slug", ({ request }) => {
				updates++;
				if (updates === 1) {
					latest = true;
					return HttpResponse.json({ status: 412, title: "Stale" }, { status: 412 });
				}
				retriedIfMatch = request.headers.get("if-match");
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: { ...practiceDefinition, name: "My unsaved draft" },
					status: status({ etag: "tag-3" }),
				});
			}),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		const name = await screen.findByRole("textbox", { name: /Name/ }, ROUTE_RENDER_WAIT);
		fireEvent.change(name, { target: { value: "My unsaved draft" } });
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		await screen.findByText("A newer version was saved while you were editing");

		fireEvent.click(screen.getByRole("button", { name: "Continue with this draft" }));
		await waitFor(() =>
			expect((screen.getByRole("textbox", { name: /Name/ }) as HTMLInputElement).value).toBe(
				"My unsaved draft",
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		await waitFor(() => expect(retriedIfMatch).toBe('"tag-2"'));
	});
});
