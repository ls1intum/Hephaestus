import type { Meta, StoryObj } from "@storybook/react";
import { expect, within } from "storybook/test";
import type { AdminWorkspaceView } from "@/api/types.gen";
import { AdminWorkspacesTable } from "./AdminWorkspacesTable";

const workspaces: AdminWorkspaceView[] = [
	{
		id: 1,
		workspaceSlug: "aet",
		displayName: "AET",
		status: "ACTIVE",
		accountLogin: "aet-org",
		providerType: "GITHUB",
		ownerLogin: "octocat",
		memberCount: 42,
		createdAt: new Date("2026-01-15T00:00:00Z"),
	},
	{
		id: 2,
		workspaceSlug: "intro-course",
		displayName: "Intro Course",
		status: "SUSPENDED",
		accountLogin: "ase/ios",
		providerType: "GITLAB",
		memberCount: 0,
		createdAt: new Date("2026-03-01T00:00:00Z"),
	},
];

const meta = {
	component: AdminWorkspacesTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { workspaces, isLoading: false, isError: false, hasSearch: false },
} satisfies Meta<typeof AdminWorkspacesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Metadata-only rows: provider, owner, member count, status. */
export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("AET")).toBeInTheDocument();
		await expect(canvas.getByText("SUSPENDED")).toBeInTheDocument();
		// Owner falls back to an em dash when there is no OWNER member.
		await expect(canvas.getByText("octocat")).toBeInTheDocument();
	},
};

/** Empty state under an active search filter. */
export const EmptyWithSearch: Story = {
	args: { workspaces: [], hasSearch: true },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No matching workspaces.")).toBeInTheDocument();
	},
};
