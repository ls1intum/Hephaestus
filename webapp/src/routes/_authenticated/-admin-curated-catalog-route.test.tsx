import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const status = (overrides: Record<string, unknown> = {}) => ({
	etag: "tag-1",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	...overrides,
});

const areaDefinition = {
	name: "Packaging work",
	description: "Make a change cheap to review",
	icon: "Package",
	color: "sky",
};
const practiceDefinition = {
	name: "Say what changed and why",
	artifactKind: "scm.pull_request",
	bindings: [mockPullRequestBinding],
	criteria: "Our own criteria",
	whyItMatters: "Reviewers need context",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
};

function mockCatalog(overrides: Record<string, unknown> = {}) {
	const catalog = {
		etag: "structure-1",
		customOrder: false,
		summary: {
			total: 2,
			updatesChangingDetection: 1,
			updatesChangingWordingOnly: 0,
			updatesChangingPresentation: 0,
			editedHere: 1,
			yours: 0,
			notOffered: 0,
			noLongerShipped: 0,
		},
		areas: [{ slug: "packaging", position: 0, definition: areaDefinition, status: status() }],
		practices: [
			{
				slug: "describe-what-and-why",
				name: practiceDefinition.name,
				artifactKind: "scm.pull_request",
				automatedReview: mockPullRequestPolicy.automatedReview,
				areaSlug: "packaging",
				position: 0,
				effectivelyOffered: true,
				status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
			},
		],
		...overrides,
	};
	server.use(http.get("*/admin/practice-catalog", () => HttpResponse.json(catalog)));
	server.use(
		http.get("*/admin/practice-catalog/definition-options", () =>
			HttpResponse.json(mockPracticeDefinitionOptions),
		),
	);
	return catalog;
}

