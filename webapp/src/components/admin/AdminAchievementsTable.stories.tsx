import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent } from "storybook/test";

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

const meta = {
	component: AdminAchievementsTable,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		users: Array.from({ length: 8 }, (_, index) => member(index + 1)),
		workspaceSlug: "acme",
		onRecalculate: fn(),
		recalculatingUsers: new Set<string>(),
	},
} satisfies Meta<typeof AdminAchievementsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		// Re-queried each time: sorting re-renders the header, so a held reference goes stale.
		const nameHeader = () => canvas.getByRole("columnheader", { name: "Name" });
		const sortByName = () => canvas.getByRole("button", { name: "Name" });

		await expect(nameHeader()).toHaveAttribute("aria-sort", "none");
		await userEvent.click(sortByName());
		await expect(nameHeader()).toHaveAttribute("aria-sort", "ascending");
		await userEvent.click(sortByName());
		await expect(nameHeader()).toHaveAttribute("aria-sort", "descending");

		// A column that cannot sort claims no sort state at all.
		await expect(canvas.getByRole("columnheader", { name: "Actions" })).not.toHaveAttribute(
			"aria-sort",
		);
	},
};

export const Loading: Story = {
	args: { users: [], isLoading: true },
};

export const NoMembers: Story = {
	args: { users: [] },
};

export const Recalculating: Story = {
	args: { recalculatingUsers: new Set(["member2"]) },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: /Recalculating/ })).toBeDisabled();
		await expect(canvas.getAllByRole("button", { name: "Recalculate" })[0]).toBeEnabled();
	},
};

export const ManyMembers: Story = {
	args: { users: Array.from({ length: 30 }, (_, index) => member(index + 1)) },
};
