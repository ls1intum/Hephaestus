// jest-dom matchers and user-event are NOT set up in this repo's vitest, so assertions use plain
// DOM and `fireEvent`. Requests are served by MSW (see src/test/setup-msw.ts).

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { delay, HttpResponse, http } from "msw";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { listWorkspacesQueryKey } from "@/api/@tanstack/react-query.gen";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { server } from "@/mocks/server";
import { useWorkspaceStore } from "@/stores/workspace-store";
import { renderWithRouter } from "@/test/router";
import { AdminDangerZoneSettings } from "./AdminDangerZoneSettings";

vi.mock("sonner", () => ({
	toast: { success: vi.fn(), error: vi.fn() },
}));

// The hook only needs to know someone is signed in.
vi.mock("@/integrations/auth/AuthContext", () => ({
	useAuth: () => ({ isAuthenticated: true, isLoading: false }),
}));

// This file has the heaviest setup in the suite (real router + store + two queries + MSW), so the
// 1s waitFor default is too tight on a cold runner.
const SLOW = { timeout: 8000 };

// The real redirect authority: leaving a purged workspace is use-active-workspace's job, so the
// exit contract needs the real hook.
function ActiveWorkspaceProbe() {
	useActiveWorkspaceSlug();
	return null;
}

const queryClientRef: { current: QueryClient | null } = { current: null };

function renderContainer() {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	queryClientRef.current = queryClient;
	// Start on the workspace's own admin page: purging has to navigate away from here, and a test
	// that started at "/" would assert the destination it was already sitting on.
	return renderWithRouter(
		<QueryClientProvider client={queryClient}>
			<ActiveWorkspaceProbe />
			<AdminDangerZoneSettings workspaceSlug="demo" />
		</QueryClientProvider>,
		"/w/demo/admin/settings",
	);
}

function membershipHandler(role: "OWNER" | "ADMIN") {
	return http.get("*/workspaces/demo/members/me", () =>
		HttpResponse.json({ role, userLogin: "ada" }),
	);
}

// Drops the purged workspace on DELETE, as the server does (its list returns only ACTIVE).
// A handler that kept returning it would resurrect the workspace and mask the redirect.
function workspaceListHandlers(...slugs: string[]) {
	const state = {
		list: slugs.map((slug) => ({ workspaceSlug: slug, displayName: slug })),
		reads: 0,
	};
	return {
		state,
		handlers: [
			http.get("*/workspaces", () => {
				state.reads += 1;
				return HttpResponse.json(state.list);
			}),
			http.delete("*/workspaces/demo", () => {
				state.list = state.list.filter((workspace) => workspace.workspaceSlug !== "demo");
				return new HttpResponse(null, { status: 204 });
			}),
		],
	};
}

function deleteButton() {
	return screen.getByRole("button", { name: /^delete workspace$/i });
}

/** The trigger is inert until the role query resolves, so opening the dialog has to wait for it. */
async function openDialog() {
	await waitFor(() => expect(deleteButton().getAttribute("aria-disabled")).toBe("false"), SLOW);
	fireEvent.click(deleteButton());
	return screen.findByLabelText(/to confirm/i);
}

function dialogConfirmButton() {
	return within(screen.getByRole("alertdialog")).getByRole("button", {
		name: /^delete workspace$/i,
	});
}

beforeEach(() => {
	vi.clearAllMocks();
	// The store persists to localStorage, so a leftover selection would leak across tests.
	useWorkspaceStore.setState({ selectedSlug: "demo", hasHydrated: true });
});