describe("instance catalog routes", () => {
	it("leads pending updates to a focused review", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		await screen.findByText("1 Hephaestus change needs review", undefined, ROUTE_RENDER_WAIT);
		expect(screen.getByText("1 update would change review behavior")).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Review changes" }));
		expect(await screen.findByRole("button", { name: "Show all entries" })).toBeTruthy();
	});

	it("opens the practice editor from the catalog", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole("link", { name: "Create practice" }, ROUTE_RENDER_WAIT),
		);

		await screen.findByRole("heading", { name: "Create practice" }, ROUTE_RENDER_WAIT);
	});

	it("opens the area editor from the catalog", async () => {
		mockCatalog();
		renderRouteAt("/admin/catalog");

		fireEvent.click(await screen.findByRole("link", { name: "Create area" }, ROUTE_RENDER_WAIT));

		await screen.findByRole("heading", { name: "Create area" }, ROUTE_RENDER_WAIT);
	});

	it("reorders areas with the catalog tag", async () => {
		const secondArea = {
			slug: "delivery",
			position: 1,
			definition: { ...areaDefinition, name: "Delivery" },
			status: status(),
		};
		mockCatalog({
			areas: [
				{ slug: "packaging", position: 0, definition: areaDefinition, status: status() },
				secondArea,
			],
		});
		let ifMatch: string | null = null;
		let body: unknown;
		server.use(
			http.patch("*/admin/practice-catalog/areas/reorder", async ({ request }) => {
				ifMatch = request.headers.get("if-match");
				body = await request.json();
				return HttpResponse.json({
					etag: "structure-2",
					summary: {
						total: 2,
						updatesChangingDetection: 0,
						updatesChangingWordingOnly: 0,
						updatesChangingPresentation: 0,
						editedHere: 0,
						yours: 0,
						notOffered: 0,
						noLongerShipped: 0,
					},
					areas: [
						secondArea,
						{ slug: "packaging", position: 1, definition: areaDefinition, status: status() },
					],
					practices: [],
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: "More actions for Packaging work" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(await screen.findByRole("menuitem", { name: "Move down" }));

		await waitFor(() => expect(ifMatch).toBe('"structure-1"'));
		expect(body).toEqual({ orderedSlugs: ["delivery", "packaging"] });
	});

	it("restores the Hephaestus order with the catalog tag", async () => {
		const catalog = mockCatalog({ customOrder: true });
		let ifMatch: string | null = null;
		server.use(
			http.delete("*/admin/practice-catalog/order", ({ request }) => {
				ifMatch = request.headers.get("If-Match");
				return HttpResponse.json({ ...catalog, customOrder: false, etag: "structure-2" });
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole("button", { name: "Use Hephaestus order" }, ROUTE_RENDER_WAIT),
		);
		const dialog = await screen.findByRole("alertdialog");
		fireEvent.click(within(dialog).getByRole("button", { name: "Use Hephaestus order" }));

		await waitFor(() => expect(ifMatch).toBe('"structure-1"'));
	});

	it("restores the order and focus after a reorder conflict", async () => {
		const secondArea = {
			slug: "delivery",
			position: 1,
			definition: { ...areaDefinition, name: "Delivery" },
			status: status(),
		};
		mockCatalog({
			areas: [
				{ slug: "packaging", position: 0, definition: areaDefinition, status: status() },
				secondArea,
			],
		});
		server.use(
			http.patch("*/admin/practice-catalog/areas/reorder", async () => {
				await delay(100);
				return HttpResponse.json({ status: 412, title: "Stale" }, { status: 412 });
			}),
		);
		renderRouteAt("/admin/catalog");

		const packagingHeading = await screen.findByText(
			"Packaging work",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		const deliveryHeading = screen.getByText("Delivery");
		expect(
			packagingHeading.compareDocumentPosition(deliveryHeading) & Node.DOCUMENT_POSITION_FOLLOWING,
		).toBeTruthy();
		const trigger = await screen.findByRole(
			"button",
			{ name: "More actions for Packaging work" },
			ROUTE_RENDER_WAIT,
		);
		fireEvent.click(trigger);
		fireEvent.click(await screen.findByRole("menuitem", { name: "Move down" }));
		await waitFor(() =>
			expect(
				screen.getByText("Delivery").compareDocumentPosition(screen.getByText("Packaging work")) &
					Node.DOCUMENT_POSITION_FOLLOWING,
			).toBeTruthy(),
		);

		await screen.findByText(
			"The catalog order changed before this move was saved. We reloaded the latest order.",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(
			screen.getByText("Packaging work").compareDocumentPosition(screen.getByText("Delivery")) &
				Node.DOCUMENT_POSITION_FOLLOWING,
		).toBeTruthy();
		await waitFor(() => expect(document.activeElement).toBe(trigger));
	});

	it("loads the new area when a moved practice editor is reopened", async () => {
		const deliveryArea = {
			slug: "delivery",
			position: 1,
			definition: { ...areaDefinition, name: "Delivery" },
			status: status(),
		};
		const catalog = mockCatalog({
			areas: [
				{ slug: "packaging", position: 0, definition: areaDefinition, status: status() },
				deliveryArea,
			],
		});
		let latestArea = "packaging";
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: { ...practiceDefinition, areaSlug: latestArea },
					status: status(),
				}),
			),
			http.patch("*/admin/practice-catalog/practices/:slug/placement", () => {
				latestArea = "delivery";
				return HttpResponse.json({
					...catalog,
					etag: "structure-2",
					practices: catalog.practices.map((practice) => ({
						...practice,
						areaSlug: "delivery",
					})),
				});
			}),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		fireEvent.click(await screen.findByRole("link", { name: "Cancel" }, ROUTE_RENDER_WAIT));
		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: `More actions for ${practiceDefinition.name}` },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(await screen.findByRole("menuitemradio", { name: "Delivery" }));
		await waitFor(() => expect(latestArea).toBe("delivery"));

		fireEvent.click(await screen.findByRole("link", { name: practiceDefinition.name }));
		expect(
			(await screen.findByRole("combobox", { name: "Practice area" }, ROUTE_RENDER_WAIT))
				.textContent,
		).toContain("Delivery");
	});

	it("explains which practices area exclusion affects", async () => {
		const catalog = mockCatalog();
		let ifMatch: string | null = null;
		server.use(
			http.patch("*/admin/practice-catalog/areas/:slug/status", ({ request }) => {
				ifMatch = request.headers.get("if-match");
				return HttpResponse.json({
					...catalog,
					etag: "structure-2",
					areas: catalog.areas.map((area) => ({
						...area,
						status: status({ offered: false }),
					})),
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"switch",
				{ name: "Include Packaging work in new workspaces" },
				ROUTE_RENDER_WAIT,
			),
		);
		const confirmation = screen.getByRole("alertdialog");
		expect(
			within(confirmation).getByText(/also excludes 1 currently included practice/),
		).toBeTruthy();
		expect(within(confirmation).getByText("Say what changed and why")).toBeTruthy();
		fireEvent.click(within(confirmation).getByRole("button", { name: "Exclude area" }));

		await waitFor(() => expect(ifMatch).toBe('"structure-1"'));
	});

	it("prevents a second catalog write while an area update is pending", async () => {
		const catalog = mockCatalog({
			customOrder: true,
			areas: [
				{ slug: "packaging", position: 0, definition: areaDefinition, status: status() },
				{
					slug: "delivery",
					position: 1,
					definition: { ...areaDefinition, name: "Delivery" },
					status: status(),
				},
			],
		});
		server.use(
			http.patch("*/admin/practice-catalog/areas/:slug/status", async () => {
				await delay(500);
				return HttpResponse.json({
					...catalog,
					etag: "structure-2",
					areas: catalog.areas.map((area) => ({
						...area,
						status: status({ offered: false }),
					})),
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"switch",
				{ name: "Include Packaging work in new workspaces" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Exclude area" }));

		await waitFor(() =>
			expect(
				screen
					.getByRole("switch", { name: "Include Delivery in new workspaces" })
					.getAttribute("aria-disabled"),
			).toBe("true"),
		);
		expect(
			screen.getByRole("button", { name: "Use Hephaestus order" }).hasAttribute("disabled"),
		).toBe(true);
		expect(screen.getByRole("button", { name: "Create area" }).hasAttribute("disabled")).toBe(true);
		expect(screen.getByRole("button", { name: "Create practice" }).hasAttribute("disabled")).toBe(
			true,
		);
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
					status: status({ offered: false }),
				});
			}),
		);
		renderRouteAt("/admin/catalog");

		fireEvent.click(
			await screen.findByRole(
				"switch",
				{ name: "Include Say what changed and why in new workspaces" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Exclude practice" }));

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
		expect(body).toEqual({ status: "RETIRED" });
	});

	it("uses the status response tag when an inactive editor is reopened", async () => {
		mockCatalog();
		let latestTag = "tag-1";
		let updateIfMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ etag: latestTag }),
				}),
			),
			http.patch("*/admin/practice-catalog/practices/:slug/status", () => {
				latestTag = "tag-2";
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ etag: latestTag, offered: false }),
				});
			}),
			http.put("*/admin/practice-catalog/practices/:slug", ({ request }) => {
				updateIfMatch = request.headers.get("if-match");
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: { ...practiceDefinition, name: "Updated name" },
					status: status({ etag: "tag-3", offered: false }),
				});
			}),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		fireEvent.click(await screen.findByRole("link", { name: "Cancel" }, ROUTE_RENDER_WAIT));
		fireEvent.click(
			await screen.findByRole(
				"switch",
				{ name: "Include Say what changed and why in new workspaces" },
				ROUTE_RENDER_WAIT,
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Exclude practice" }));
		await waitFor(() => expect(latestTag).toBe("tag-2"));

		fireEvent.click(await screen.findByRole("link", { name: practiceDefinition.name }));
		const name = await screen.findByRole("textbox", { name: /Name/ }, ROUTE_RENDER_WAIT);
		fireEvent.change(name, { target: { value: "Updated name" } });
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		await waitFor(() => expect(updateIfMatch).toBe('"tag-2"'));
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
			await screen.findByRole("button", { name: "Review Hephaestus update" }, ROUTE_RENDER_WAIT),
		);

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
			await screen.findByRole("button", { name: "Apply Hephaestus update" }, ROUTE_RENDER_WAIT),
		);
		fireEvent.click(
			within(screen.getByRole("alertdialog", { name: "Apply Hephaestus update?" })).getByRole(
				"button",
				{ name: "Apply Hephaestus update" },
			),
		);

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
	});

	it("lets a waiting update be declined, not only taken", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					shipped: { ...practiceDefinition, criteria: "The definition Hephaestus ships now" },
					status: status({ state: "UPDATE_WAITING", changeKind: "DETECTION" }),
				}),
			),
			http.put(
				"*/admin/practice-catalog/practices/:slug/override/acknowledgement",
				({ request }) => {
					ifMatch = request.headers.get("if-match");
					return HttpResponse.json({
						slug: "describe-what-and-why",
						definition: practiceDefinition,
						status: status({ state: "EDITED_HERE", etag: "tag-2" }),
					});
				},
			),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");
		const name = await screen.findByRole("textbox", { name: /Name/ }, ROUTE_RENDER_WAIT);
		fireEvent.change(name, { target: { value: "Unsaved draft name" } });

		fireEvent.click(
			await screen.findByRole("button", { name: "Keep saved version" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
		await screen.findByText("Customized on this instance", undefined, ROUTE_RENDER_WAIT);
		expect(name).toHaveProperty("value", "Unsaved draft name");
	});

	it("keeps a removed default as a custom practice", async () => {
		mockCatalog();
		let ifMatch: string | null = null;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ state: "NO_LONGER_SHIPPED" }),
				}),
			),
			http.put(
				"*/admin/practice-catalog/practices/:slug/override/acknowledgement",
				({ request }) => {
					ifMatch = request.headers.get("if-match");
					return HttpResponse.json({
						slug: "describe-what-and-why",
						definition: practiceDefinition,
						status: status({ state: "YOURS", etag: "tag-2" }),
					});
				},
			),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Keep saved version as custom" },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() => expect(ifMatch).toBe('"tag-1"'));
		await screen.findByText("No Hephaestus default", undefined, ROUTE_RENDER_WAIT);
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
		await screen.findByText("This practice changed while you were editing");

		fireEvent.click(screen.getByRole("button", { name: "Continue with my draft" }));
		await waitFor(() =>
			expect((screen.getByRole("textbox", { name: /Name/ }) as HTMLInputElement).value).toBe(
				"My unsaved draft",
			),
		);
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		await waitFor(() => expect(retriedIfMatch).toBe('"tag-2"'));
	});

	it.each([
		["unchanged", false, mockPullRequestPolicy, mockPullRequestBinding.signals],
		[
			"changed",
			true,
			mockPracticeDefinitionOptions.workTypes[2].recommendedPolicy,
			["chat.conversation_thread.settled"],
		],
	] as const)("%s artifact sends the visible review rule", async (_label, changeArtifact, expectedPolicy, expectedSignals) => {
		mockCatalog();
		let requestBody: Record<string, unknown> | undefined;
		server.use(
			http.get("*/admin/practice-catalog/practices/:slug", () =>
				HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status(),
				}),
			),
			http.put("*/admin/practice-catalog/practices/:slug", async ({ request }) => {
				requestBody = (await request.json()) as Record<string, unknown>;
				return HttpResponse.json({
					slug: "describe-what-and-why",
					definition: practiceDefinition,
					status: status({ etag: "tag-2" }),
				});
			}),
		);
		renderRouteAt("/admin/catalog/practices/describe-what-and-why");

		await screen.findByRole("button", { name: "Save changes" }, ROUTE_RENDER_WAIT);
		if (changeArtifact) {
			const user = userEvent.setup();
			await user.click(screen.getByRole("radio", { name: /Conversation/ }));
		}
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		await waitFor(() => expect(requestBody).toBeDefined());
		expect(requestBody?.automatedReviewPolicy).toEqual(expectedPolicy);
		// The kind of work is read off the signals, so switching it has to rewrite the occasions
		// rather than send a kind alongside bindings that still name the old one.
		expect(
			(requestBody?.bindings as Array<{ signals: string[] }>).map((binding) => binding.signals),
		).toEqual([[...expectedSignals]]);
	});
});
