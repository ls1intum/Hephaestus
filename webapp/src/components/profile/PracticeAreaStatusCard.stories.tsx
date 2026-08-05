import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { PracticeArea, PracticeAreaStatus } from "@/api/types.gen";
import { PracticeAreaStatusCard } from "@/components/profile/PracticeAreaStatusCard";

const makeArea = (id: number, slug: string, name: string, description: string): PracticeArea => ({
	id,
	active: true,
	slug,
	name,
	description,
	displayOrder: id,
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
});

// Real areas (names + descriptions verbatim) from the seeded default catalog. No admin-set icon,
// so the chips use the centrally defined catalog icons.
const areas: PracticeArea[] = [
	makeArea(
		1,
		"review-ready-work",
		"Packaging work for review",
		"Make a change cheap to review before you ask for one. Keep it to a single self-contained concern of a readable size, say what changed and why, write commit subjects a reviewer can follow, and link it to the issue it answers. This is about how the work is packaged, not whether the code has bugs.",
	),
	makeArea(
		2,
		"acting-on-review-feedback",
		"Acting on review feedback",
		"Close the review loop. When a reviewer leaves a substantive comment on a line, answer it with a reply that names what changed or a follow-up commit that addresses it, and resolve the thread before merging. Feedback only improves the work once someone acts on it.",
	),
	makeArea(
		3,
		"testing-discipline",
		"Testing your changes",
		"When a change adds or fixes behaviour, it comes with the tests that exercise it. And it never quietly deletes, skips, or weakens a test just to make the build pass.",
	),
];

// Statuses as the server generates them: label-colon summaries built from the catalog's
// imperative practice names, trajectory hints, and the actual feedback span with its start date.
const statuses: Record<string, PracticeAreaStatus> = {
	"review-ready-work": {
		areaSlug: "review-ready-work",
		areaName: "Packaging work for review",
		status: "MIXED",
		guidance:
			"Your recent feedback shows a strength in “Describe what changed and why”. Next, focus on “Scope the change to one concern”.",
		guidanceSource: "RULE_BASED",
		trajectory: "IMPROVING",
		feedbackSpanDays: 34,
		feedbackSince: new Date("2026-06-24T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
				title: "Change bundles two unrelated concerns",
				guidance: "Split the refactoring from the feature change so each can be reviewed alone.",
				severity: "MINOR",
				artifactType: "PULL_REQUEST",
				artifactId: 41,
			},
		],
		sources: [
			{ source: "PULL_REQUEST", count: 12 },
			{ source: "ISSUE", count: 3 },
		],
	},
	"acting-on-review-feedback": {
		areaSlug: "acting-on-review-feedback",
		areaName: "Acting on review feedback",
		status: "STRENGTH",
		guidance:
			"Your recent feedback shows strengths in “Respond to each review comment” and “Resolve open threads before merging”. Keep building on them.",
		guidanceSource: "RULE_BASED",
		feedbackSpanDays: 21,
		feedbackSince: new Date("2026-07-07T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
				title: "Every review thread answered",
				guidance: "You consistently close the loop on review threads — keep it up.",
				artifactType: "PULL_REQUEST",
				artifactId: 42,
			},
		],
		sources: [{ source: "PULL_REQUEST", count: 9 }],
	},
	"testing-discipline": {
		areaSlug: "testing-discipline",
		areaName: "Testing your changes",
		status: "DEVELOPING",
		guidance:
			"Your recent feedback points to “Include tests with the change” as the next practice to focus on.",
		guidanceSource: "RULE_BASED",
		trajectory: "REGRESSING",
		feedbackSpanDays: 8,
		feedbackSince: new Date("2026-07-20T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c003",
				title: "Behaviour change shipped without a test",
				guidance: "Add a test that fails without this fix so the behaviour stays covered.",
				severity: "MAJOR",
				artifactType: "PULL_REQUEST",
				artifactId: 43,
			},
		],
		sources: [
			{ source: "PULL_REQUEST", count: 4 },
			{ source: "ISSUE", count: 1 },
		],
	},
};

const meta = {
	component: PracticeAreaStatusCard,
	parameters: {
		layout: "padded",
	},
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeAreaStatusCard>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Overview: Story = {
	args: {
		areas,
		statuses,
		isLoading: false,
		onOpenDetails: fn(),
	},
	play: async ({ canvasElement, args }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText("Feedback highlight")).not.toBeInTheDocument();
		await userEvent.click(
			canvas.getByRole("button", { name: "See details about Packaging work for review" }),
		);
		await expect(args.onOpenDetails).toHaveBeenCalledWith(areas[0]);
	},
};

/** The provenance row identifies Slack when conversation feedback contributes to the summary. */
export const WithSlackFeedback: Story = {
	args: {
		areas: [areas[0]],
		statuses: {
			"review-ready-work": {
				...statuses["review-ready-work"],
				sources: [
					...statuses["review-ready-work"].sources,
					{ source: "CONVERSATION_THREAD", count: 5 },
				],
			},
		},
		isLoading: false,
		onOpenDetails: fn(),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Feedback from 5 Slack conversations")).toBeInTheDocument();
	},
};

export const NoDataYet: Story = {
	args: {
		areas,
		statuses: {},
		isLoading: false,
	},
};

export const EmptyWorkspace: Story = {
	args: {
		areas: [],
		statuses: {},
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		areas,
		statuses: {},
		isLoading: true,
	},
};

export const ErrorState: Story = {
	args: {
		areas,
		statuses: {},
		isLoading: false,
		error: new Error("Request failed"),
	},
};
