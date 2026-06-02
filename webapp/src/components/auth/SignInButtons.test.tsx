// Behavioral test for SignInButtons (ADR 0017): the discovery endpoint emits UPPERCASE
// providerType (`"GITHUB"`/`"GITLAB"`, per IdentityProviderDiscoveryController.providerTypeOf).
// This asserts an uppercase GITHUB provider renders the *branded* GitHubSignInButton — not the
// generic fallback Button — so a case-sensitive `=== "github"` comparison regresses to the
// generic path and fails this test.

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
	it("renders the branded GitHub button for an UPPERCASE GITHUB provider from discovery", async () => {
		// Realistic discovery payload: providerType is uppercase, exactly as the server emits it.
		const providers: IdentityProviderView[] = [
			{ registrationId: "github", displayName: "GitHub", providerType: "GITHUB" },
		];
		server.use(http.get("*/identity-providers", () => HttpResponse.json(providers)));

		renderWithClient(<SignInButtons onSignIn={vi.fn()} />);

		const button = await screen.findByRole("button", { name: /continue with github/i });
		// The branded GitHubSignInButton carries the GitHub brand class; the generic fallback
		// Button does not. Asserting on it proves we took the branded path, not the fallback.
		expect(button.className).toContain("bg-github-black");
	});
});
