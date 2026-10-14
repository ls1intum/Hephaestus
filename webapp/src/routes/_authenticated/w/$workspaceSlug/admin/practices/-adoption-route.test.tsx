import { fireEvent, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { getPracticeGuidanceOptions } from "@/api/@tanstack/react-query.gen";
import type { CatalogPracticePreview, CatalogPracticeSummary } from "@/api/types.gen";
import { definitionOf } from "@/components/admin/practice-editor/fixtures";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import type { Wire } from "@/lib/dates";
import { server } from "@/mocks/server";
import {
	ROUTE_RENDER_WAIT,
	renderRouteAt,
	renderRouteAtWithRouter,
	testQueryClient,
} from "@/test/router-harness";

import {
	practiceSetupHandlers,
	practicesHandlers,
	wireGuidance,
	wirePractice,
} from "./-practice-wire";

vi.setConfig({ testTimeout: 20_000 });

const definition = definitionOf({
	name: "Describe what changed and why",
	criteria: "Explain the change and why it is needed.",
});

const preview: Wire<CatalogPracticePreview> = {
	slug: "describe-what-and-why",
	kind: "scm.pull_request",
	availability: "AVAILABLE",
	etag: '"reviewed-plan"',
	initialFeedbackMode: "APPROVE_FIRST",
	offeredDigest: "sha256:describe-what-and-why",
	group: {
		slug: "review-ready-work",
		disposition: "CREATE_CATALOG_GROUP",
		definition: { name: "Review-ready work", description: null, icon: null, color: null },
	},
	definition,
};

function summary(
	slug: string,
	name: string,
	kind: string,
	availability: CatalogPracticeSummary["availability"],
	groupSlug: string | null = null,
): Wire<CatalogPracticeSummary> {
	return {
		slug,
		name,
		kind,
		availability,
		groupSlug,
		groupName: groupSlug === null ? null : "Review-ready work",
		whyItMatters: null,
		reviewedBy: { by: "HEPHAESTUS", humanReason: null, limits: [] },
	};
}

/**
 * The preview as it stands, then as it stands after someone else changed it — the shape a stale 409 is
 * about. A handler is where setup is allowed to branch; a test body is not.
 */
function planChangedAfterTheFirstRead() {
	let reads = 0;
	return http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () => {
		reads += 1;
		const changed = reads > 1;
		return HttpResponse.json({
			...preview,
			definition: {
				...definition,
				criteria: changed ? "Updated review rule that must be reviewed." : definition.criteria,
			},
			etag: changed ? '"updated-plan"' : preview.etag,
		});
	});
}

/** The preview once, and then nothing: the refresh a 409 asks for is itself allowed to fail. */
function planUnreadableAfterTheFirstRead() {
	let reads = 0;
	return http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () => {
		reads += 1;
		return reads === 1
			? HttpResponse.json(preview)
			: HttpResponse.json(
					{ title: "Service Unavailable", status: 503, detail: "Try again in a moment." },
					{ status: 503 },
				);
	});
}

/** A one-level stack survives the readable URL form as well as the encoded array form. */
const LIBRARY = "/w/acme/admin/practices?library=true";
const REVIEWING = `${LIBRARY}&detail=catalog-practice:${preview.slug}`;

const workspacePractice = wirePractice({
	slug: "already-mine",
	definition: definitionOf({ name: "Already mine" }),
});

