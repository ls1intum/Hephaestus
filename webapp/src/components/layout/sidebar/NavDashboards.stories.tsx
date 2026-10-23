import type { Decorator, Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";

import { SidebarProvider } from "@/components/ui/sidebar";

import { NavDashboards } from "./NavDashboards";
import { withSidebarFrame } from "./sidebar-story-frame";

/**
 * The sidebar folded to icons, marked as the sidebar marks itself, so what hides there (an open
 * section's pages) hides in the story too.
 */
const withIconOnlySidebar: Decorator = (Story) => (
	<SidebarProvider defaultOpen={false} className="min-h-0">
		<div className="group" data-collapsible="icon">
			<Story />
		</div>
	</SidebarProvider>
);

const meta = {
	component: NavDashboards,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "aet",
		practicesEnabled: true,
	},
	decorators: [withSidebarFrame],
} satisfies Meta<typeof NavDashboards>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The "Your practices" row folds like the admin sections; its pages, the profile first, sit under it. */
export const Default: Story = {
	play: async ({ canvas }) => {
		// The Practice profile is the workspace home, so its section leads; Activity follows it.
		const section = canvas.getByRole("button", { name: "Your practices" });
		await expect(section).toHaveAttribute("aria-expanded", "false");
		const links = canvas.getAllByRole("link");
		await expect(links.map((link) => link.textContent)).toEqual([
			"Activity",
			"Workspace activity",
			"Teams",
		]);
		await expect(canvas.getAllByRole("listitem")[0]).toContainElement(section);
	},
};

/** Opened, the profile and the workspace's view of the same groups sit under the row. */
export const PracticePagesOpen: Story = {
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Your practices" }));
		const pages = within(canvas.getByRole("list", { name: "Your practices" }));
		await expect(pages.getAllByRole("link").map((link) => link.textContent)).toEqual([
			"Practice profile",
			"Across the workspace",
		]);
		await expect(pages.getByRole("link", { name: "Practice profile" })).toHaveAttribute(
			"href",
			"/w/aet/practice-profile",
		);
		await expect(pages.getByRole("link", { name: "Across the workspace" })).toHaveAttribute(
			"href",
			"/w/aet/practices-across-the-workspace",
		);
	},
};

/** A press on the row opens its pages, the next closes them, and neither leaves the current page. */
export const PressingTheRowTogglesItsPages: Story = {
	parameters: { router: { initialUrl: "/w/aet/activity" } },
	play: async ({ canvas }) => {
		const section = canvas.getByRole("button", { name: "Your practices" });
		await expect(section).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(section);
		await expect(section).toHaveAttribute("aria-expanded", "true");
		await expect(canvas.getByRole("link", { name: "Across the workspace" })).toBeVisible();
		await userEvent.click(section);
		await expect(section).toHaveAttribute("aria-expanded", "false");
		await expect(canvas.queryByRole("link", { name: "Across the workspace" })).toBeNull();
		await expect(canvas.getByRole("link", { name: "Activity" })).toHaveAttribute("data-active");
	},
};

/** The profile's own entry under the row leads to it, and the row stays open on arrival. */
export const PressingTheProfileEntryOpensIt: Story = {
	parameters: { router: { initialUrl: "/w/aet/activity" } },
	play: async ({ canvas }) => {
		const section = canvas.getByRole("button", { name: "Your practices" });
		await userEvent.click(section);
		const pages = within(canvas.getByRole("list", { name: "Your practices" }));
		await userEvent.click(pages.getByRole("link", { name: "Practice profile" }));
		await expect(pages.getByRole("link", { name: "Practice profile" })).toHaveAttribute(
			"data-active",
		);
		await expect(canvas.getByRole("link", { name: "Activity" })).not.toHaveAttribute("data-active");
		await expect(section).toHaveAttribute("aria-expanded", "true");
		await expect(section).not.toHaveAttribute("data-active");
	},
};

/** Folded on the profile, the row stands in for it as the active entry. */
export const PracticeProfileFolded: Story = {
	parameters: { router: { initialUrl: "/w/aet/practice-profile" } },
	play: async ({ canvas }) => {
		const section = canvas.getByRole("button", { name: "Your practices" });
		await expect(section).toHaveAttribute("aria-expanded", "true");
		await expect(section).not.toHaveAttribute("data-active");
		await userEvent.click(section);
		await expect(section).toHaveAttribute("aria-expanded", "false");
		await expect(section).toHaveAttribute("data-active");
	},
};

/** Arriving on the profile with a slide-in layer open, the pages under it are already in sight. */
export const PracticeProfileWithLayerOpen: Story = {
	parameters: {
		router: {
			initialUrl:
				"/w/aet/practice-profile?detail=%5B%22practice-groups%3Aall%22%2C%22practice-group%3Atesting-discipline%22%5D",
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Your practices" })).toHaveAttribute(
			"aria-expanded",
			"true",
		);
		await expect(canvas.getByRole("link", { name: "Practice profile" })).toHaveAttribute(
			"data-active",
		);
	},
};

/** Folded to icons, nothing can unfold: the row links to the profile, and each page is one press. */
export const PracticePagesFolded: Story = {
	decorators: [withIconOnlySidebar],
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Your practices" })).toHaveAttribute(
			"href",
			"/w/aet/practice-profile",
		);
		await expect(canvas.getByRole("link", { name: "Across the workspace" })).toHaveAttribute(
			"href",
			"/w/aet/practices-across-the-workspace",
		);
	},
};

/** On the workspace view, folded to icons: its own icon is the one active entry, not the profile's. */
export const AcrossTheWorkspaceActiveFolded: Story = {
	parameters: { router: { initialUrl: "/w/aet/practices-across-the-workspace" } },
	decorators: [withIconOnlySidebar],
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Across the workspace" })).toHaveAttribute(
			"data-active",
		);
		await expect(canvas.getByRole("link", { name: "Your practices" })).not.toHaveAttribute(
			"data-active",
		);
	},
};

/** On the workspace view with the sidebar open, the view's sub entry is active, not the row or the profile. */
export const AcrossTheWorkspaceActive: Story = {
	parameters: { router: { initialUrl: "/w/aet/practices-across-the-workspace" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "Across the workspace" })).toHaveAttribute(
			"data-active",
		);
		await expect(canvas.getByRole("link", { name: "Practice profile" })).not.toHaveAttribute(
			"data-active",
		);
		await expect(canvas.getByRole("button", { name: "Your practices" })).not.toHaveAttribute(
			"data-active",
		);
	},
};

export const PracticeReviewsOff: Story = {
	args: { practicesEnabled: false },
	play: async ({ canvas }) => {
		// Activity is always on, and leads — it is the home of a workspace that does not review
		// practices. The practice pages are gone rather than leading to a page that could only explain
		// itself.
		const links = canvas.getAllByRole("link").map((link) => link.textContent);
		await expect(links).toEqual(["Activity", "Workspace activity", "Teams"]);
	},
};
