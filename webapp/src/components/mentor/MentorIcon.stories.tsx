import type { Meta, StoryObj } from "@storybook/react";
import { MentorIcon } from "./MentorIcon";

const meta: Meta<typeof MentorIcon> = {
	component: MentorIcon,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		size: {
			control: { type: "range", min: 12, max: 64, step: 2 },
			description: "Size of the icon in pixels",
		},
		animated: {
			control: "boolean",
			description:
				"Whether to enable cute animations (floating, blinking, etc.)",
		},
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		size: 24,
		animated: true,
	},
};

export const Small: Story = {
	args: {
		size: 16,
		animated: true,
	},
};

export const Large: Story = {
	args: {
		size: 48,
		animated: true,
	},
};

export const StaticIcon: Story = {
	args: {
		size: 24,
		animated: false,
	},
	parameters: {
		docs: {
			description: {
				story:
					"Icon without animations for accessibility or performance reasons.",
			},
		},
	},
};

export const MultipleIcons: Story = {
	render: () => (
		<div className="flex items-center gap-4">
			<MentorIcon size={16} animated={true} />
			<MentorIcon size={24} animated={true} />
			<MentorIcon size={32} animated={true} />
			<MentorIcon size={48} animated={true} />
		</div>
	),
	parameters: {
		docs: {
			description: {
				story: "Different sizes showing consistent design at various scales.",
			},
		},
	},
};

export const AccessibilityPreference: Story = {
	render: () => (
		<div className="space-y-4">
			<div className="flex items-center gap-2">
				<MentorIcon size={24} animated={true} />
				<span>With animations (default)</span>
			</div>
			<div className="flex items-center gap-2">
				<MentorIcon size={24} animated={false} />
				<span>Without animations (respects prefers-reduced-motion)</span>
			</div>
		</div>
	),
	parameters: {
		docs: {
			description: {
				story:
					"The icon respects user accessibility preferences for reduced motion.",
			},
		},
	},
};