describe("catalog adoption over practice setup", () => {
	beforeEach(() => {
		server.use(...practiceSetupHandlers());
	});

	it("shows every included practice in the catalog and distinguishes adoption states", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([
					summary(
						preview.slug,
						definition.name,
						"scm.pull_request",
						"AVAILABLE",
						"review-ready-work",
					),
					summary("already-there", "Already there", "scm.issue", "ADOPTED"),
					summary("issue-context", "Include enough issue context", "scm.issue", "SLUG_CONFLICT"),
				]),
			),
		);

		renderRouteAt(LIBRARY);

		await screen.findByRole("heading", { name: "Instance catalog" }, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("Available")).toBeNull();
		await screen.findByText("Name in use");
		screen.getByRole("link", { name: /Describe what changed and why/u });
		screen.getByRole("link", {
			name: /Include enough issue context, see why it cannot be added/u,
		});
	});

	it("reviews a practice over the catalog instead of leaving the page", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([
					summary(
						preview.slug,
						definition.name,
						"scm.pull_request",
						"AVAILABLE",
						"review-ready-work",
					),
				]),
			),
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(preview),
			),
		);

		const { router } = renderRouteAtWithRouter(LIBRARY);
		fireEvent.click(
			await screen.findByRole(
				"link",
				{ name: /Describe what changed and why/u },
				ROUTE_RENDER_WAIT,
			),
		);

		await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT);
		// The catalog is still the page; only the drawer stack changed.
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices");
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`catalog-practice:${preview.slug}`,
		]);
	});

	it("opens a workspace practice read-only over the tree instead of in the edit form", async () => {
		server.use(...practicesHandlers([workspacePractice]));

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices");
		fireEvent.click(await screen.findByRole("link", { name: "Already mine" }, ROUTE_RENDER_WAIT));

		// The definition is readable, and editing is a separate, explicit act: a level on top of it,
		// so leaving the form lands back on the panel it was opened from rather than on the bare tree.
		const edit = await screen.findByRole("link", { name: "Edit practice" }, ROUTE_RENDER_WAIT);
		expect(edit.getAttribute("href")).toBe(
			"/w/acme/admin/practices?detail=practice:already-mine&detail=practice-edit:already-mine",
		);
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices");
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			"practice:already-mine",
		]);
	});

	it("pins adoption to the reviewed ETag and returns to the catalog", async () => {
		const queryClient = testQueryClient();
		const guidanceOptions = {
			...getPracticeGuidanceOptions({
				path: { workspaceSlug: "acme", practiceSlug: preview.slug },
			}),
			staleTime: 30_000,
		};
		queryClient.setQueryData(guidanceOptions.queryKey, wireGuidance(preview.slug, null));
		const seenIfMatch = vi.fn();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([]),
			),
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(preview),
			),
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/guidance", () =>
				HttpResponse.json(wireGuidance(preview.slug, "Adopted guide")),
			),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", ({ request }) => {
				seenIfMatch(request.headers.get("If-Match"));
				return HttpResponse.json(wirePractice({ slug: preview.slug, definition }), {
					status: 201,
				});
			}),
		);

		const { router } = renderRouteAtWithRouter(REVIEWING, queryClient);
		fireEvent.click(await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT));

		await waitFor(() => expect(seenIfMatch).toHaveBeenCalledWith(preview.etag));
		// Adding closes the drawer rather than opening the practice form, so the next one is one click away.
		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices");
		expect(router.state.location.search.library).toBe(true);
		await expect(queryClient.query(guidanceOptions)).resolves.toMatchObject({
			guide: { markdown: "Adopted guide" },
		});
	});

	it("requires review again when the plan changed before the add", async () => {
		const seenIfMatch = vi.fn();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([]),
			),
			planChangedAfterTheFirstRead(),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", ({ request }) => {
				seenIfMatch(request.headers.get("If-Match"));
				return HttpResponse.json(
					{
						title: "Practice adoption preview changed",
						status: 409,
						detail: "The preview changed.",
					},
					{ status: 409 },
				);
			}),
		);

		renderRouteAt(REVIEWING);
		fireEvent.click(await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT));

		await waitFor(() => expect(seenIfMatch).toHaveBeenCalledWith(preview.etag));
		// `role="alert"` announces the change without pulling focus off the action.
		const changed = await screen.findByRole("alert");
		expect(changed.textContent).toContain("The catalog changed while you were reading");
		// The rule is what a stale 409 is about, so open the disclosure that holds it and check the panel
		// is showing the refetched one rather than the plan that was just rejected.
		fireEvent.click(await screen.findByRole("button", { name: "How it decides" }));
		await screen.findByText("Updated review rule that must be reviewed.");
	});

	it("does not claim the latest preview is shown when refreshing after a 409 fails", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([]),
			),
			planUnreadableAfterTheFirstRead(),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(
					{
						title: "Practice adoption preview changed",
						status: 409,
						detail: "The preview changed.",
					},
					{ status: 409 },
				),
			),
		);

		renderRouteAt(REVIEWING);
		fireEvent.click(await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT));

		await screen.findByText("We could not load the adoption preview", {}, ROUTE_RENDER_WAIT);
		expect(screen.queryByText("The catalog changed while you were reading")).toBeNull();
	});

	it("says a practice was already added and returns to the catalog", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([]),
			),
			// Someone else added it: the preview read after the refusal says so.
			http.get(
				"*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug",
				() => HttpResponse.json(preview),
				{ once: true },
			),
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json({ ...preview, availability: "ADOPTED" }),
			),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(
					{
						type: "about:blank",
						title: "Conflict",
						status: 409,
						detail: "A workspace practice already uses this slug.",
					},
					{ status: 409 },
				),
			),
		);

		const { router } = renderRouteAtWithRouter(REVIEWING);
		fireEvent.click(await screen.findByRole("button", { name: "Add practice" }, ROUTE_RENDER_WAIT));

		// Both halves: closing on its own is what a route that swallowed every failure would also do.
		await screen.findByText("This practice is already in the workspace", {}, ROUTE_RENDER_WAIT);
		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
			ROUTE_RENDER_WAIT,
		);
	});

	it("creates a practice from the editor level and lands back on the tree", async () => {
		const created = vi.fn();
		server.use(
			http.post("*/workspaces/:workspaceSlug/practices", async ({ request }) => {
				created(await request.json());
				return HttpResponse.json(workspacePractice, { status: 201 });
			}),
		);

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices?detail=practice-new:draft");
		fireEvent.change(await screen.findByRole("textbox", { name: /Name/u }, ROUTE_RENDER_WAIT), {
			target: { value: "Explain the change" },
		});
		fireEvent.change(screen.getByRole("textbox", { name: /What to look for/u }), {
			target: { value: "Check that the description says why." },
		});
		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));

		// One request: the kind and the whole definition, the group beside it and never inside it.
		await waitFor(() => expect(created).toHaveBeenCalledOnce(), ROUTE_RENDER_WAIT);
		expect(created.mock.lastCall?.[0]).toMatchObject({
			slug: "explain-the-change",
			kind: "scm.pull_request",
			groupSlug: null,
			position: null,
			definition: {
				name: "Explain the change",
				criteria: "Check that the description says why.",
			},
		});
		// The level goes; the tree it was opened over is what the reader lands on.
		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
			ROUTE_RENDER_WAIT,
		);
	});

	// Escape here, a press on the page in `DetailDrawerStack`'s stories: jsdom does not drive Base
	// UI's outside-press. Both reach the same `onClose`, and this is the half that also owns the
	// guard, so it is the half worth asserting against the real form.
	it("asks before Escape discards a draft, and keeps it when refused", async () => {
		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices?detail=practice-new:draft");
		fireEvent.change(await screen.findByRole("textbox", { name: /Name/u }, ROUTE_RENDER_WAIT), {
			target: { value: "A draft worth keeping" },
		});

		fireEvent.keyDown(document.body, { key: "Escape" });

		await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" }, ROUTE_RENDER_WAIT);
		fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());

		// The level is still open and still holds the draft: refusing has to be free.
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			"practice-new:draft",
		]);
		screen.getByDisplayValue("A draft worth keeping");
	});

	it("discards the draft and leaves when the reader says so", async () => {
		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices?detail=practice-new:draft");
		fireEvent.change(await screen.findByRole("textbox", { name: /Name/u }, ROUTE_RENDER_WAIT), {
			target: { value: "A draft worth losing" },
		});

		fireEvent.keyDown(document.body, { key: "Escape" });
		fireEvent.click(
			await screen.findByRole("button", { name: "Discard changes" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
			ROUTE_RENDER_WAIT,
		);
	});

	it("leaves a clean editor without asking anything", async () => {
		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices?detail=practice-new:draft");
		await screen.findByRole("textbox", { name: /Name/u }, ROUTE_RENDER_WAIT);

		fireEvent.keyDown(document.body, { key: "Escape" });

		await waitFor(
			() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
			ROUTE_RENDER_WAIT,
		);
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});
});
