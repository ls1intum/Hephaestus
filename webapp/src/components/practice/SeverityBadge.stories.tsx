import type { Meta, StoryObj } from "@storybook/react";
import { SeverityBadge } from "./SeverityBadge";

/**
 * Color-coded badge indicating the severity level of a practice finding.
 */
const meta = {
	component: SeverityBadge,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays the severity level of a practice finding as a color-coded pill badge.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		severity: {
			control: "select",
			options: ["CRITICAL", "MAJOR", "MINOR", "INFO"],
			description: "The finding severity to display",
		},
	},
} satisfies Meta<typeof SeverityBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Critical: Story = {
	args: { severity: "CRITICAL" },
};

export const Major: Story = {
	args: { severity: "MAJOR" },
};

export const Minor: Story = {
	args: { severity: "MINOR" },
};

export const Info: Story = {
	args: { severity: "INFO" },
};
