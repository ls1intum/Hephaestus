import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import type { AdminAccountView } from "@/api/types.gen";
import { currentUser } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

const stepUpRefusal = () =>
	HttpResponse.json(
		{
			status: 403,
			title: "Confirm access",
			detail: "This action requires a recent sign-in.",
			code: "step_up_required",
			maxAgeSeconds: 300,
		},
		{ status: 403 },
	);

/** Opens one row's action menu and picks an item from it. */
async function chooseRowAction(rowName: string, action: string) {
	const user = userEvent.setup();
	await user.click(await screen.findByRole("button", { name: rowName }, ROUTE_RENDER_WAIT));
	await user.click(await screen.findByRole("menuitem", { name: action }));
	return user;
}

function serveRoleChanges(earlierAccounts: AdminAccountView[] = [], failFirstChange = false) {
	let account: AdminAccountView = {
		id: 7,
		displayName: "Ada Lovelace",
		primaryEmail: "ada@example.com",
		appRole: "USER",
		status: "ACTIVE",
	};
	const reads: AdminAccountView["appRole"][] = [];
	const pages: number[] = [];
	const changes: { id: string; body: unknown }[] = [];
	server.use(
		http.get("*/admin/users", ({ request }) => {
			const page = Number(new URL(request.url).searchParams.get("page") ?? 0);
			reads.push(account.appRole);
			pages.push(page);
			return HttpResponse.json([...earlierAccounts, account].slice(page * 25, (page + 1) * 25));
		}),
		http.patch("*/admin/users/:id", async ({ params, request }) => {
			changes.push({ id: String(params.id), body: await request.json() });
			if (failFirstChange && changes.length === 1) {
				return HttpResponse.json(
					{ status: 503, detail: "Role update unavailable." },
					{ status: 503 },
				);
			}
			account = { ...account, appRole: "APP_ADMIN" };
			return HttpResponse.json(account);
		}),
	);
	return { reads, pages, changes };
}

