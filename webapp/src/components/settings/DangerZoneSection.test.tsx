// Behavioral tests for the GDPR danger-zone settings (ADR 0017). HTTP is intercepted at the MSW
// boundary; we assert on observable DOM and that the destructive delete is gated behind the typed
// confirmation phrase.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { currentUser } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { DangerZoneSection } from "./DangerZoneSection";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>{node}</AuthProvider>
		</QueryClientProvider>,
	);
}

describe("DangerZoneSection — data export", () => {
	afterEach(() => {
		vi.useRealTimers();
	});

	it("requests an export, polls PENDING -> READY, then surfaces a Download affordance", async () => {
		// `shouldAdvanceTime` lets MSW's promise-based responses keep resolving while we still
		// control the 2s `refetchInterval` poll deterministically (no real wall-clock wait).
		vi.useFakeTimers({ shouldAdvanceTime: true });
		renderWithClient(<DangerZoneSection onAccountDeleted={vi.fn()} />);

		fireEvent.click(screen.getByRole("button", { name: /Request export/ }));

		// First status poll returns PENDING; the in-progress copy proves we're polling.
		await waitFor(() => expect(screen.getByText(/Preparing your export/i)).toBeTruthy());

		// Drive the 2s poll interval forward; the next poll lands READY.
		await act(() => vi.advanceTimersByTimeAsync(2000));

		await waitFor(() => expect(screen.getByRole("button", { name: /Download/ })).toBeTruthy());
		expect(screen.getByText(/ready to download/i)).toBeTruthy();
	});
});

describe("DangerZoneSection — account deletion", () => {
	function openDeleteDialog() {
		// The trigger button (collapsed) is labelled "Delete"; opening reveals the confirm input.
		fireEvent.click(screen.getByRole("button", { name: "Delete" }));
	}

	it("keeps deletion disabled until the exact confirmation phrase is typed", async () => {
		renderWithClient(<DangerZoneSection onAccountDeleted={vi.fn()} />);
		openDeleteDialog();

		const dialog = await screen.findByRole("alertdialog");
		const confirmButton = within(dialog).getByRole("button", {
			name: "Delete account",
		}) as HTMLButtonElement;
		expect(confirmButton.disabled).toBe(true);

		const input = within(dialog).getByLabelText("Confirmation phrase");
		fireEvent.change(input, { target: { value: "delete my acc" } });
		expect(confirmButton.disabled).toBe(true);

		// Enables only once the phrase matches AND the session (account id) has resolved.
		fireEvent.change(input, { target: { value: "delete my account" } });
		await waitFor(() => expect(confirmButton.disabled).toBe(false));
	});

	it("sends the account id in X-Confirm-Delete and fires onAccountDeleted once confirmed", async () => {
		const onAccountDeleted = vi.fn();
		let sentHeader: string | null = null;
		server.use(
			http.delete("*/user", ({ request }) => {
				sentHeader = request.headers.get("X-Confirm-Delete");
				// Enforce the real server contract: header must equal the account id.
				return sentHeader === String(currentUser.id)
					? new HttpResponse(null, { status: 204 })
					: new HttpResponse(null, { status: 400 });
			}),
		);

		renderWithClient(<DangerZoneSection onAccountDeleted={onAccountDeleted} />);
		openDeleteDialog();

		const dialog = await screen.findByRole("alertdialog");
		const confirmButton = within(dialog).getByRole("button", {
			name: "Delete account",
		}) as HTMLButtonElement;
		fireEvent.change(within(dialog).getByLabelText("Confirmation phrase"), {
			target: { value: "  Delete My Account  " }, // trimmed + case-insensitive match
		});
		// Button only enables once the session (account id) has resolved.
		await waitFor(() => expect(confirmButton.disabled).toBe(false));
		fireEvent.click(confirmButton);

		await waitFor(() => expect(onAccountDeleted).toHaveBeenCalledTimes(1));
		expect(sentHeader).toBe(String(currentUser.id));
	});

	it("does not call the deletion endpoint when the phrase is wrong (button stays disabled)", async () => {
		const onAccountDeleted = vi.fn();
		let deleteHit = false;
		server.use(
			http.delete("*/user", () => {
				deleteHit = true;
				return new HttpResponse(null, { status: 204 });
			}),
		);

		renderWithClient(<DangerZoneSection onAccountDeleted={onAccountDeleted} />);
		openDeleteDialog();

		const dialog = await screen.findByRole("alertdialog");
		fireEvent.change(within(dialog).getByLabelText("Confirmation phrase"), {
			target: { value: "nope" },
		});
		fireEvent.click(within(dialog).getByRole("button", { name: "Delete account" }));

		await Promise.resolve();
		expect(deleteHit).toBe(false);
		expect(onAccountDeleted).not.toHaveBeenCalled();
	});
});
