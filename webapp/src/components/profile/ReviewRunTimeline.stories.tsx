import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeGroupReviewRun } from "@/api/types.gen";
import { ReviewRunTimeline } from "./ReviewRunTimeline";

const runs: PracticeGroupReviewRun[] = [
	{
		reviewId: "00000000-0000-0000-0000-000000000101",
		reviewedAt: new Date("2026-08-12T10:26:00Z"),
		reviewedWork: {
			type: "scm.pull_request",
			id: 902,
			provider: "GITHUB",
			number: 902,
			title: "Split the practice catalog loader per workspace",
			repositoryName: "HephaestusTest/practice-validation",
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
		],
	},
	{
		reviewId: "00000000-0000-0000-0000-000000000201",
		reviewedAt: new Date("2026-08-09T16:40:00Z"),
		reviewedWork: {
			type: "chat.conversation_thread",
			id: 42,
			provider: "SLACK",
			channelName: "dev-hephaestus",
		},
		observations: [
			{
				observationId: "00000000-0000-0000-0000-000000000202",
				practiceSlug: "asks-answerable-questions",
				practiceName: "Ask questions a teammate can answer",
				title: "The question includes the attempted fix",
				presence: "PRESENT",
				assessment: "GOOD",
			},
		],
	},
];

const meta = {
	title: "Profile/Review runs/Timeline",
	component: ReviewRunTimeline,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
} satisfies Meta<typeof ReviewRunTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { runs } };
