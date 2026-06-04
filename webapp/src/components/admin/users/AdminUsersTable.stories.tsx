import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { AdminAccountView } from "@/api/types.gen";
import { AdminUsersTable } from "./AdminUsersTable";

const admin: AdminAccountView = {
	id: 1,
	displayName: "Ada Admin",
	primaryEmail: "ada@example.com",
	appRole: "APP_ADMIN",
	status: "ACTIVE",
};
const user: AdminAccountView = {
	id: 2,
	displayName: "Bob User",
	primaryEmail: "bob@example.com",
	appRole: "USER",
	status: "ACTIVE",
};
const suspended: AdminAccountView = {
	id: 3,
	displayName: "Carol Suspended",
	primaryEmail: "carol@example.com",
	appRole: "USER",
	status: "SUSPENDED",
};

/**
 * Super-admin users table (presentation-only). Rendered against fixtures so every state — loading,
 * error, empty, the self-admin lockout guard, and pagination — is reviewable in isolation.
 */
const meta = {
	component: AdminUsersTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		users: [admin, user, suspended],
		isLoading: false,
		isError: false,
		hasSearch: false,
		totalLoaded: 3,
		currentUserId: 1,
		hasNextPage: false,
		isFetchingNextPage: false,
		onLoadMore: fn(),
		onChangeRole: fn(),
		onImpersonate: fn(),
		onForceSignOut: fn(),
	},
} satisfies Meta<typeof AdminUsersTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A signed-in admin (id 1) cannot revoke their own admin or impersonate themselves. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("Ada Admin");

		// Open the signed-in admin's own action menu (row id 1).
		await userEvent.click(canvas.getByRole("button", { name: "Actions for Ada Admin" }));
		// Both self-guards render their menu item disabled, so neither action can fire. (Asserting the
		// disabled state, not a click: a disabled Base UI item has pointer-events:none so it can't be
		// clicked — the positive path is covered by ChangeAnotherUsersRole.)
		const revokeSelf = await screen.findByRole("menuitem", {
			name: /can't revoke your own admin/i,
		});
		await expect(revokeSelf).toHaveAttribute("data-disabled");
		await expect(
			screen.getByRole("menuitem", { name: /cannot impersonate self/i }),
		).toHaveAttribute("data-disabled");
	},
};

/** Acting on another account fires the role-change callback. */
export const ChangeAnotherUsersRole: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: "Actions for Bob User" }));
		await userEvent.click(await screen.findByRole("menuitem", { name: "Change role" }));
		await expect(args.onChangeRole).toHaveBeenCalledWith(user);
	},
};

export const Loading: Story = { args: { users: [], isLoading: true } };

export const Empty: Story = {
	args: { users: [], totalLoaded: 0 },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText("No users found")).toBeInTheDocument();
	},
};

/** Empty because a search matched nothing — copy nudges adjusting the term. */
export const EmptySearch: Story = {
	args: { users: [], hasSearch: true, totalLoaded: 3 },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText(/adjusting your search/i)).toBeInTheDocument();
	},
};

export const ErrorState: Story = {
	args: { users: [], isError: true },
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).getByText(/failed to load users/i)).toBeInTheDocument();
	},
};

/** More pages available — the "Load more" control is offered. */
export const HasMore: Story = {
	args: { hasNextPage: true },
	play: async ({ args, canvasElement }) => {
		await userEvent.click(within(canvasElement).getByRole("button", { name: "Load more" }));
		await expect(args.onLoadMore).toHaveBeenCalled();
	},
};
