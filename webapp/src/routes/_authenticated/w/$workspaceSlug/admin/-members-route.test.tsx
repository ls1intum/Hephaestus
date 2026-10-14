import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import type {
	AssignRoleRequest,
	SetMemberHiddenRequest,
	WorkspaceMembership,
} from "@/api/types.gen";
import type { Wire } from "@/lib/dates";
import { ownMembership } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt, renderRouteAtWithRouter } from "@/test/router-harness";

vi.setConfig({ testTimeout: 15_000 });

const WORKSPACE = {
	id: 1,
	workspaceSlug: "acme",
	displayName: "Acme",
	providerType: "GITHUB",
	status: "ACTIVE",
	practicesEnabled: true,
};

function member(
	accountId: string,
	account: Partial<Wire<WorkspaceMembership>["account"]>,
	overrides: Partial<Wire<WorkspaceMembership>> = {},
): Wire<WorkspaceMembership> {
	return {
		accountId,
		account: {
			id: accountId,
			status: "ACTIVE",
			displayName: null,
			avatarUrl: null,
			identities: [],
			...account,
		},
		role: "MEMBER",
		joinedAt: "2026-03-02T09:00:00Z",
		leftAt: null,
		hidden: false,
		reviewSelected: true,
		eligibleForPracticeReview: true,
		...overrides,
	};
}

/** One account that signed in with GitHub and with GitLab. */
const ADA = member("7", {
	displayName: "Ada Lovelace",
	identities: [
		{ provider: "GITHUB", login: "ada", displayName: "Ada Lovelace", avatarUrl: null },
		{ provider: "GITLAB", login: "ada-lrz", displayName: "Ada (LRZ)", avatarUrl: null },
	],
});

/** Roster sync created this account; the person has not signed in and has no name of their own. */
const SAM = member("8", {
	status: "UNCLAIMED",
	identities: [{ provider: "GITHUB", login: "sam", displayName: "Sam Unclaimed", avatarUrl: null }],
});

function mockMembersRoute(members: Wire<WorkspaceMembership>[] = [ADA, SAM]) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces", () => HttpResponse.json([WORKSPACE])),
		http.get("*/workspaces/:workspaceSlug", () => HttpResponse.json(WORKSPACE)),
		http.get("*/workspaces/:workspaceSlug/connections/catalog", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(members)),
		http.get("*/workspaces/:workspaceSlug/teams", () => HttpResponse.json([])),
	);
}

function rowOf(name: string): HTMLElement {
	const row = screen.getByText(name, { selector: "div" }).closest("tr");
	if (row === null) {
		throw new Error(`No row for ${name}`);
	}
	return row;
}

describe("workspace members route", () => {
	it("does not trap sidebar navigation", async () => {
		mockMembersRoute();
		const { router } = renderRouteAtWithRouter("/w/acme/admin/members");

		await screen.findByRole("heading", { name: "Members" }, ROUTE_RENDER_WAIT);
		fireEvent.click(await screen.findByRole("link", { name: "Settings" }));

		await waitFor(() => expect(router.state.location.pathname).toBe("/w/acme/admin/settings"));
	});

	it("shows one row per account, with every identity and unclaimed accounts marked", async () => {
		mockMembersRoute();
		renderRouteAt("/w/acme/admin/members");

		await screen.findByText("Ada Lovelace", { selector: "div" }, ROUTE_RENDER_WAIT);
		const ada = within(rowOf("Ada Lovelace"));
		ada.getByText("ada");
		ada.getByText("ada-lrz");
		screen.getByText("Showing 2 of 2 members");

		// The unclaimed account has no name of its own, so its workspace identity names it.
		const sam = within(rowOf("Sam Unclaimed"));
		sam.getByText("Unclaimed");
		expect(sam.queryByText("Active")).toBeNull();
	});

	it("reads every page of members before it counts them", async () => {
		const many = Array.from({ length: 100 }, (_, index) =>
			member(String(index + 100), { displayName: `Member ${String(index).padStart(3, "0")}` }),
		);
		const pageContents: Record<string, Wire<WorkspaceMembership>[]> = { "0": many, "1": [SAM] };
		const pages: (string | null)[] = [];
		mockMembersRoute();
		server.use(
			http.get("*/workspaces/:workspaceSlug/members", ({ request }) => {
				const page = new URL(request.url).searchParams.get("page");
				pages.push(page);
				return HttpResponse.json(pageContents[String(page)]);
			}),
		);
		renderRouteAt("/w/acme/admin/members");

		await screen.findByText("Showing 10 of 101 members", undefined, ROUTE_RENDER_WAIT);
		expect(pages).toStrictEqual(["0", "1"]);
	});

	it("hides a member by account id", async () => {
		let hidden: { accountId: unknown; body: SetMemberHiddenRequest } | undefined;
		mockMembersRoute();
		server.use(
			http.patch<{ accountId: string }, SetMemberHiddenRequest>(
				"*/workspaces/:workspaceSlug/members/:accountId/hidden",
				async ({ params, request }) => {
					hidden = { accountId: params.accountId, body: await request.json() };
					return HttpResponse.json({ ...SAM, hidden: true });
				},
			),
		);
		renderRouteAt("/w/acme/admin/members");

		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: "Hide Sam Unclaimed from workspace activity" },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() => expect(hidden).toStrictEqual({ accountId: "8", body: { hidden: true } }));
	});

	it("changes a role by account id and refreshes the list", async () => {
		let assigned: AssignRoleRequest | undefined;
		let members = [ADA, SAM];
		let reads = 0;
		mockMembersRoute();
		server.use(
			http.get("*/workspaces/:workspaceSlug/members", () => {
				reads += 1;
				return HttpResponse.json(members);
			}),
			http.post<never, AssignRoleRequest>(
				"*/workspaces/:workspaceSlug/members/assign",
				async ({ request }) => {
					const body = await request.json();
					assigned = body;
					members = [ADA, { ...SAM, role: body.role }];
					return HttpResponse.json({ ...SAM, role: body.role });
				},
			),
		);
		renderRouteAt("/w/acme/admin/members");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("combobox", { name: "Role of Sam Unclaimed" }, ROUTE_RENDER_WAIT),
		);
		await user.click(await screen.findByRole("option", { name: "Admin" }));

		await waitFor(() => expect(assigned).toStrictEqual({ accountId: "8", role: "ADMIN" }));
		await waitFor(() => expect(reads).toBe(2));
		await waitFor(() =>
			within(screen.getByRole("combobox", { name: "Role of Sam Unclaimed" })).getByText("Admin"),
		);
	});

	it("keeps the list and says so when a role change is refused", async () => {
		mockMembersRoute();
		server.use(
			http.post("*/workspaces/:workspaceSlug/members/assign", () =>
				HttpResponse.json(
					{ status: 409, detail: "A workspace needs at least one owner." },
					{ status: 409 },
				),
			),
		);
		renderRouteAt("/w/acme/admin/members");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("combobox", { name: "Role of Ada Lovelace" }, ROUTE_RENDER_WAIT),
		);
		await user.click(await screen.findByRole("option", { name: "Member" }));

		await screen.findByText("We could not change the role");
		screen.getByText("Ada Lovelace", { selector: "div" });
	});
});
