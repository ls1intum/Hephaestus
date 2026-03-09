import type { Meta, StoryObj } from "@storybook/react";
import { LabelBadge } from "./LabelBadge";

/**
 * Component for displaying repository label badges with customizable text and colors.
 * Supports both light and dark themes with proper color contrast handling.
 */
const meta = {
	component: LabelBadge,
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A component that displays repository labels with proper visual appearance and color handling for both light and dark themes.",
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
} satisfies Meta<typeof LabelBadge>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Bug badge with red color, used to indicate issues related to bugs.
 */
export const Bug: Story = {
	args: {
		label: "bug",
		color: "d73a4a",
	},
};

/**
 * Enhancement badge with light blue color for feature requests.
 */
export const Enhancement: Story = {
	args: {
		label: "enhancement",
		color: "a2eeef",
	},
};

/**
 * Documentation badge with blue color for doc-related issues.
 */
export const Documentation: Story = {
	args: {
		label: "documentation",
		color: "0075ca",
	},
};

/**
 * Good first issue badge with purple color, suitable for newcomers.
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
			<LabelBadge {...args} />
			<LabelBadge label="enhancement" color="a2eeef" />
			<LabelBadge label="documentation" color="0075ca" />
			<LabelBadge label="good first issue" color="7057ff" />
			<LabelBadge label="help wanted" color="008672" />
			<LabelBadge label="priority: critical" color="ff0000" />
			<LabelBadge label="priority: high" color="ff8800" />
			<LabelBadge label="priority: medium" color="ffcc00" />
			<LabelBadge label="priority: low" color="c2e0c6" />
			<LabelBadge label="status: in progress" color="9e6a03" />
			<LabelBadge label="status: completed" color="0e8a16" />
		</div>
	),
};
