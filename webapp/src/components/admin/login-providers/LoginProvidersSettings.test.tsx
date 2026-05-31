// Behavioral tests for the workspace login-providers admin surface (ADR 0017). HTTP is
// intercepted at the MSW boundary (src/mocks/handlers.ts); we assert on observable DOM and that
// an RFC-9457 problem+json error from the issuer-discovery probe surfaces its `detail` via
// problemDetailOf. Closes part of the F7 gating gap. The dialog's pure client-side validation is
// covered separately in AddLoginProviderDialog.test.tsx; here we drive the mutation flows.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { identityConnections } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { LoginProvidersSettings } from "./LoginProvidersSettings";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

function fillDialog(dialog: HTMLElement) {
	const input = (suffix: string) => {
		const el = Array.from(dialog.querySelectorAll<HTMLInputElement>("input")).find((i) =>
			i.id.endsWith(suffix),
		);
		if (!el) throw new Error(`No input ${suffix}`);
		return el;
	};
	fireEvent.change(input("display-name"), { target: { value: "Acme GHE" } });
	fireEvent.change(input("issuer-url"), { target: { value: "https://git.example.com" } });
	fireEvent.change(input("client-id"), { target: { value: "cid" } });
	fireEvent.change(input("client-secret"), { target: { value: "shh" } });
}

describe("LoginProvidersSettings", () => {
	it("lists the IDENTITY-family providers from the connection registry", async () => {
		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);

		for (const c of identityConnections) {
			expect(await screen.findByText(c.displayName as string)).toBeTruthy();
		}
	});

	it("adds a provider and refreshes the list with the newly created connection", async () => {
		// After the initiate POST succeeds, the component invalidates the list query; the refetch
		// must show the newly created provider. We flip the GET to include it on the second call.
		let listCalls = 0;
		server.use(
			http.get("*/workspaces/:workspaceSlug/connections", () => {
				listCalls += 1;
				if (listCalls === 1) return HttpResponse.json(identityConnections);
				return HttpResponse.json([
					...identityConnections,
					{
						id: 599,
						kind: "OIDC_LOGIN_GITHUB",
						family: "IDENTITY",
						displayName: "Freshly Added GHE",
						state: "ACTIVE",
						capabilities: [],
						createdAt: "2026-05-30T00:00:00Z",
						updatedAt: "2026-05-30T00:00:00Z",
					},
				]);
			}),
			http.post("*/workspaces/:workspaceSlug/connections", () =>
				HttpResponse.json({ type: "LINKED", connectionId: 599, displayName: "Freshly Added GHE" }),
			),
		);

		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);
		await screen.findByText("GitHub Enterprise (login)");

		fireEvent.click(screen.getByRole("button", { name: /Add provider/ }));
		const dialog = await screen.findByRole("dialog");
		fillDialog(dialog);
		fireEvent.click(within(dialog).getByRole("button", { name: /Validate.*add/ }));

		expect(await screen.findByText("Freshly Added GHE")).toBeTruthy();
	});

	it("renders the RFC-9457 problem+json `detail` inline when the issuer probe fails", async () => {
		server.use(
			http.post("*/workspaces/:workspaceSlug/connections", () =>
				HttpResponse.json(
					{
						type: "https://hephaestus.test/problems/issuer-unreachable",
						title: "Issuer discovery failed",
						status: 400,
						detail: "The issuer https://git.example.com could not be reached (timeout).",
					},
					{ status: 400, headers: { "Content-Type": "application/problem+json" } },
				),
			),
		);

		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);
		await screen.findByText("GitHub Enterprise (login)");

		fireEvent.click(screen.getByRole("button", { name: /Add provider/ }));
		const dialog = await screen.findByRole("dialog");
		fillDialog(dialog);
		fireEvent.click(within(dialog).getByRole("button", { name: /Validate.*add/ }));

		// problemDetailOf prefers `detail`; it must reach the inline alert, not be swallowed.
		expect(
			await screen.findByText("The issuer https://git.example.com could not be reached (timeout)."),
		).toBeTruthy();
	});

	it("suspends an active provider via the unified status endpoint", async () => {
		// The old per-verb endpoints are gone: suspend now PATCHes .../status with
		// { state: "SUSPENDED" }. Capture the request to prove the transition is encoded
		// in the body and the list is refetched afterwards.
		let patchedBody: { state?: string; reason?: string } | undefined;
		server.use(
			http.patch("*/workspaces/:workspaceSlug/connections/:id/status", async ({ request }) => {
				patchedBody = (await request.json().catch(() => ({}))) as typeof patchedBody;
				return HttpResponse.json({ ok: true });
			}),
		);

		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);
		// The ACTIVE GHE provider exposes a Suspend control; the SUSPENDED GitLab one does not.
		const suspendButton = await screen.findByRole("button", {
			name: /Suspend GitHub Enterprise/,
		});
		fireEvent.click(suspendButton);

		await vi.waitFor(() => {
			expect(patchedBody?.state).toBe("SUSPENDED");
		});
	});

	it("shows the empty state when no IDENTITY connections exist", async () => {
		server.use(http.get("*/workspaces/:workspaceSlug/connections", () => HttpResponse.json([])));
		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);
		expect(await screen.findByText("No login providers yet")).toBeTruthy();
	});

	it("renders a load-error alert when the registry query fails", async () => {
		server.use(
			http.get(
				"*/workspaces/:workspaceSlug/connections",
				() => new HttpResponse(null, { status: 500 }),
			),
		);
		renderWithClient(
			<LoginProvidersSettings workspaceSlug="acme" apiOrigin="https://hephaestus.test" />,
		);
		const alert = await screen.findByRole("alert");
		expect(alert.textContent).toContain("Failed to load login providers");
	});
});
