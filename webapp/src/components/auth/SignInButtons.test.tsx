// Behavioral test for SignInButtons (ADR 0017): the discovery endpoint emits UPPERCASE
// providerType (`"GITHUB"`/`"GITLAB"`, per IdentityProviderDiscoveryController.providerTypeOf).
// This asserts an uppercase GITHUB provider renders a "Continue with GitHub" button (case-insensitive
// providerType handling), and that a discovery FAILURE renders a neutral message with NO provider
// button (so a GitLab-only instance never shows a dead GitHub path).

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { IdentityProviderView } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { SignInButtons } from "./SignInButtons";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe("SignInButtons", () => {
	it("renders a Continue-with-GitHub button for an UPPERCASE GITHUB provider from discovery", async () => {
		// Realistic discovery payload: providerType is uppercase, exactly as the server emits it. A
		// case-sensitive `=== "github"` comparison would mis-handle it; the accessible name proves the
		// provider was resolved and labelled correctly.
		const providers: IdentityProviderView[] = [
			{ registrationId: "github", displayName: "GitHub", providerType: "GITHUB" },
		];
		server.use(http.get("*/identity-providers", () => HttpResponse.json(providers)));

		renderWithClient(<SignInButtons onSignIn={vi.fn()} />);

		// findByRole throws if no enabled "Continue with GitHub" button is rendered.
		const button = await screen.findByRole("button", { name: /continue with github/i });
		expect(button).toBeTruthy();
	});

	it("shows a neutral error state — and NO provider button — when discovery fails", async () => {
		// Security-relevant: on a GitLab-only instance a fallback GitHub button would lead users to a
		// dead OAuth path, so a discovery failure must render a neutral message, not a provider button.
		server.use(
			http.get("*/identity-providers", () =>
				HttpResponse.json({ message: "boom" }, { status: 500 }),
			),
		);

		renderWithClient(<SignInButtons onSignIn={vi.fn()} />);

		// findByText throws if the neutral message never appears (jest-dom matchers aren't set up here).
		expect(await screen.findByText(/Couldn't load sign-in options/i)).toBeTruthy();
		expect(screen.queryByRole("button", { name: /continue with/i })).toBeNull();
	});
});
