// Behavioral tests for the GDPR danger-zone settings (ADR 0017). HTTP is intercepted at the MSW
// boundary; we assert on observable DOM and that the destructive delete is gated behind the typed
// confirmation phrase. Closes part of the F7 gating gap.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { DangerZoneSection } from "./DangerZoneSection";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe("DangerZoneSection — data export", () => {
	it("requests an export, polls PENDING -> READY, then surfaces a Download affordance", async () => {
		renderWithClient(<DangerZoneSection onAccountDeleted={vi.fn()} />);

		fireEvent.click(screen.getByRole("button", { name: /Request export/ }));

		// The default handlers return PENDING on the first status poll then READY afterwards; the
		// component polls every 2s while PENDING, so the Download button appears once READY lands.
		const downloadButton = await screen.findByRole(
			"button",
			{ name: /Download/ },
			{ timeout: 5000 },
		);
		expect(downloadButton).toBeTruthy();
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

		fireEvent.change(input, { target: { value: "delete my account" } });
		expect(confirmButton.disabled).toBe(false);
	});

	it("calls deleteCurrentUser and the onAccountDeleted callback once confirmed", async () => {
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
			target: { value: "  Delete My Account  " }, // trimmed + case-insensitive match
		});
		fireEvent.click(within(dialog).getByRole("button", { name: "Delete account" }));

		await waitFor(() => expect(onAccountDeleted).toHaveBeenCalledTimes(1));
		expect(deleteHit).toBe(true);
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

		// Give any in-flight microtasks a chance; nothing should have fired.
		await Promise.resolve();
		expect(deleteHit).toBe(false);
		expect(onAccountDeleted).not.toHaveBeenCalled();
	});
});
