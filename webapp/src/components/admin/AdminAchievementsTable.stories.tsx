import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import { expectGenuinelyDisabled } from "@/test/controls";
import { AdminAchievementsTable } from "./AdminAchievementsTable";
import type { ExtendedUserTeams } from "./types";

function member(index: number): ExtendedUserTeams {
	return {
		id: index,
		login: `member${index}`,
		name: `Member ${index}`,
		hidden: false,
		url: `https://github.com/member${index}`,
		teams: [],
		user: {
			id: `user-${index}`,
			name: `Member ${index}`,
			login: `member${index}`,
			email: `member${index}@example.com`,
		},
	};
}

/** Workspace-admin view for re-running a member's achievement calculation. */
const meta = {
	component: AdminAchievementsTable,
	parameters: { layout: "fullscreen" },
	args: {
		users: Array.from({ length: 8 }, (_, index) => member(index + 1)),
		workspaceSlug: "acme",
		onRecalculate: fn(),
		recalculatingUsers: new Set<string>(),
	},
} satisfies Meta<typeof AdminAchievementsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: { users: [], isLoading: true },
};

export const NoMembers: Story = {
	args: { users: [] },
};

/** A row mid-recalculation keeps its own button busy; the others stay usable. */
export const Recalculating: Story = {
	args: { recalculatingUsers: new Set(["member2"]) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("button", { name: /Recalculating/ })).toBeDisabled();
		await expect(canvas.getAllByRole("button", { name: "Recalculate" })[0]).toBeEnabled();
	},
};

/**
 * Thirty members at ten a page, so the pager is on screen. It is the shared `TablePagination`, whose
 * boundary control is a real `<button disabled>` rather than an anchor dimmed with
 * `pointer-events-none` — a dimmed anchor stays in the tab order and is announced as an available
 * control, which is the WCAG 2.2 SC 4.1.2 failure.
 */
export const ManyMembers: Story = {
	args: { users: Array.from({ length: 30 }, (_, index) => member(index + 1)) },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Go to previous page" }));
		await expect(canvas.getByRole("button", { name: "Go to next page" })).toBeEnabled();
		await expect(canvas.getByRole("button", { name: "Go to page 1" })).toHaveAttribute(
			"aria-current",
			"page",
		);
	},
};
