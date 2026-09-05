import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory, createRouter } from "@tanstack/react-router";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { consentIsPending } from "@/integrations/auth/guard";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { unauthenticatedUser } from "@/mocks/handlers";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// `router.load()` lazily imports each matched route's module, so a case pays its transform cost.
vi.setConfig({ testTimeout: 15_000 });

const DEEP_LINK = "/w/acme/mentor/thread-1?message=hi";

/**
 * The gate every authenticated route consults before anything below it loads. Everything else about
 * the notice is covered by the dialog's stories; what matters here is which way the gate errs,
 * because the server refuses every gated call until the notice is answered.
 */
describe("consent gate", () => {
	function newClient() {
		return new QueryClient({ defaultOptions: { queries: { retry: false } } });
	}

	function noticeAnswered(completed: boolean) {
		server.use(http.get("*/user/consent", () => HttpResponse.json({ completed })));
	}

	it("lets the application load once the notice has been answered", async () => {
		noticeAnswered(true);
		await expect(consentIsPending(newClient())).resolves.toBe(false);
	});

	it("holds the application back while the notice is outstanding", async () => {
		noticeAnswered(false);
		await expect(consentIsPending(newClient())).resolves.toBe(true);
	});

	it("never asks on behalf of a signed-out visitor", async () => {
		// The landing page waits on this, and the only answer it could get is 401.
		let asked = false;
		server.use(
			unauthenticatedUser,
			http.get("*/user/consent", () => {
				asked = true;
				return HttpResponse.json({ completed: false });
			}),
		);

		await expect(consentIsPending(newClient())).resolves.toBe(false);
		expect(asked).toBe(false);
	});

	it("lets the application load when it cannot tell, rather than blocking everyone", async () => {
		// Blocking here would put an undismissable notice in front of every reader whenever this one
		// call failed, with no way out but a reload — and it would not be protective either, because
		// the server refuses the gated calls itself. A reader who owes the notice still meets that.
		server.use(http.get("*/user/consent", () => HttpResponse.error()));

		await expect(consentIsPending(newClient())).resolves.toBe(false);
	});
});

/** The same gate as the authenticated subtree runs it, driven through the generated tree. */
describe("authenticated route gate", () => {
	async function land(url: string) {
		server.use(http.get("*/workspaces", () => HttpResponse.json([workspaceListItem("acme")])));
		const router = createRouter({
			routeTree,
			history: createMemoryHistory({ initialEntries: [url] }),
			context: { queryClient: new QueryClient(), auth: undefined },
		});
		await router.load();
		return router.state.location;
	}

	it("puts the outstanding notice over the page the reader asked for", async () => {
		server.use(http.get("*/user/consent", () => HttpResponse.json({ completed: false })));

		const location = await land(DEEP_LINK);

		expect(location.pathname).toBe("/consent");
		expect(location.search).toMatchObject({ returnTo: DEEP_LINK });
		// The address bar stays on the page, so the notice reads as that page pausing.
		expect(location.maskedLocation?.href).toBe(DEEP_LINK);
	});

	it("opens the page anyway when the notice cannot be loaded", async () => {
		server.use(http.get("*/user/consent", () => HttpResponse.error()));

		expect((await land(DEEP_LINK)).href).toBe(DEEP_LINK);
	});
});
