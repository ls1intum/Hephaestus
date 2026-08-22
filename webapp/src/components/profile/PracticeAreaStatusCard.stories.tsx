import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent } from "storybook/test";
import type { PracticeArea, PracticeAreaStatus, ReflectionPractice } from "@/api/types.gen";
import { PracticeAreaStatusCard } from "@/components/profile/PracticeAreaStatusCard";

const makeArea = (id: number, slug: string, name: string, description: string): PracticeArea => ({
	id,
	slug,
	name,
	description,
	displayOrder: id,
	visibleInPracticeDashboards: true,
	autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
});

const makePractice = (
	slug: string,
	name: string,
	standing: ReflectionPractice["standing"],
): ReflectionPractice => ({ slug, name, standing, strengths: [], toWorkOn: [] });

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

const trendSupport = {
	level: "WELL_SUPPORTED" as const,
	currentOpportunities: 4,
	previousOpportunities: 4,
	opportunitiesUntilComparable: 0,
	bundleSize: 4,
	ropeHalfWidth: 0.15,
	credibilityThreshold: 0.9,
};

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
		trendSupport,
		feedbackSpanDays: 34,
		feedbackSince: new Date("2026-06-24T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
				title: "Change bundles two unrelated concerns",
				deliveredFeedback:
					"Split the refactoring from the feature change so each can be reviewed alone.",
				severity: "MINOR",
				artifactKind: "PULL_REQUEST",
				artifactId: 41,
				origin: "LIVE",
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
				deliveredFeedback: "You consistently close the loop on review threads — keep it up.",
				artifactKind: "PULL_REQUEST",
				artifactId: 42,
				origin: "LIVE",
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
		trajectory: "DECLINING",
		trendSupport,
		feedbackSpanDays: 8,
		feedbackSince: new Date("2026-07-20T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c003",
				title: "Behaviour change shipped without a test",
				deliveredFeedback: "Add a test that fails without this fix so the behaviour stays covered.",
				severity: "MAJOR",
				artifactKind: "PULL_REQUEST",
				artifactId: 43,
				origin: "LIVE",
			},
		],
		sources: [
			{ source: "PULL_REQUEST", count: 4 },
			{ source: "ISSUE", count: 1 },
		],
	},
};

const practicesByArea: Record<string, ReflectionPractice[]> = {
	"review-ready-work": [
		makePractice("describe-change", "Describe what changed and why", "STRENGTH"),
		makePractice("scope-change", "Scope the change to one concern", "MIXED"),
		makePractice("link-issue", "Link the issue the change answers", "DEVELOPING"),
	],
	"acting-on-review-feedback": [
		makePractice("respond-comments", "Respond to each review comment", "STRENGTH"),
		makePractice("resolve-threads", "Resolve open threads before merging", "STRENGTH"),
	],
	"testing-discipline": [
		makePractice("include-tests", "Include tests with the change", "DEVELOPING"),
		makePractice("keep-tests", "Do not weaken existing tests", "DEVELOPING"),
	],
};

const practiceCountByArea = {
	"review-ready-work": 4,
	"acting-on-review-feedback": 2,
	"testing-discipline": 3,
};

// A workspace carrying more areas than the grid shows at once — the state the collapse exists for.
// Both halves of it deliberately contain a green share and an unmeasured one, so the collapsed grid
// is not a different visual language from the expanded one.
const moreAreas: PracticeArea[] = [
	makeArea(
		4,
		"robust-error-handling",
		"Handling failure well",
		"Handle the failure paths you introduce: no silently swallowed errors, validated inputs at the boundary, and no crash where the code could recover.",
	),
	makeArea(
		5,
		"code-craftsmanship",
		"Writing maintainable code",
		"Leave the code easier to work in than you found it — factor out duplication, keep functions to one purpose, and comment the why rather than the what.",
	),
	makeArea(
		6,
		"communication",
		"Communicating in the open",
		"Ask questions a teammate can answer, give answers people can act on, and post status and blockers where the team can see them.",
	),
];

