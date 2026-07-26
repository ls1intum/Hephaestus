import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { adminListLoginProvidersQueryKey } from "@/api/@tanstack/react-query.gen";
import type { LoginProviderView } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { Route } from "./admin.login-providers";

const AdminLoginProvidersPage = Route.options.component;

// `preload()` lazily transforms the whole route module and its dialogs.
vi.setConfig({ testTimeout: 15_000 });

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

describe("AdminLoginProvidersPage", () => {
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

		const queryClient = new QueryClient({
			defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
		});
		queryClient.setQueryData(adminListLoginProvidersQueryKey(), providers);

		if (!AdminLoginProvidersPage) throw new Error("Login providers route must have a component");
		await (
			AdminLoginProvidersPage as typeof AdminLoginProvidersPage & {
				preload: () => Promise<unknown>;
			}
		).preload();
		render(
			<QueryClientProvider client={queryClient}>
				<AdminLoginProvidersPage />
			</QueryClientProvider>,
		);

		fireEvent.click(await screen.findByRole("switch", { name: "Disable GitHub" }));
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
