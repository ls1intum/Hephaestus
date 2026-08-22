import type { Meta, StoryObj } from "@storybook/react";
import {
	ReviewFindingRow,
	type ReviewFindingRowProps,
} from "@/components/profile/ReviewFindingRow";
import type { ReviewFinding } from "@/components/profile/review-history";

const findings: ReviewFinding[] = [
	{
		observationId: "present-good",
		feedbackId: "feedback-present-good",
		helpful: true,
		practiceSlug: "explains-decisions",
		practiceName: "Explain significant decisions",
		presence: "PRESENT",
		assessment: "GOOD",
		reasoning: "The reasoning is recorded next to the changed behavior.",
		evidence: "PracticeCatalogLoader.java:48–76",
		guidance: "Continue documenting the alternatives you considered.",
	},
	{
		observationId: "absent-good",
		practiceSlug: "avoids-unsafe-defaults",
		practiceName: "Avoid unsafe defaults",
		presence: "ABSENT",
		assessment: "GOOD",
		reasoning: "The reviewed boundary does not fall back to an unsafe value.",
		evidence: "WorkspaceCatalogResolver.java:29–36",
		guidance: "Keep invalid configuration explicit instead of adding a silent fallback.",
	},
	{
		observationId: "present-bad",
		practiceSlug: "does-not-swallow-errors",
		practiceName: "Do not swallow recoverable errors",
		presence: "PRESENT",
		assessment: "BAD",
		severity: "MAJOR",
		reasoning:
			"The exception is caught and discarded, so the caller continues with incomplete data.",
		evidence: "CatalogLoader.java:64",
		guidance: "Propagate the failure or recover explicitly.",
	},
	{
		observationId: "absent-bad",
		practiceSlug: "tests-boundaries",
		practiceName: "Test boundary cases",
		presence: "ABSENT",
		assessment: "BAD",
		severity: "MINOR",
		reasoning: "The new empty-catalog path has no corresponding test in the reviewed change.",
		evidence: "CatalogLoaderTest.java",
		guidance: "Add a test for a workspace without a configured catalog.",
	},
	{
		observationId: "not-applicable",
		practiceSlug: "network-timeouts",
		practiceName: "Document network timeout behavior",
		presence: "NOT_APPLICABLE",
		reasoning: "The change does not make or modify a network request.",
		evidence: "CatalogLoader.java:41–78",
		guidance: "No action is needed for this practice in this review.",
	},
];

const meta = {
	title: "Profile/Review history/Finding row",
	component: ReviewFindingRow,
	parameters: { layout: "padded" },
	decorators: [
		(Story) => (
			<ul className="divide-y rounded-lg border">
				<Story />
			</ul>
		),
	],
} satisfies Meta<typeof ReviewFindingRow>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StrengthShown: Story = {
	args: {
		finding: findings[0],
		onRateFeedback: () => undefined,
	},
};

export const AssessmentMatrix: Story = {
	args: StrengthShown.args,
	render: (args: ReviewFindingRowProps) => (
		<>
			{findings.map((finding) => (
				<ReviewFindingRow key={finding.observationId} {...args} finding={finding} />
			))}
		</>
	),
};
