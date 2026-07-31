import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const practice = {
	id: 1,
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	artifactType: "PULL_REQUEST",
	areaSlug: "communication",
	revisionNumber: 3,
	revisionCreatedAt: "2026-07-30T12:00:00Z",
	status: "AVAILABLE",
	updatedAt: "2026-07-30T12:00:00Z",
	version: 7,
	sourceKind: "BUNDLED",
	syncStatus: "SYNCED",
	latestBundledCatalogRevision: 3,
};
const practiceDetail = {
	...practice,
	triggerEvents: ["PullRequestCreated"],
	criteria: "Assess whether the description explains the change.",
	whyItMatters: "Reviewers need context.",
	whatGoodLooksLike: "The description explains what changed and why.",
};

function mockCatalog() {
	server.use(
		http.get("*/admin/practice-catalog/areas", () =>
			HttpResponse.json([{ slug: "communication", name: "Communication", displayOrder: 0 }]),
		),
		http.get("*/admin/practice-catalog/practices", () => HttpResponse.json([practice])),
	);
}

describe("instance curated catalog routes", () => {
	it("opens the create page from the catalog", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole("button", { name: "Create practice" }, ROUTE_RENDER_WAIT),
		);

		await screen.findByRole("heading", { name: "Create curated practice" }, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("heading", { name: "Curated practice catalog" })).toBeNull();
	});

	it("sends the current strong ETag when retiring a practice", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		let requestedStatus: unknown;
		server.use(
			http.patch("*/admin/practice-catalog/practices/:slug/status", async ({ request }) => {
				ifMatch = request.headers.get("if-match");
				requestedStatus = await request.json();
				return HttpResponse.json({ ...practice, status: "RETIRED", version: 8 });
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Retire Write a clear pull request description" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Retire practice" }));

		await waitFor(() => expect(ifMatch).toBe('"v7"'));
		expect(requestedStatus).toEqual({ status: "RETIRED" });
	});

	it("refreshes the version before retrying retirement after a conflict", async () => {
		let currentPractice = practice;
		let catalogGetCount = 0;
		let patchCount = 0;
		let retriedIfMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/areas", () =>
				HttpResponse.json([{ slug: "communication", name: "Communication", displayOrder: 0 }]),
			),
			http.get("*/admin/practice-catalog/practices", () => {
				catalogGetCount++;
				return HttpResponse.json([currentPractice]);
			}),
			http.patch("*/admin/practice-catalog/practices/:slug/status", ({ request }) => {
				patchCount++;
				if (patchCount === 1) {
					currentPractice = { ...practice, version: 8 };
					return HttpResponse.json(
						{ status: 412, title: "Curated practice changed" },
						{ status: 412 },
					);
				}
				retriedIfMatch = request.headers.get("if-match");
				return HttpResponse.json({ ...currentPractice, status: "RETIRED", version: 9 });
			}),
		);
		renderRouteAt("/admin/catalog");

		const retire = await screen.findByRole(
			"button",
			{ name: "Retire Write a clear pull request description" },
			ROUTE_RENDER_WAIT,
		);
		fireEvent.click(retire);
		fireEvent.click(screen.getByRole("button", { name: "Retire practice" }));
		await waitFor(() => expect(patchCount).toBe(1));
		await waitFor(() => expect(catalogGetCount).toBeGreaterThan(1));
		await waitFor(() => expect((retire as HTMLButtonElement).disabled).toBe(false));

		fireEvent.click(retire);
		fireEvent.click(screen.getByRole("button", { name: "Retire practice" }));

		await waitFor(() => expect(retriedIfMatch).toBe('"v8"'));
	});

	it("preserves the draft and refreshes the version after an edit conflict", async () => {
		let latest = false;
		let updateCount = 0;
		let retriedIfMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/areas", () =>
				HttpResponse.json([{ slug: "communication", name: "Communication", displayOrder: 0 }]),
			),
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json(
					latest ? { ...practiceDetail, revisionNumber: 4, version: 8 } : practiceDetail,
				),
			),
			http.put("*/admin/practice-catalog/practices/:slug", ({ request }) => {
				updateCount++;
				if (updateCount === 1) {
					latest = true;
					return HttpResponse.json(
						{ status: 412, title: "Curated practice changed" },
						{ status: 412 },
					);
				}
				retriedIfMatch = request.headers.get("if-match");
				return HttpResponse.json({ ...practiceDetail, name: "My unsaved draft", version: 9 });
			}),
		);
		renderRouteAt("/admin/catalog/clear-pr-description");

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

		await waitFor(() => expect(retriedIfMatch).toBe('"v8"'));
	});

	it("confirms and sends the current ETag before using the Hephaestus version", async () => {
		const overridden = {
			...practiceDetail,
			syncStatus: "UPDATE_AVAILABLE",
			latestBundledCatalogRevision: 4,
		};
		let ifMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/areas", () =>
				HttpResponse.json([{ slug: "communication", name: "Communication", displayOrder: 0 }]),
			),
			http.get("*/admin/practice-catalog/practices/:slug", () => HttpResponse.json(overridden)),
			http.delete("*/admin/practice-catalog/practices/:slug/override", ({ request }) => {
				ifMatch = request.headers.get("if-match");
				return HttpResponse.json({
					...practiceDetail,
					name: "Hephaestus pull request descriptions",
					revisionNumber: 4,
					version: 8,
				});
			}),
		);
		renderRouteAt("/admin/catalog/clear-pr-description");

		fireEvent.click(
			await screen.findByRole("button", { name: "Use Hephaestus version" }, ROUTE_RENDER_WAIT),
		);
		const confirmation = screen.getByRole("alertdialog", {
			name: "Use the Hephaestus version?",
		});
		fireEvent.click(within(confirmation).getByRole("button", { name: "Use Hephaestus version" }));

		await waitFor(() => expect(ifMatch).toBe('"v7"'));
		await screen.findByDisplayValue(
			"Hephaestus pull request descriptions",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(screen.getByText("Hephaestus-managed practice")).toBeTruthy();
	});
});
