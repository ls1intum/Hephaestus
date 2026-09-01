import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";
import type { ObservationDetail, PracticeGroupReviewObservation } from "@/api/types.gen";
import { daysBefore } from "@/components/common/story-clock";
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
		observationId: "00000000-0000-0000-0000-000000000351",
		practiceSlug: "covers-new-behavior",
		practiceName: "Cover new behavior with a test",
		title: "The new branch has no test exercising it",
		presence: "ABSENT",
		assessment: "BAD",
		severity: "CRITICAL",
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
	tags: ["autodocs"],
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
	args: { observation: strength, onRespond: fn() },
};

/** Every outcome the review can record, in the order the registry declares them. */
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

const detail = {
	id: strength.observationId,
	practiceSlug: strength.practiceSlug,
	practiceName: strength.practiceName,
	presence: "PRESENT",
	assessment: "GOOD",
	summary: strength.title,
	observedAt: daysBefore(2),
	origin: "LIVE",
	claimCurrentness: "CURRENT",
	artifactId: 4821,
	artifactKind: "scm.pull_request",
	evidenceRationale:
		"The comment above the changed branch states why the timeout was raised, so a later reader does not have to reconstruct it from the diff.",
	deliveredFeedback:
		"Keep doing this where a value is chosen rather than derived — the reasoning is what a reviewer cannot recover on their own.",
	evidence: {
		detector: "practice-observer",
		citations: [
			{
				sourceKind: "scm.pull-request.diff",
				artifactPath: "owner/repo#4821",
				path: "server/application/src/main/java/de/tum/cit/aet/hephaestus/agent/AgentClient.java",
				side: "NEW",
				startLine: 88,
				endLine: 90,
				quote:
					"// Raised from 30s: the precompute step regularly needs 45s on a cold cache.\n" +
					"private static final Duration TIMEOUT = Duration.ofSeconds(90);",
				quoteRedacted: false,
			},
		],
	},
} satisfies ObservationDetail;

/** Opened: the reasoning, the guidance and the quoted evidence behind one observation. */
export const Opened: Story = {
	args: {
		observation: strength,
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, detail },
	},
};

/** The detail is still on its way; the panel holds its shape instead of jumping when it lands. */
export const DetailLoading: Story = {
	args: {
		observation: strength,
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: true },
	},
};

/** The detail request failed: the row stays open and offers the error rather than an empty panel. */
export const DetailFailed: Story = {
	args: {
		observation: strength,
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, error: new Error("Request failed with status 503") },
	},
};

/** Loaded, but the review recorded no rationale, guidance or evidence — said plainly, not left blank. */
export const NoFurtherDetail: Story = {
	args: {
		observation: strength,
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: {
			isLoading: false,
			detail: {
				...detail,
				evidenceRationale: undefined,
				deliveredFeedback: undefined,
				evidence: undefined,
			},
		},
	},
};

/** A response already recorded, and a second one being written — both controls stay disabled meanwhile. */
export const FeedbackPending: Story = {
	args: {
		observation: { ...strength, feedbackUsefulness: "UNHELPFUL" },
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		isFeedbackResponsePending: true,
		detailState: { isLoading: false, detail },
	},
};

/** Marking a piece of feedback helpful reaches the caller with the observation it belongs to. */
export const RecordsAResponse: Story = {
	args: {
		observation: strength,
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, detail },
	},
	play: async ({ args, canvas, userEvent }) => {
		// `strength` arrives already marked HELPFUL, so pressing it again withdraws that half. The other
		// two fields still travel — the endpoint replaces, so omitting them would clear them.
		await userEvent.click(canvas.getByRole("button", { name: "Helpful" }));
		await expect(args.onRespond).toHaveBeenCalledWith(strength, {
			usefulness: undefined,
			resolution: undefined,
			comment: undefined,
		});
	},
};

/**
 * The response in full: how useful the review was, what the developer did about it, and room to say
 * why. The three are independent — the server keeps them apart, and so does this.
 */
export const RespondingInFull: Story = {
	args: {
		observation: {
			...strength,
			feedbackUsefulness: "HELPFUL",
			feedbackResolution: "ADDRESSED",
			feedbackResponseComment:
				"Split the change into two commits so the reasoning reads on its own.",
		},
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, detail },
	},
};

/**
 * Disputing is the one answer the server insists on an explanation for, so the field says it is
 * required and the save button stays disabled until something is written.
 */
export const DisputedNeedsAnExplanation: Story = {
	args: {
		observation: { ...strength, feedbackUsefulness: "UNHELPFUL", feedbackResolution: "DISPUTED" },
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, detail },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Disputed" })).toHaveAttribute(
			"aria-pressed",
			"true",
		);
		await expect(canvas.getByRole("button", { name: "Save comment" })).toBeDisabled();
	},
};

/** Recording a resolution keeps the usefulness already given — the whole answer travels together. */
export const RecordsAResolution: Story = {
	args: {
		observation: { ...strength, feedbackUsefulness: "HELPFUL" },
		isOpen: true,
		onToggle: fn(),
		onRespond: fn(),
		detailState: { isLoading: false, detail },
	},
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Not applicable" }));
		await expect(args.onRespond).toHaveBeenCalledWith(
			{ ...strength, feedbackUsefulness: "HELPFUL" },
			{ usefulness: "HELPFUL", resolution: "NOT_APPLICABLE", comment: undefined },
		);
	},
};
