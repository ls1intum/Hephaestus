import type { QueryClient } from "@tanstack/react-query";
import { act, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse, type PathParams } from "msw";
import { expect, it, onTestFinished, vi } from "vitest";

import { client } from "@/api/client.gen";
import { getIntegrationCatalog } from "@/api/sdk.gen";
import type { WorkspaceOnboarding } from "@/api/types.gen";
import { workspaceOnboarding } from "@/mocks/fixtures/onboarding";
import { ownMembership, workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { installPasskeyChallenge } from "@/runtime/auth/passkey-challenge";
import { deferred } from "@/test/async";
import { renderRouteAtWithRouter, ROUTE_RENDER_WAIT } from "@/test/router-harness";

// Mounting the real route pulls in the whole layout and its lazy modules.
vi.setConfig({ testTimeout: 30_000 });

/** The member chose no AI, so the Heph panel stays away and the page makes no Heph reads. */
const NO_HEPH: WorkspaceOnboarding = {
	...workspaceOnboarding(),
	aiChoice: "NO_AI",
	mentorRefusal: "NO_AI",
};

/**
 * An administrator of `acme` and `tutors` whose account also has the instance's administrator role,
 * and who has not verified a passkey: member reads answer, and every administration read is refused
 * as the server refuses it. Refusals in `held.workspaceSlug` wait for `held.until`. Returns each
 * refused read as it arrives, such as "acme catalog".
 */
function serveUnverifiedAdministrator(held?: { workspaceSlug: string; until: Promise<unknown> }) {
	const refused: string[] = [];
	const refuse =
		(read: string) =>
		async ({ params }: { params: PathParams }) => {
			const workspaceSlug = String(params.workspaceSlug);
			refused.push(`${workspaceSlug} ${read}`);
			if (held?.workspaceSlug === workspaceSlug) {
				await held.until;
			}
			return HttpResponse.json(
				{ status: 403, code: "passkey_required", title: "Passkey required" },
				{ status: 403 },
			);
		};
	server.use(
		http.get("*/workspaces", () =>
			HttpResponse.json([
				workspaceListItem("acme", { practicesEnabled: true }),
				workspaceListItem("tutors", { id: 2, practicesEnabled: true }),
			]),
		),
		http.get("*/user/features", () => HttpResponse.json({})),
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces/:workspaceSlug/onboarding/me", () => HttpResponse.json(NO_HEPH)),
		http.get("*/workspaces/:workspaceSlug/connections/catalog", refuse("catalog")),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", refuse("feedback")),
	);
	return refused;
}

/** The route at `path`, with the app's handling of passkey refusals on the generated client. */
function renderAt(path: string) {
	const rendered = renderRouteAtWithRouter(path);
	onTestFinished(installPasskeyChallenge(rendered.router));
	return rendered;
}

/** Wait for the mounted chrome's requests, including a refused request, to finish. */
async function settleChrome(queryClient: QueryClient) {
	await screen.findByText("Administration", undefined, ROUTE_RENDER_WAIT);
	await waitFor(() => expect(queryClient.isFetching()).toBe(0));
}

const ACME = { path: { workspaceSlug: "acme" } };

it("keeps an administrator on an ordinary page without requesting protected sidebar data", async () => {
	const refused = serveUnverifiedAdministrator();
	const { router, queryClient } = renderAt("/w/acme/teams");
	await screen.findByRole("heading", { name: "Teams" }, ROUTE_RENDER_WAIT);
	await settleChrome(queryClient);
	expect(refused).toStrictEqual([]);
	expect(router.state.location.pathname).toBe("/w/acme/teams");
});

it("does not request protected sidebar data from global user settings", async () => {
	const refused = serveUnverifiedAdministrator();
	const { router, queryClient } = renderAt("/settings");
	await screen.findByRole("heading", { name: "User settings" }, ROUTE_RENDER_WAIT);
	await settleChrome(queryClient);
	expect(refused).toStrictEqual([]);
	expect(router.state.location.pathname).toBe("/settings");
});

it("still takes an administrator who opens an administration page to verify a passkey", async () => {
	serveUnverifiedAdministrator();
	const { router, queryClient } = renderAt("/w/acme/teams");
	await screen.findByRole("heading", { name: "Teams" }, ROUTE_RENDER_WAIT);
	await settleChrome(queryClient);
	await act(async () => {
		await router.navigate({
			to: "/w/$workspaceSlug/admin/integrations",
			params: { workspaceSlug: "acme" },
		});
	});

	await waitFor(() => expect(router.state.location.pathname).toBe("/settings"), ROUTE_RENDER_WAIT);
	expect(router.state.location.hash).toBe("passkeys");
});

it("leaves the developer on the workspace they switched to when a refusal of the page they left arrives late", async () => {
	const held = deferred();
	const refused = serveUnverifiedAdministrator({ workspaceSlug: "acme", until: held.promise });
	const { router, queryClient } = renderAt("/w/acme/teams");
	await screen.findByRole("heading", { name: "Teams" }, ROUTE_RENDER_WAIT);
	await settleChrome(queryClient);
	expect(refused).toStrictEqual([]);
	const reads = refused.length;

	// A request the page itself makes, such as an administration action, that the server refuses.
	// Its arrival at the server means the app saw it leave this page.
	const request = getIntegrationCatalog(ACME);
	await waitFor(() => expect(refused).toHaveLength(reads + 1));
	await act(async () => {
		await router.navigate({
			to: "/w/$workspaceSlug/teams",
			params: { workspaceSlug: "tutors" },
		});
	});
	held.resolve();
	const { response } = await request;

	expect(response?.status).toBe(403);
	expect(router.state.location.pathname).toBe("/w/tutors/teams");
	await screen.findByRole("heading", { name: "Teams" });
});

it("leaves the current workspace intact when the old page changes while its response body is arriving", async () => {
	serveUnverifiedAdministrator();
	const headers = deferred();
	const finishBody = deferred();
	onTestFinished(() => finishBody.resolve());
	server.use(
		http.get("*/workspaces/acme/connections/catalog", () => {
			const body = new ReadableStream<Uint8Array>({
				start(controller) {
					void finishBody.promise.then(() => {
						controller.enqueue(
							new TextEncoder().encode(JSON.stringify({ code: "passkey_required" })),
						);
						controller.close();
					});
				},
			});
			return new HttpResponse(body, {
				status: 403,
				headers: { "Content-Type": "application/problem+json", "X-Test-Delayed-Body": "passkey" },
			});
		}),
	);
	const { router, queryClient } = renderRouteAtWithRouter("/w/acme/teams");
	await screen.findByRole("heading", { name: "Teams" }, ROUTE_RENDER_WAIT);
	await settleChrome(queryClient);
	// Only the request below remains: observe its headers before the passkey interceptor reads its body.
	const observing = client.interceptors.response.use((response) => {
		headers.resolve();
		return response;
	});
	onTestFinished(() => client.interceptors.response.eject(observing));
	onTestFinished(installPasskeyChallenge(router));
	const request = getIntegrationCatalog(ACME);
	await headers.promise;
	// Flush handling of the received headers while the real streamed JSON body stays open.
	await act(async () => {
		await Promise.resolve();
	});
	await act(async () => {
		await router.navigate({ to: "/w/$workspaceSlug/teams", params: { workspaceSlug: "tutors" } });
	});
	finishBody.resolve();
	const { response } = await request;
	expect(response?.status).toBe(403);
	expect(router.state.location.pathname).toBe("/w/tutors/teams");
	await screen.findByRole("heading", { name: "Teams" });
});