describe("instance users route", () => {
	it("shows the saved role and its inverse action after a successful role change", async () => {
		const { reads, changes } = serveRoleChanges();
		renderRouteAt("/admin/users");
		const table = await screen.findByRole(
			"table",
			{ name: "Application users" },
			ROUTE_RENDER_WAIT,
		);
		const row = await within(table).findByRole("row", { name: /Ada Lovelace/u });
		within(row).getByText("User", { exact: true });
		const user = await chooseRowAction("Actions for Ada Lovelace", "Grant instance admin");
		const dialog = await screen.findByRole("alertdialog", { name: "Grant instance admin?" });
		await user.click(within(dialog).getByRole("button", { name: "Grant instance admin" }));

		await screen.findByText("Role updated to Instance admin");
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(changes).toStrictEqual([{ id: "7", body: { appRole: "APP_ADMIN" } }]);
		expect.soft(reads).toStrictEqual(["USER", "APP_ADMIN"]);
		await within(table).findByText("Instance admin", { exact: true });
		await user.click(screen.getByRole("button", { name: "Actions for Ada Lovelace" }));
		await screen.findByRole("menuitem", { name: "Revoke instance admin" });
		expect(screen.queryByRole("menuitem", { name: "Grant instance admin" })).toBeNull();
	});

	it("keeps loaded pages after a failed role update and refreshes them after a successful retry", async () => {
		const earlierAccounts = Array.from(
			{ length: 25 },
			(_, index) =>
				({
					id: 100 + index,
					displayName: `Other account ${index}`,
					primaryEmail: `other-${index}@example.com`,
					appRole: "USER",
					status: "ACTIVE",
				}) satisfies AdminAccountView,
		);
		const { reads, pages, changes } = serveRoleChanges(earlierAccounts, true);
		renderRouteAt("/admin/users");
		const user = userEvent.setup();
		await user.click(await screen.findByRole("button", { name: "Load more" }, ROUTE_RENDER_WAIT));
		await chooseRowAction("Actions for Ada Lovelace", "Grant instance admin");
		const dialog = await screen.findByRole("alertdialog", { name: "Grant instance admin?" });
		await user.click(within(dialog).getByRole("button", { name: "Grant instance admin" }));

		await within(dialog).findByText("Role update unavailable.");
		expect(reads).toStrictEqual(["USER", "USER"]);
		expect(pages).toStrictEqual([0, 1]);
		const table = screen.getByRole("table", { name: "Application users", hidden: true });
		expect(within(table).getAllByRole("row", { hidden: true })).toHaveLength(27);
		expect(within(table).getByText("ada@example.com").closest("tr")?.textContent).toContain("User");

		await user.click(within(dialog).getByRole("button", { name: "Grant instance admin" }));
		await screen.findByText("Role updated to Instance admin");
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		await within(table).findByText("Instance admin", { exact: true });
		expect(reads).toStrictEqual(["USER", "USER", "APP_ADMIN", "APP_ADMIN"]);
		expect(pages).toStrictEqual([0, 1, 0, 1]);
		expect(changes).toStrictEqual([
			{ id: "7", body: { appRole: "APP_ADMIN" } },
			{ id: "7", body: { appRole: "APP_ADMIN" } },
		]);
		expect(within(table).getAllByRole("row")).toHaveLength(27);
		within(table).getByText("other-0@example.com");
		within(table).getByText("other-24@example.com");
	});

	it("offers only the sign-in providers this account is linked to when a role change is refused", async () => {
		let roleChanges = 0;
		server.use(
			http.patch("*/admin/users/:id", () => {
				roleChanges += 1;
				return stepUpRefusal();
			}),
			// The instance also offers GitHub, but this account has only ever signed in with GitLab.
			http.get("*/user", () =>
				HttpResponse.json({
					...currentUser,
					linkedProviders: [{ type: "GITLAB", serverUrl: "https://gitlab.lrz.de" }],
				}),
			),
		);

		renderRouteAt("/admin/users");
		const user = await chooseRowAction("Actions for Ada Lovelace", "Grant instance admin");
		await user.click(await screen.findByRole("button", { name: "Grant instance admin" }));

		const dialog = await screen.findByRole("dialog", { name: "Confirm access" });
		expect(dialog.textContent).toContain("sign-in from the last 5 minutes");
		expect(dialog.textContent).toContain("not a second factor");
		expect(screen.getByRole("button", { name: "Continue with GitLab" })).not.toBeNull();
		// Signing in with GitHub here would resolve a different account and end this session.
		expect(screen.queryByRole("button", { name: "Continue with GitHub" })).toBeNull();

		// The ask replaces the confirmation it came from rather than stacking on top of it.
		expect(screen.queryByText("Grant instance admin?")).toBeNull();

		await user.click(screen.getByRole("button", { name: "Close" }));
		await waitFor(() =>
			expect(screen.queryByRole("dialog", { name: "Confirm access" })).toBeNull(),
		);
		// Dismissing the ask abandons the action; it must not be replayed against the same refusal.
		expect(roleChanges).toBe(1);
	});

	it("asks for a fresh sign-in instead of reporting a force sign-out failure", async () => {
		server.use(http.delete("*/admin/users/:id/sessions", () => stepUpRefusal()));

		renderRouteAt("/admin/users");
		const user = await chooseRowAction("Actions for Ada Lovelace", "Force sign out");
		await user.click(await screen.findByRole("button", { name: "Force sign out" }));

		await screen.findByRole("dialog", { name: "Confirm access" });
		expect(screen.queryByText(/could not sign the user out/u)).toBeNull();
	});

	it("says nothing about a recent sign-in when the refusal is an ordinary one", async () => {
		server.use(
			http.patch("*/admin/users/:id", () =>
				HttpResponse.json(
					{
						status: 409,
						detail: "You cannot revoke the last admin. Grant admin to another account first.",
					},
					{ status: 409 },
				),
			),
		);

		renderRouteAt("/admin/users");
		const user = await chooseRowAction("Actions for Ada Lovelace", "Grant instance admin");
		await user.click(await screen.findByRole("button", { name: "Grant instance admin" }));

		const refusal = await screen.findByRole("alert");
		expect(refusal.textContent).toBe(
			"You cannot revoke the last admin. Grant admin to another account first.",
		);
		expect(screen.queryByRole("dialog", { name: "Confirm access" })).toBeNull();
	});
});