const moreStatuses: Record<string, PracticeAreaStatus> = {
	"robust-error-handling": {
		areaSlug: "robust-error-handling",
		areaName: "Handling failure well",
		status: "MIXED",
		guidance:
			"Your recent feedback is mixed in “Validate inputs and edge cases at the boundary”, with both strengths and room to grow.",
		guidanceSource: "RULE_BASED",
		trajectory: "STABLE",
		trendSupport,
		feedbackSpanDays: 27,
		feedbackSince: new Date("2026-07-01T09:00:00Z"),
		items: [],
		sources: [{ source: "PULL_REQUEST", count: 6 }],
	},
	"code-craftsmanship": {
		areaSlug: "code-craftsmanship",
		areaName: "Writing maintainable code",
		status: "STRENGTH",
		guidance:
			"Your recent feedback shows strengths in “Keep functions small and single-purpose”. Keep building on them.",
		guidanceSource: "RULE_BASED",
		trajectory: "IMPROVING",
		trendSupport,
		feedbackSpanDays: 19,
		feedbackSince: new Date("2026-07-09T09:00:00Z"),
		items: [],
		sources: [{ source: "PULL_REQUEST", count: 5 }],
	},
	// No feedback at all: the card falls back to its badge and draws no ring.
	communication: {
		areaSlug: "communication",
		areaName: "Communicating in the open",
		status: "NOT_OBSERVED",
		items: [],
		sources: [],
	},
};

const morePracticesByArea: Record<string, ReflectionPractice[]> = {
	"robust-error-handling": [
		makePractice("validate-inputs", "Validate inputs and edge cases at the boundary", "MIXED"),
		makePractice("handle-errors", "Handle errors instead of swallowing them", "STRENGTH"),
	],
	"code-craftsmanship": [
		makePractice("small-functions", "Keep functions small and single-purpose", "STRENGTH"),
		makePractice(
			"remove-duplication",
			"Factor out duplication instead of copy-pasting",
			"STRENGTH",
		),
	],
};

const morePracticeCountByArea = { "robust-error-handling": 3, "code-craftsmanship": 3 };

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
		practicesByArea,
		practiceCountByArea,
		isLoading: false,
		onOpenDetails: fn(),
	},
	play: async ({ canvas, args }) => {
		await expect(canvas.queryByText("Feedback highlight")).not.toBeInTheDocument();
		await userEvent.click(
			canvas.getByRole("button", { name: "See details about Packaging work for review" }),
		);
		await expect(args.onOpenDetails).toHaveBeenCalledWith(areas[0]);
	},
};

/**
 * Six areas: only the first three of the priority order are drawn until the learner asks for the rest,
 * so the feedback history below stays reachable without scrolling past a wall of cards.
 */
export const ManyAreas: Story = {
	args: {
		areas: [...areas, ...moreAreas],
		statuses: { ...statuses, ...moreStatuses },
		practicesByArea: { ...practicesByArea, ...morePracticesByArea },
		practiceCountByArea: { ...practiceCountByArea, ...morePracticeCountByArea },
		isLoading: false,
		onOpenDetails: fn(),
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("Writing maintainable code")).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Show all 6 practice areas" }));
		canvas.getByText("Writing maintainable code");
		await userEvent.click(canvas.getByRole("button", { name: "Show fewer areas" }));
		await expect(canvas.queryByText("Writing maintainable code")).not.toBeInTheDocument();
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
		practicesByArea: { "review-ready-work": practicesByArea["review-ready-work"] },
		practiceCountByArea: { "review-ready-work": practiceCountByArea["review-ready-work"] },
		isLoading: false,
		onOpenDetails: fn(),
	},
	play: async ({ canvas }) => {
		canvas.getByText("Feedback from 5 Slack conversations");
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
