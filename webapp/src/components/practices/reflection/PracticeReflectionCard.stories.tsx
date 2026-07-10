import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeReportCard } from "@/api/types.gen";
import { PracticeReflectionCard } from "./PracticeReflectionCard";

const basePractice: PracticeReportCard = {
	name: "Write a clear pull request description",
	areaName: "Pull requests",
	slug: "clear-pr-description",
	standing: "MIXED",
	whyItMatters:
		"A clear description helps reviewers understand the change quickly and gives future readers the context behind it.",
	strengths: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "strength-1",
			title: "Explained the user-facing impact up front",
			guidance: "You opened with what changes for the user — reviewers get oriented fast.",
			locator: "PR #42",
			severity: "INFO",
		},
	],
	toWorkOn: [
		{
			artifactId: 42,
			artifactType: "PULL_REQUEST",
			observationId: "work-1",
			title: "Link the issue this pull request closes",
			guidance: "Add a closing keyword (e.g. “Closes #17”) so the issue and PR stay connected.",
			locator: "PR #42",
			severity: "MINOR",
		},
		{
			artifactId: 51,
			artifactType: "ISSUE",
			observationId: "work-2",
			title: "State the acceptance criteria",
			guidance: "List what “done” looks like so reviewers can check the change against it.",
			severity: "MAJOR",
		},
	],
};

/** One practice's readable feedback for a developer: a criterion-referenced standing chip, strengths, and what to work on. */
const meta = {
	component: PracticeReflectionCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { practice: basePractice },
} satisfies Meta<typeof PracticeReflectionCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const Strength: Story = {
	args: {
		practice: {
			...basePractice,
			standing: "STRENGTH",
			toWorkOn: [],
		},
	},
};

export const Developing: Story = {
	args: {
		practice: {
			...basePractice,
			standing: "DEVELOPING",
			strengths: [],
		},
	},
};

export const NoGuidance: Story = {
	args: {
		practice: {
			...basePractice,
			whyItMatters: undefined,
			strengths: [],
			toWorkOn: [
				{
					artifactId: 42,
					artifactType: "PULL_REQUEST",
					observationId: "work-only",
					title: "Add a summary line",
				},
			],
		},
	},
};
