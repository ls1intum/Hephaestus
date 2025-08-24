import type { Meta, StoryObj } from "@storybook/react";
import { GithubBadge } from "./GithubBadge";

/**
 * Component for displaying GitHub-style badges with customizable text and colors.
 * Precisely replicates the appearance of GitHub labels with proper color contrast handling
 * for both light and dark themes.
 */
const meta = {
	component: GithubBadge,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A component that displays GitHub-style labels with the exact same visual appearance and color handling as on github.com.",
			},
		},
	},
	argTypes: {
		label: {
			control: "text",
			description: "The text displayed in the badge",
		},
		color: {
			control: "text",
			description: "Hex color code without the # prefix",
		},
		className: {
			control: "text",
			description: "Additional CSS classes to apply",
		},
	},
} satisfies Meta<typeof GithubBadge>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Bug badge example with standard GitHub red color.
 * Used to indicate issues related to bugs.
 */
export const Bug: Story = {
	args: {
		label: "bug",
		color: "d73a4a",
	},
};

/**
 * Enhancement badge example with light blue color.
 * Used to indicate feature enhancement requests.
 */
export const Enhancement: Story = {
	args: {
		label: "enhancement",
		color: "a2eeef",
	},
};

/**
 * Documentation badge example with blue color.
 * Used for issues related to documentation updates.
 */
export const Documentation: Story = {
	args: {
		label: "documentation",
		color: "0075ca",
	},
};

/**
 * Good first issue badge example with purple color.
 * Used to indicate issues suitable for newcomers.
 */
export const GoodFirstIssue: Story = {
	args: {
		label: "good first issue",
		color: "7057ff",
	},
};

/**
 * Multiple badges with different colors to showcase the component in use.
 */
export const MultipleBadges: Story = {
	args: {
		label: "bug",
		color: "d73a4a",
	},
	render: (args) => (
		<div className="flex flex-wrap gap-2 max-w-[600px]">
			<GithubBadge {...args} />
			<GithubBadge label="enhancement" color="a2eeef" />
			<GithubBadge label="documentation" color="0075ca" />
			<GithubBadge label="good first issue" color="7057ff" />
			<GithubBadge label="help wanted" color="008672" />
			<GithubBadge label="priority: critical" color="ff0000" />
			<GithubBadge label="priority: high" color="ff8800" />
			<GithubBadge label="priority: medium" color="ffcc00" />
			<GithubBadge label="priority: low" color="c2e0c6" />
			<GithubBadge label="status: in progress" color="9e6a03" />
			<GithubBadge label="status: completed" color="0e8a16" />
		</div>
	),
};
