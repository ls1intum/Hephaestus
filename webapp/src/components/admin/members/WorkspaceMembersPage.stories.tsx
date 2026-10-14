import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow, expectTablesScrollInPlace } from "@/stories/reflow";

import { mockMemberships, mockTeams } from "./fixtures";
import { workspaceMemberRows } from "./workspace-members";
import { WorkspaceMembersPage } from "./WorkspaceMembersPage";

const members = workspaceMemberRows(mockMemberships, mockTeams);

const meta = {
	component: WorkspaceMembersPage,
	parameters: {
		layout: "fullscreen",
	},
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		members,
		teams: mockTeams,
		isLoading: false,
		onToggleHidden: fn(),
		onRoleChange: fn(),
		view: { q: "", team: "all", sort: "name", desc: false, page: 0, size: 10 },
		onViewChange: fn(),
		renderPageLink: (page, props) => <a {...props} href={`?page=${page}`} />,
	},
} satisfies Meta<typeof WorkspaceMembersPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Loading: Story = {
	args: {
		members: [],
		teams: [],
		isLoading: true,
	},
};

export const Empty: Story = {
	args: {
		members: [],
		isLoading: false,
	},
};

/** A failed read keeps the page and offers Retry, never an empty member list. */
export const Error: Story = {
	args: {
		members: [],
		error: { status: 503, detail: "Service unavailable" },
		onRetry: fn(),
	},
	play: async ({ args, canvas }) => {
		await expect(canvas.getByText("We could not load members")).toBeVisible();
		await expect(canvas.queryByText("No members found")).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(args.onRetry).toHaveBeenCalled();
	},
};

export const NoTeams: Story = {
	args: {
		members: workspaceMemberRows(mockMemberships, []),
		teams: [],
	},
};

export const MobileReflow: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async () => {
		await expectNoPageOverflow();
		await expectTablesScrollInPlace();
	},
};
