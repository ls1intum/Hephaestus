import type { Meta, StoryObj } from "@storybook/react";
import type { TeamInfo } from "@/api/types.gen";
import { TeamCard } from "./TeamCard";

/**
 * TeamCard shows a team's basic info and a slot for repositories/children.
 * Use it to present a team header with visibility controls.
 */
const meta = {
	component: TeamCard,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		team: {
			id: 1,
			name: "Platform Team",
			hidden: false,
			members: [{ id: 10, login: "alice" }],
			repositories: [
				{
					id: 101,
					nameWithOwner: "org/repo",
					htmlUrl: "https://github.com/org/repo",
				},
			],
		} as unknown as TeamInfo,
		memberCount: 1,
	},
} satisfies Meta<typeof TeamCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
		children: (
			<div className="text-sm text-muted-foreground">
				Repositories/children slot
			</div>
		),
	},
};

export const Hidden: Story = {
	args: {
		team: { id: 1, name: "Platform Team", hidden: true } as unknown as TeamInfo,
		memberCount: 5,
		onToggleVisibility: () => {},
		getCatalogLabels: () => [],
	},
};
