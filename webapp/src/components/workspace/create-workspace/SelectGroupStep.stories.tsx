import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { GitLabGroup } from "@/api/types.gen";
import { SelectGroupStep } from "./SelectGroupStep";
import { initialWizardState, WizardContext, type WizardState } from "./wizard-context";

function withWizardState(overrides: Partial<WizardState>) {
	const state: WizardState = { ...initialWizardState, step: 2, ...overrides };
	return function WizardDecorator(Story: React.ComponentType) {
		return (
			<WizardContext.Provider value={{ state, dispatch: fn() }}>
				<Story />
			</WizardContext.Provider>
		);
	};
}

const makeGroup = (
	id: number,
	name: string,
	fullPath: string,
	opts: Partial<GitLabGroup> = {},
): GitLabGroup => ({
	id,
	name,
	fullPath,
	avatarUrl: `https://gitlab.com/uploads/-/system/group/avatar/${id}/avatar.png`,
	visibility: "private",
	...opts,
});

const sampleGroups: GitLabGroup[] = [
	makeGroup(1, "Hephaestus", "ls1intum/hephaestus", { visibility: "public" }),
	makeGroup(2, "Artemis", "ls1intum/artemis", { visibility: "internal" }),
	makeGroup(3, "Athena", "ls1intum/athena"),
	makeGroup(4, "IRIS", "ls1intum/iris", { visibility: "public" }),
	makeGroup(5, "Design System", "ls1intum/design-system"),
];

/**
 * Group picker step in the GitLab workspace creation wizard.
 * Displays a searchable list of GitLab groups with radio selection,
 * avatars, and visibility badges.
 */
const meta = {
	component: SelectGroupStep,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Searchable radio-group list of GitLab groups. Uses WizardContext for state and dispatch.",
			},
		},
	},
	decorators: [
		withWizardState({ groups: sampleGroups }),
		(Story) => (
			<div className="w-96">
				<Story />
			</div>
		),
	],
	tags: ["autodocs"],
} satisfies Meta<typeof SelectGroupStep>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with multiple groups, none selected.
 */
export const Default: Story = {};

/**
 * A group is pre-selected (highlighted row).
 */
export const WithSelection: Story = {
	decorators: [withWizardState({ groups: sampleGroups, selectedGroup: sampleGroups[0] })],
};

/**
 * Empty groups list — shows the "no groups found" message.
 */
export const EmptyGroups: Story = {
	decorators: [withWizardState({ groups: [] })],
};

/**
 * Single group available.
 */
export const SingleGroup: Story = {
	decorators: [withWizardState({ groups: [sampleGroups[0]] })],
};

/**
 * Many groups to demonstrate scroll behavior.
 */
export const ManyGroups: Story = {
	decorators: [
		withWizardState({
			groups: Array.from({ length: 20 }, (_, i) =>
				makeGroup(i + 100, `Group ${i + 1}`, `org/group-${i + 1}`, {
					visibility: i % 3 === 0 ? "public" : i % 3 === 1 ? "internal" : "private",
				}),
			),
		}),
	],
};

/**
 * Groups without avatar URLs — shows fallback initials.
 */
export const NoAvatars: Story = {
	decorators: [
		withWizardState({
			groups: sampleGroups.map((g) => ({ ...g, avatarUrl: undefined })),
		}),
	],
};
