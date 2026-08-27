import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeGroupReviewRun } from "@/api/types.gen";
import { ReviewRunCard } from "./ReviewRunCard";

const run: PracticeGroupReviewRun = {
	reviewId: "00000000-0000-0000-0000-000000000101",
	reviewedAt: new Date("2026-08-12T10:26:00Z"),
	reviewedWork: {
		type: "PULL_REQUEST",
		id: 902,
		provider: "GITHUB",
		number: 902,
		title: "Split the practice catalog loader per workspace",
		repositoryName: "HephaestusTest/practice-validation",
		url: "https://github.com/HephaestusTest/practice-validation/pull/902",
	},
	observations: [
		{
			observationId: "00000000-0000-0000-0000-000000000102",
			practiceSlug: "records-decisions",
			practiceName: "Record significant decisions",
			title: "The workspace trade-off is documented",
			presence: "PRESENT",
			assessment: "GOOD",
		},
		{
			observationId: "00000000-0000-0000-0000-000000000103",
			practiceSlug: "keeps-docs-current",
			practiceName: "Keep linked documentation current",
			title: "A linked page still uses the old component name",
			presence: "ABSENT",
			assessment: "BAD",
			severity: "MINOR",
		},
	],
};

const meta = {
	title: "Profile/Review runs/Review run card",
	component: ReviewRunCard,
	parameters: { layout: "padded" },
	decorators: [
		(Story) => (
			<ol>
				<Story />
			</ol>
		),
	],
} satisfies Meta<typeof ReviewRunCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { run } };
