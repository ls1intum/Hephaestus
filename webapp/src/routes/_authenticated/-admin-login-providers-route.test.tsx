import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import type { LoginProviderView } from "@/api/types.gen";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

function provider(registrationId: string, displayName: string): LoginProviderView {
	return {
		registrationId,
		displayName,
		type: "oidc",
		baseUrl: `https://${registrationId}.example.test`,
		redirectUri: `https://app.example.test/login/oauth2/code/${registrationId}`,
		scopes: "openid profile",
		enabled: true,
		createdAt: new Date("2026-07-01T00:00:00Z"),
		updatedAt: new Date("2026-07-01T00:00:00Z"),
	};
}

/** The first mount in a file pays the lazy transform of the whole admin layout — seconds, not 1s. */
const TRANSFORM_WAIT = { timeout: 10_000 };

/**
 * The real router, not `Route.options.component`: the gate in `admin.tsx`'s `beforeLoad`, the route's
 * `head` and anything it reads off the URL only exist when the route is matched, and a test that
 * calls the component directly cannot tell a working route from an unreachable one.
 */
async function renderLoginProvidersRoute() {
	// One client for the guards and the provider, exactly as `main.tsx` wires it.
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: ["/admin/login-providers"] }),
		context: { queryClient, auth: undefined },
	});
	render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				{/* biome-ignore lint/suspicious/noExplicitAny: the app's router context is wider than this test needs. */}
				<RouterProvider router={router as any} />
			</AuthProvider>
		</QueryClientProvider>,
	);
}

describe("instance login providers route", () => {
	it("keeps each provider's toggle pending independently when two run at once", async () => {
		// Nothing gates this switch — no confirm, no modal — so a second row can be toggled while the
		// first PATCH is still open. GitHub's hangs, GitLab's answers at once. A single "which provider
		// is mutating" id is cleared by GitLab settling, which re-enables GitHub's switch mid-request.
		let releaseSlowToggle: (() => void) | undefined;
		const slowToggle = new Promise<void>((resolve) => {
			releaseSlowToggle = resolve;
		});
		let slowToggleCalls = 0;
		const providers = [provider("github", "GitHub"), provider("gitlab", "GitLab")];
		server.use(
			http.get("*/admin/login-providers", () => HttpResponse.json(providers)),
			http.patch("*/admin/login-providers/github", async () => {
				slowToggleCalls += 1;
				await slowToggle;
				return HttpResponse.json({ ...providers[0], enabled: false });
			}),
			http.patch("*/admin/login-providers/gitlab", () =>
				HttpResponse.json({ ...providers[1], enabled: false }),
			),
		);

		await renderLoginProvidersRoute();

		fireEvent.click(await screen.findByRole("switch", { name: "Disable GitHub" }, TRANSFORM_WAIT));
		await waitFor(() => expect(slowToggleCalls).toBe(1));
		fireEvent.click(screen.getByRole("switch", { name: "Disable GitLab" }));
		await waitFor(() =>
			expect(screen.getByRole("switch", { name: "Disable GitLab" }).getAttribute("aria-busy")).toBe(
				"false",
			),
		);

		// The still-running row reads as busy and refuses input; the settled one is usable again.
		expect(screen.getByRole("switch", { name: "Disable GitHub" }).getAttribute("aria-busy")).toBe(
			"true",
		);
		expect(
			(screen.getByRole("button", { name: "Delete GitHub" }) as HTMLButtonElement).disabled,
		).toBe(true);
		expect(
			(screen.getByRole("button", { name: "Delete GitLab" }) as HTMLButtonElement).disabled,
		).toBe(false);

		releaseSlowToggle?.();
		await waitFor(() => expect(slowToggleCalls).toBe(1));
	});
});
