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
			description:
				"Intrinsic width and height in pixels, set as SVG attributes. They are presentation hints, so any CSS width or height on the icon wins over them.",
		},
		animated: {
			control: "boolean",
			description: "Whether to enable cute animations (floating, blinking, etc.)",
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
				story: "Icon without animations for accessibility or performance reasons.",
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

export const BrandExport: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		docs: {
			description: {
				story:
					"Capture surfaces for `pnpm --filter webapp run export:brand-assets`, which screenshots them into `docs/static/img/brand/`. Colors are literal white and black so the export matches the hammer marks and never follows the Storybook theme.",
			},
		},
	},
	render: () => (
		<div className="flex gap-8">
			<div
				data-brand-export="heph-avatar"
				className="flex size-[512px] items-center justify-center bg-white text-black"
			>
				<MentorIcon size={400} pad={2} animated={false} />
			</div>
			<div
				data-brand-export="heph-avatar-transparent"
				className="flex size-[512px] items-center justify-center bg-transparent text-black"
			>
				<MentorIcon size={400} pad={2} animated={false} />
			</div>
		</div>
	),
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
				<span>Without animations (manual override)</span>
			</div>
		</div>
	),
	parameters: {
		docs: {
			description: {
				story: "The icon respects user accessibility preferences for reduced motion.",
			},
		},
	},
};
