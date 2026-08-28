import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";

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

export const Default: Story = {};

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
