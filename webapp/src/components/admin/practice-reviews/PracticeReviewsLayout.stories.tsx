import type { Meta, StoryObj } from "@storybook/react-vite";
import { PageLayout } from "@/components/core/PageLayout";
import { withStandardPage } from "@/stories/decorators";
import { PracticeReviewsHeader } from "./PracticeReviewsLayout";

const meta = {
	title: "Admin/Practice reviews/Navigation",
	component: PracticeReviewsHeader,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 1440] },
		viewport: { defaultViewport: "reflow" },
	},
	args: {
		workspaceSlug: "demo",
		activeSection: "reviews",
	},
	decorators: [
		(Story) => (
			<PageLayout>
				<Story />
			</PageLayout>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReviewsHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Reviews: Story = {};

export const Findings: Story = {
	args: { activeSection: "findings" },
};

export const Delivery: Story = {
	args: { activeSection: "delivery" },
};

export const ReviewedWork: Story = {
	args: { activeSection: undefined },
};
