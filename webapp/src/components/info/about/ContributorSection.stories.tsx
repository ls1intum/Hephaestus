import type { Meta, StoryObj } from "@storybook/react";
import { ContributorSection } from "./ContributorSection";

/**
 * ContributorSection component for displaying project contributors with loading and error states.
 * Features error handling and integrates with the ContributorGrid component.
 */
const meta = {
	component: ContributorSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	argTypes: {
		contributors: {
			description: "Array of contributor objects",
		},
		isLoading: {
			control: "boolean",
			description: "Whether contributors are currently being loaded",
		},
		isError: {
			control: "boolean",
			description: "Whether there was an error loading contributors",
		},
	},
} satisfies Meta<typeof ContributorSection>;

export default meta;
type Story = StoryObj<typeof meta>;

const MOCK_CONTRIBUTORS = [
	{
		id: 1,
		login: "contributor1",
		name: "Alice Developer",
		avatarUrl: "https://i.pravatar.cc/150?img=1",
		htmlUrl: "https://github.com/contributor1",
	},
	{
		id: 2,
		login: "contributor2",
		name: "Bob Builder",
		avatarUrl: "https://i.pravatar.cc/150?img=2",
		htmlUrl: "https://github.com/contributor2",
	},
	{
		id: 3,
		login: "contributor3",
		name: "Charlie Coder",
		avatarUrl: "https://i.pravatar.cc/150?img=3",
		htmlUrl: "https://github.com/contributor3",
	},
	{
		id: 4,
		login: "contributor4",
		name: "Dana Designer",
		avatarUrl: "https://i.pravatar.cc/150?img=4",
		htmlUrl: "https://github.com/contributor4",
	},
];

/**
 * Default contributor section with multiple contributors loaded.
 */
export const Default: Story = {
	args: {
		contributors: MOCK_CONTRIBUTORS,
		isLoading: false,
		isError: false,
	},
};

/**
 * Contributor section in loading state.
 */
export const IsLoading: Story = {
	args: {
		contributors: [],
		isLoading: true,
		isError: false,
	},
};

/**
 * Contributor section in error state.
 */
export const IsError: Story = {
	args: {
		contributors: [],
		isLoading: false,
		isError: true,
	},
};

/**
 * Contributor section with single contributor.
 */
export const SingleContributor: Story = {
	args: {
		contributors: [MOCK_CONTRIBUTORS[0]],
		isLoading: false,
		isError: false,
	},
};

/**
 * Contributor section with many contributors to test grid layout.
 */
export const ManyContributors: Story = {
	args: {
		contributors: [
			...MOCK_CONTRIBUTORS,
			{
				id: 5,
				login: "contributor5",
				name: "Erin Engineer",
				avatarUrl: "https://i.pravatar.cc/150?img=5",
				htmlUrl: "https://github.com/contributor5",
			},
			{
				id: 6,
				login: "contributor6",
				name: "Frank Frontend",
				avatarUrl: "https://i.pravatar.cc/150?img=6",
				htmlUrl: "https://github.com/contributor6",
			},
			{
				id: 7,
				login: "contributor7",
				name: "Grace Golang",
				avatarUrl: "https://i.pravatar.cc/150?img=7",
				htmlUrl: "https://github.com/contributor7",
			},
			{
				id: 8,
				login: "contributor8",
				name: "Henry Hardware",
				avatarUrl: "https://i.pravatar.cc/150?img=8",
				htmlUrl: "https://github.com/contributor8",
			},
		],
		isLoading: false,
		isError: false,
	},
};
