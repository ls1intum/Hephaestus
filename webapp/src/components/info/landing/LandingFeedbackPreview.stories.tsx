import type { Meta, StoryObj } from "@storybook/react";
import { LandingFeedbackPreview } from "./LandingHeroSection";

const meta = {
	component: LandingFeedbackPreview,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The deterministic export surface for the worked practice-feedback example used on the landing page and in the README.",
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof LandingFeedbackPreview>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ReadmeExport: Story = {
	render: () => (
		<div className="flex w-full justify-center bg-background">
			<div
				data-readme-export="landing-feedback-preview"
				className="w-full max-w-[744px] bg-background p-4 sm:p-8"
			>
				<div className="mx-auto w-full max-w-[680px]">
					<LandingFeedbackPreview staticMode />
				</div>
			</div>
		</div>
	),
};
