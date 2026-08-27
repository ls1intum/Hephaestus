import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeGroupReviewObservation } from "@/api/types.gen";
import { ReviewObservationRow } from "./ReviewObservationRow";

const strength = {
	observationId: "00000000-0000-0000-0000-000000000101",
	feedbackId: "00000000-0000-0000-0000-000000000102",
	feedbackUsefulness: "HELPFUL",
	practiceSlug: "explains-decisions",
	practiceName: "Explain significant decisions",
	title: "The reasoning is recorded next to the changed behavior",
	presence: "PRESENT",
	assessment: "GOOD",
} satisfies PracticeGroupReviewObservation;

const observations: PracticeGroupReviewObservation[] = [
	strength,
	{
		observationId: "00000000-0000-0000-0000-000000000201",
		practiceSlug: "avoids-unsafe-defaults",
		practiceName: "Avoid unsafe defaults",
		title: "The boundary does not fall back to an unsafe value",
		presence: "ABSENT",
		assessment: "GOOD",
	},
	{
		observationId: "00000000-0000-0000-0000-000000000301",
		practiceSlug: "does-not-swallow-errors",
		practiceName: "Do not swallow recoverable errors",
		title: "The exception is caught and discarded",
		presence: "PRESENT",
		assessment: "BAD",
		severity: "MAJOR",
	},
	{
		observationId: "00000000-0000-0000-0000-000000000401",
		practiceSlug: "network-timeouts",
		practiceName: "Document network timeout behavior",
		title: "This change performs no network request",
		presence: "NOT_APPLICABLE",
	},
	{
		observationId: "00000000-0000-0000-0000-000000000501",
		practiceSlug: "keeps-docs-current",
		practiceName: "Keep documentation current",
		title: "The evidence does not settle whether the page is current",
		presence: "INCONCLUSIVE",
	},
];

const meta = {
	title: "Profile/Review runs/Observation row",
	component: ReviewObservationRow,
	parameters: { layout: "padded" },
	decorators: [
		(Story) => (
			<ul className="divide-y rounded-lg border">
				<Story />
			</ul>
		),
	],
} satisfies Meta<typeof ReviewObservationRow>;

export default meta;
type Story = StoryObj<typeof meta>;

export const StrengthShown: Story = {
	args: { observation: strength, onChangeUsefulness: () => undefined },
};

export const AssessmentMatrix: Story = {
	args: StrengthShown.args,
	render: (args) => (
		<>
			{observations.map((observation) => (
				<ReviewObservationRow key={observation.observationId} {...args} observation={observation} />
			))}
		</>
	),
};
