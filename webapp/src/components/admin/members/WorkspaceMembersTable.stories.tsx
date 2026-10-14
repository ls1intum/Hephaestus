import type { Meta, StoryContext, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";

import { expectNoPageOverflow } from "@/stories/reflow";

import {
	memberAccount,
	membership,
	mockMemberships,
	mockTeams,
	multiProviderMember,
	unclaimedMember,
} from "./fixtures";
import { workspaceMemberRows } from "./workspace-members";
import { WorkspaceMembersTable } from "./WorkspaceMembersTable";

const members = workspaceMemberRows(mockMemberships, mockTeams);

type Scope = StoryContext["canvas"];

function rowOf(canvas: Scope, name: string): HTMLElement {
	const row = canvas.getByText(name, { selector: "div" }).closest("tr");
	if (row === null) {
		throw new Error(`No row for ${name}`);
	}
	return row;
}

const meta = {
	component: WorkspaceMembersTable,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	args: {
		members,
		teams: mockTeams,
		onToggleHidden: fn(),
		onRoleChange: fn(),
		view: { q: "", team: "all", sort: "name", desc: false, page: 0, size: 10 },
		onViewChange: fn(),
	},
} satisfies Meta<typeof WorkspaceMembersTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args, canvas }) => {
		// The sort lives in the URL, so a column is always sorted — and which one has to be legible
		// to a screen reader, not only to whoever can see the arrow.
		await expect(canvas.getByRole("columnheader", { name: "Name" })).toHaveAttribute(
			"aria-sort",
			"ascending",
		);
		await expect(canvas.getByRole("columnheader", { name: "Username" })).toHaveAttribute(
			"aria-sort",
			"none",
		);

		await userEvent.click(canvas.getByRole("button", { name: "Username" }));
		await expect(args.onViewChange).toHaveBeenCalledWith({
			sort: "username",
			desc: false,
			page: 0,
		});
	},
};

/** One account with a GitHub and a GitLab identity is one member row that lists both logins. */
export const MultiProviderAccount: Story = {
	args: { members: workspaceMemberRows([multiProviderMember], mockTeams) },
	play: async ({ canvas }) => {
		const row = within(rowOf(canvas, "Alice Johnson"));
		await expect(row.getByText("alice")).toBeVisible();
		await expect(row.getByText("ajohnson")).toBeVisible();
		await expect(row.getByText("(GitLab)")).toBeVisible();
		await expect(canvas.getByText("Showing 1 of 1 member")).toBeVisible();
	},
};

/**
 * Roster sync created this account before the person signed in. It is named by its workspace
 * identity and marked Unclaimed; nothing claims a sign-in or a choice the person did not make.
 */
export const UnclaimedAccount: Story = {
	args: { members: workspaceMemberRows([unclaimedMember], mockTeams) },
	play: async ({ canvas }) => {
		const row = within(rowOf(canvas, "Bob Smith"));
		await expect(row.getByText("Unclaimed")).toBeVisible();
		await expect(row.queryByText("Active")).not.toBeInTheDocument();
	},
};

/** A member who left keeps their row with the day they left, so results stay attributed. */
export const MemberWhoLeft: Story = {
	args: {
		members: workspaceMemberRows(
			[
				membership(
					memberAccount("21", {
						displayName: "Erin Former",
						identities: [{ provider: "GITHUB", login: "erin", displayName: null, avatarUrl: null }],
					}),
					{ leftAt: new Date("2026-06-30T12:00:00Z") },
				),
			],
			[],
		),
	},
	play: async ({ canvas }) => {
		await expect(within(rowOf(canvas, "Erin Former")).getByText(/^Left /u)).toBeVisible();
	},
};

/** An admin picks another role; the change targets the member's account. */
export const ChangeRole: Story = {
	play: async ({ args, canvas }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Role of Bob Smith" }));
		await userEvent.click(await screen.findByRole("option", { name: "Admin" }));
		await expect(args.onRoleChange).toHaveBeenCalledWith(unclaimedMember.accountId, "ADMIN");
	},
};

/** Only the member whose role change is in flight waits. */
export const RoleChangePending: Story = {
	args: { pendingRoleAccountId: unclaimedMember.accountId },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("combobox", { name: "Role of Bob Smith" })).toBeDisabled();
		await expect(canvas.getByRole("combobox", { name: "Role of Alice Johnson" })).toBeEnabled();
	},
};

/** A team lists provider logins, so the filter finds the account through either identity. */
export const FilteredByTeam: Story = {
	args: {
		view: { q: "", team: "3", sort: "name", desc: false, page: 0, size: 10 },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Alice Johnson")).toBeVisible();
		await expect(canvas.queryByText("Charlie Brown")).not.toBeInTheDocument();
	},
};

/** 200% zoom on a laptop: the toolbar's search, filter and columns do not fit one row, so they wrap. */
export const AtTwoHundredPercentZoom: Story = {
	parameters: { viewport: { defaultViewport: "zoom200" } },
	play: expectNoPageOverflow,
};

export const Loading: Story = {
	args: {
		isLoading: true,
		members: [],
	},
};

export const Empty: Story = {
	args: {
		members: [],
		isLoading: false,
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("This workspace has no members yet.")).toBeVisible();
	},
};

/** Without admin controls, roles read as words and nothing can be changed. */
export const ReadOnly: Story = {
	args: { onRoleChange: undefined, onToggleHidden: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("combobox", { name: /^Role of/u })).not.toBeInTheDocument();
		await expect(canvas.getByText("Owner")).toBeVisible();
	},
};

export const ManyMembers: Story = {
	args: {
		members: workspaceMemberRows(
			Array.from({ length: 50 }, (_, i) =>
				membership(
					memberAccount(String(i + 100), {
						displayName: `User ${i + 1}`,
						identities: [
							{ provider: "GITHUB", login: `user${i + 1}`, displayName: null, avatarUrl: null },
						],
					}),
				),
			),
			mockTeams,
		),
	},
};
