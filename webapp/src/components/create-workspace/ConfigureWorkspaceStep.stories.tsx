import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";

import type { GitLabGroup } from "@/api/types.gen";

import { ConfigureWorkspaceStep } from "./ConfigureWorkspaceStep";
import { ProjectScopeField } from "./ProjectScopeField";
import { makeGroup, withWizardState } from "./stories-utils";

const defaultGroup: GitLabGroup = makeGroup(1, "Hephaestus", "ls1intum/hephaestus", {
	visibility: "public",
});

/** The project choice as the step opens: every project, no list read. */
const allProjects = (
	<ProjectScopeField
		scope="ALL"
		onScopeChange={fn()}
		selectedIds={[]}
		onSelectedChange={fn()}
		choices={{ status: "loading" }}
		onSearch={fn()}
		onPageChange={fn()}
	/>
);

/**
 * Final configuration step of the GitLab workspace creation wizard.
 * Shows display name and slug inputs with live validation, the projects to monitor,
 * plus a summary panel of all prior selections.
 */
const meta = {
	component: ConfigureWorkspaceStep,
	args: { projects: allProjects },
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Form step for workspace name, URL slug and the projects to monitor. Auto-generates slug from display name. Shows summary of instance, group, projects and token owner.",
			},
		},
	},
	decorators: [
		withWizardState({
			step: 3,
			selectedGroup: defaultGroup,
			preflightResult: { valid: true, username: "admin-user" },
		}),
		(Story) => (
			<div className="w-96">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof ConfigureWorkspaceStep>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state — auto-populates name from group, generates slug, and monitors all projects.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("radio", { name: /^All projects/u })).toBeChecked();
		await expect(canvas.getByText("All projects", { selector: "dd" })).toBeVisible();
	},
};

/**
 * Two projects chosen: the summary counts them, whichever page they were chosen on.
 */
export const SelectedProjects: Story = {
	args: {
		projects: (
			<ProjectScopeField
				scope="SELECTED"
				onScopeChange={fn()}
				selectedIds={[11, 13]}
				onSelectedChange={fn()}
				choices={{
					status: "ready",
					projects: [
						{ id: 11, name: "API", fullPath: "ls1intum/hephaestus/api" },
						{ id: 12, name: "Docs", fullPath: "ls1intum/hephaestus/docs" },
					],
					page: 1,
					nextPage: 2,
					search: "",
				}}
				onSearch={fn()}
				onPageChange={fn()}
			/>
		),
	},
	decorators: [
		withWizardState({
			step: 3,
			selectedGroup: defaultGroup,
			preflightResult: { valid: true, username: "admin-user" },
			projectScope: "SELECTED",
			selectedProjectIds: [11, 13],
		}),
	],
	play: async ({ canvas }) => {
		await expect(canvas.getByText("2 selected projects", { selector: "dd" })).toBeVisible();
		await expect(canvas.getByRole("checkbox", { name: "API" })).toBeChecked();
	},
};

/**
 * Pre-filled with a custom display name and slug already set.
 */
export const PreFilled: Story = {
	decorators: [
		withWizardState({
			step: 3,
			selectedGroup: defaultGroup,
			displayName: "My Custom Workspace",
			workspaceSlug: "my-custom-workspace",
			slugManuallyEdited: true,
			preflightResult: { valid: true, username: "admin-user" },
		}),
	],
};

/**
 * Self-hosted GitLab instance — summary shows custom server URL.
 */
export const SelfHosted: Story = {
	decorators: [
		withWizardState({
			step: 3,
			selectedGroup: defaultGroup,
			serverUrl: "https://gitlab.example.com",
			preflightResult: { valid: true, username: "devops-lead" },
		}),
	],
};

/**
 * Group with a long name to test truncation in the summary panel.
 */
export const LongGroupName: Story = {
	decorators: [
		withWizardState({
			step: 3,
			selectedGroup: {
				...defaultGroup,
				name: "Very Long Department Name That Should Truncate",
				fullPath: "org/department/sub-team/very-long-group-path-name",
			},
			preflightResult: { valid: true, username: "admin-user" },
		}),
	],
};
