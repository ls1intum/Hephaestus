import type { Meta, StoryObj } from "@storybook/react";

import { LandingFeedbackLoop } from "./LandingFeaturesSection";

const meta = {
	component: LandingFeedbackLoop,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The deterministic export surface for the feedback loop used on the landing page and in the README.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeedbackLoop>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ReadmeDesktopExport: Story = {
	render: () => (
		<div className="flex w-full justify-center bg-background">
			<div data-readme-export="feedback-loop-desktop" className="w-[1224px] bg-background p-8">
				<LandingFeedbackLoop layout="desktop" />
			</div>
		</div>
	),
};

export const ReadmeTabletExport: Story = {
	render: () => (
		<div className="flex w-full justify-center bg-background">
			<div data-readme-export="feedback-loop-tablet" className="w-[768px] bg-background p-6">
				<LandingFeedbackLoop layout="responsive" />
			</div>
		</div>
	),
};

export const ReadmeMobileExport: Story = {
	render: () => (
		<div className="flex w-full justify-center bg-background">
			<div data-readme-export="feedback-loop-mobile" className="w-full bg-background p-4">
				<LandingFeedbackLoop layout="mobile" />
			</div>
		</div>
	),
};
