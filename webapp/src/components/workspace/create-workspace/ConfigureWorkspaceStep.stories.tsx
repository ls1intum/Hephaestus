import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { ConfigureWorkspaceStep } from "./ConfigureWorkspaceStep";
import { initialWizardState, WizardContext, type WizardState } from "./wizard-context";

function withWizardState(overrides: Partial<WizardState>) {
	const state: WizardState = { ...initialWizardState, step: 3, ...overrides };
	return function WizardDecorator(Story: React.ComponentType) {
		return (
			<WizardContext.Provider value={{ state, dispatch: fn() }}>
				<Story />
			</WizardContext.Provider>
		);
	};
}

const defaultGroup = {
	id: 1,
	name: "Hephaestus",
	fullPath: "ls1intum/hephaestus",
	visibility: "public",
	avatarUrl: "https://gitlab.com/uploads/-/system/group/avatar/1/avatar.png",
};

/**
 * Final configuration step of the GitLab workspace creation wizard.
 * Shows display name and slug inputs with live validation,
 * plus a summary panel of all prior selections.
 */
const meta = {
	component: ConfigureWorkspaceStep,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Form step for workspace name and URL slug. Auto-generates slug from display name. Shows summary of instance, group, and token owner.",
			},
		},
	},
	decorators: [
		withWizardState({
			selectedGroup: defaultGroup,
			preflightResult: { valid: true, username: "admin-user", scopes: ["api", "read_user"] },
			serverUrl: "",
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
 * Default state — auto-populates name from group, generates slug.
 */
export const Default: Story = {};

/**
 * Pre-filled with a custom display name and slug already set.
 */
export const PreFilled: Story = {
	decorators: [
		withWizardState({
			selectedGroup: defaultGroup,
			displayName: "My Custom Workspace",
			workspaceSlug: "my-custom-workspace",
			slugManuallyEdited: true,
			preflightResult: { valid: true, username: "admin-user", scopes: ["api", "read_user"] },
		}),
	],
};

/**
 * Self-hosted GitLab instance — summary shows custom server URL.
 */
export const SelfHosted: Story = {
	decorators: [
		withWizardState({
			selectedGroup: defaultGroup,
			serverUrl: "https://gitlab.example.com",
			preflightResult: { valid: true, username: "devops-lead", scopes: ["api", "read_user"] },
		}),
	],
};

/**
 * Group with a long name to test truncation in the summary panel.
 */
export const LongGroupName: Story = {
	decorators: [
		withWizardState({
			selectedGroup: {
				...defaultGroup,
				name: "Very Long Department Name That Should Truncate",
				fullPath: "org/department/sub-team/very-long-group-path-name",
			},
			preflightResult: { valid: true, username: "admin-user", scopes: ["api", "read_user"] },
		}),
	],
};