describe("AdminDangerZoneSettings", () => {
	it("purges the workspace and leaves it", async () => {
		const { state, handlers } = workspaceListHandlers("demo");
		server.use(membershipHandler("OWNER"), ...handlers);

		const { router } = await renderContainer();
		// Wait for the cache itself, not the request: this test is the loaded-list path, and the
		// not-yet-loaded one is covered separately below.
		await waitFor(
			() => expect(queryClientRef.current?.getQueryData(listWorkspacesQueryKey())).toHaveLength(1),
			SLOW,
		);

		fireEvent.change(await openDialog(), { target: { value: "demo" } });
		fireEvent.click(dialogConfirmButton());

		await waitFor(() => expect(state.list).toHaveLength(0), SLOW);
		// Their only workspace is gone, so there is nowhere to fall back to but the root.
		await waitFor(() => expect(router.state.location.pathname).toBe("/"), SLOW);
		expect(toast.success).toHaveBeenCalled();
	});

	it("leaves the workspace even when the list had not loaded before the purge", async () => {
		let reads = 0;
		let list = [{ workspaceSlug: "demo", displayName: "demo" }];
		server.use(
			membershipHandler("OWNER"),
			http.get("*/workspaces", async () => {
				reads += 1;
				// The first read is still in flight when the purge lands, so the cache is empty and the
				// filter has nothing to remove: only the invalidate-driven refetch can produce the exit.
				if (reads === 1) {
					await delay(400);
				}
				return HttpResponse.json(list);
			}),
			http.delete("*/workspaces/demo", () => {
				list = [];
				return new HttpResponse(null, { status: 204 });
			}),
		);

		const { router } = await renderContainer();

		fireEvent.change(await openDialog(), { target: { value: "demo" } });
		fireEvent.click(dialogConfirmButton());

		await waitFor(() => expect(router.state.location.pathname).toBe("/"), SLOW);
	});

	it("falls back to a remaining workspace rather than the root", async () => {
		const { handlers } = workspaceListHandlers("demo", "other");
		server.use(membershipHandler("OWNER"), ...handlers);

		const { router } = await renderContainer();

		fireEvent.change(await openDialog(), { target: { value: "demo" } });
		fireEvent.click(dialogConfirmButton());

		await waitFor(() => expect(router.state.location.pathname).toContain("/w/other"), SLOW);
	});

	it("leaves the workspace even when the follow-up list refetch fails", async () => {
		let listReads = 0;
		server.use(
			membershipHandler("OWNER"),
			http.get("*/workspaces", () => {
				listReads += 1;
				// Succeed once so the list is populated, then fail every refetch.
				return listReads === 1
					? HttpResponse.json([{ workspaceSlug: "demo", displayName: "Demo" }])
					: new HttpResponse(null, { status: 500 });
			}),
			http.delete("*/workspaces/demo", () => new HttpResponse(null, { status: 204 })),
		);

		const { router } = await renderContainer();
		await waitFor(() => expect(listReads).toBeGreaterThanOrEqual(1), SLOW);

		fireEvent.change(await openDialog(), { target: { value: "demo" } });
		fireEvent.click(dialogConfirmButton());

		// The purge succeeded, so the app must leave regardless of what the refetch does, and the
		// switcher must not keep offering a workspace that no longer exists.
		await waitFor(() => expect(router.state.location.pathname).not.toContain("/w/demo"), SLOW);
		expect(queryClientRef.current?.getQueryData(listWorkspacesQueryKey())).toEqual([]);
	});

	it("keeps the dialog open and surfaces the server's reason when the purge fails", async () => {
		server.use(
			membershipHandler("OWNER"),
			http.get("*/workspaces", () => HttpResponse.json([])),
			http.delete("*/workspaces/demo", () =>
				HttpResponse.json(
					{
						title: "Workspace lifecycle violation",
						detail: "Workspace has an active billing run.",
					},
					{ status: 409 },
				),
			),
		);

		await renderContainer();

		fireEvent.change(await openDialog(), { target: { value: "demo" } });
		fireEvent.click(dialogConfirmButton());

		await waitFor(
			() =>
				expect(toast.error).toHaveBeenCalledWith("Failed to delete workspace", {
					description: "Workspace has an active billing run.",
				}),
			SLOW,
		);
		expect(screen.queryByRole("alertdialog")).toBeTruthy();
	});

	it("does not accuse the owner of not being the owner while the role loads", async () => {
		server.use(
			http.get("*/workspaces/demo/members/me", async () => {
				await delay(50);
				return HttpResponse.json({ role: "OWNER", userLogin: "ada" });
			}),
			http.get("*/workspaces", () => HttpResponse.json([])),
		);

		await renderContainer();

		expect(screen.queryByText(/only the workspace owner/i)).toBeNull();
		await waitFor(() => expect(deleteButton().getAttribute("aria-disabled")).toBe("false"), SLOW);
	});

	it("does not call the owner a non-owner when the role query fails", async () => {
		server.use(
			http.get("*/workspaces/demo/members/me", () => new HttpResponse(null, { status: 500 })),
			http.get("*/workspaces", () => HttpResponse.json([])),
		);

		await renderContainer();

		await waitFor(
			() => expect(screen.queryByText(/couldn't confirm your role/i)).toBeTruthy(),
			SLOW,
		);
		expect(screen.queryByText(/only the workspace owner/i)).toBeNull();
		expect(deleteButton().getAttribute("aria-disabled")).toBe("true");
	});

	it("blocks a non-owner admin", async () => {
		server.use(
			membershipHandler("ADMIN"),
			http.get("*/workspaces", () => HttpResponse.json([])),
		);

		await renderContainer();

		await waitFor(() => expect(deleteButton().getAttribute("aria-disabled")).toBe("true"), SLOW);
		expect(deleteButton().hasAttribute("disabled")).toBe(false);
		fireEvent.click(deleteButton());
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});
});
