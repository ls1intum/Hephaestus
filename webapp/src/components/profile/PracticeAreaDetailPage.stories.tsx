import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, fn, userEvent, within } from "storybook/test";
import type {
	ObservationDetail,
	ObservationList,
	PracticeArea,
	PracticeAreaStatus,
} from "@/api/types.gen";
import {
	type ActivityFilters,
	type ActivitySort,
	PracticeAreaDetailPage,
} from "@/components/profile/PracticeAreaDetailPage";

// A seeded area (name + description verbatim from the default catalog), admin icon/colour unset so
// the chip falls back to the neutral default.
const area: PracticeArea = {
	id: 1,
	active: true,
	slug: "review-ready-work",
	name: "Packaging work for review",
	description:
		"Make a change cheap to review before you ask for one. Keep it to a single self-contained concern of a readable size, say what changed and why, write commit subjects a reviewer can follow, and link it to the issue it answers. This is about how the work is packaged, not whether the code has bugs.",
	displayOrder: 1,
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
};

const mixedStatus: PracticeAreaStatus = {
	areaSlug: "review-ready-work",
	areaName: "Packaging work for review",
	status: "MIXED",
	guidance:
		"Your recent feedback shows a strength in “Describe what changed and why”. Next, focus on “Scope the change to one concern”.",
	guidanceSource: "RULE_BASED",
	trajectory: "IMPROVING",
	feedbackSpanDays: 34,
	feedbackSince: new Date("2026-06-24T09:00:00Z"),
	sources: [
		{ source: "PULL_REQUEST", count: 12 },
		{ source: "ISSUE", count: 3 },
	],
	items: [],
};

const practices = [
	{
		slug: "scope-one-concern",
		name: "Scope the change to one concern",
		whyItMatters:
			"Small, single-purpose changes are reviewed faster, more thoroughly, and with less back and forth.",
		whatGoodLooksLike:
			"Each pull request answers one question; refactorings ship separately from behaviour changes.",
	},
	{
		slug: "describe-what-changed",
		name: "Describe what changed and why",
		whyItMatters:
			"Reviewers who understand the intent catch real problems instead of guessing at context.",
		whatGoodLooksLike:
			"The description states the problem, the approach, and anything reviewers should look at first.",
	},
	{
		slug: "link-the-issue",
		name: "Link the change to the issue it answers",
		whyItMatters: "Traceable changes let reviewers check the change against the requirement.",
	},
];

const practiceStandings = {
	"scope-one-concern": "DEVELOPING" as const,
	"describe-what-changed": "STRENGTH" as const,
};

const activity: ObservationList[] = [
	{
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
		practiceSlug: "scope-one-concern",
		practiceName: "Scope the change to one concern",
		artifactType: "PULL_REQUEST",
		artifactId: 41,
		title: "Change bundles two unrelated concerns",
		presence: "ABSENT",
		assessment: "BAD",
		severity: "MINOR",
		confidence: 0.9,
		observedAt: new Date("2026-08-04T09:00:00Z"),
	},
	{
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
		practiceSlug: "describe-what-changed",
		practiceName: "Describe what changed and why",
		artifactType: "PULL_REQUEST",
		artifactId: 39,
		title: "PR description explains the why",
		presence: "PRESENT",
		assessment: "GOOD",
		confidence: 0.95,
		observedAt: new Date("2026-08-02T15:00:00Z"),
	},
	{
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c003",
		practiceSlug: "describe-what-changed",
		practiceName: "Describe what changed and why",
		artifactType: "CONVERSATION_THREAD",
		artifactId: 12,
		title: "Thread reply gave actionable context",
		presence: "PRESENT",
		assessment: "GOOD",
		confidence: 0.85,
		observedAt: new Date("2026-08-01T10:00:00Z"),
	},
	{
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c004",
		practiceSlug: "link-the-issue",
		practiceName: "Link the change to the issue it answers",
		artifactType: "ISSUE",
		artifactId: 17,
		title: "Issue link missing on the change",
		presence: "ABSENT",
		assessment: "BAD",
		severity: "MAJOR",
		confidence: 0.8,
		observedAt: new Date("2026-07-30T11:00:00Z"),
	},
];

const meta = {
	component: PracticeAreaDetailPage,
	parameters: {
		layout: "padded",
	},
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeAreaDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Guidance/reasoning fixtures the interactive story serves when a row is expanded. */
const observationDetails: Record<string, ObservationDetail> = Object.fromEntries(
	activity.map((observation) => [
		observation.id,
		{
			...observation,
			reasoning:
				observation.assessment === "BAD"
					? "The diff mixes a dependency upgrade with a behaviour change across 14 files, so a reviewer cannot approve either part independently."
					: "The description states the problem, the approach, and calls out the risky part for reviewers.",
			guidance:
				observation.assessment === "BAD"
					? "Split the refactoring from the feature change so each can be reviewed alone."
					: undefined,
			evidence: { locations: [{ path: "src/leaderboard/scoring.ts", startLine: 88 }] },
			artifactUrl: "https://github.com/acme/artemis-server/pull/521",
		},
	]),
);

/**
 * Fully interactive: select practice nodes, filter by source/severity, and expand feed entries —
 * state lives in the story so every interaction behaves like the wired route.
 */
export const Overview: Story = {
	args: {
		area,
		status: mixedStatus,
		practices,
		practiceStandings,
		practiceTrajectories: {
			"scope-one-concern": "IMPROVING" as const,
			"describe-what-changed": "STEADY" as const,
		},
		activity,
		isLoading: false,
		onBack: fn(),
	},
	render: function InteractiveOverview(args) {
		const [selectedPracticeSlug, setSelectedPracticeSlug] = useState<string>();
		const [filters, setFilters] = useState<ActivityFilters>({ sources: [], severities: [] });
		const [sort, setSort] = useState<ActivitySort>({ by: "DATE", direction: "DESC" });
		const [openObservationId, setOpenObservationId] = useState<string>();
		const severityRank = { CRITICAL: 0, MAJOR: 1, MINOR: 2, INFO: 3 } as const;
		const filtered = activity
			.filter(
				(observation) =>
					(!selectedPracticeSlug || observation.practiceSlug === selectedPracticeSlug) &&
					(filters.sources.length === 0 || filters.sources.includes(observation.artifactType)) &&
					(filters.severities.length === 0 ||
						observation.assessment !== "BAD" ||
						(observation.severity != null && filters.severities.includes(observation.severity))),
			)
			.sort((a, b) => {
				if (sort.by === "SEVERITY") {
					const rankA = a.severity ? severityRank[a.severity] : 4;
					const rankB = b.severity ? severityRank[b.severity] : 4;
					if (rankA !== rankB) {
						return sort.direction === "DESC" ? rankA - rankB : rankB - rankA;
					}
					return new Date(b.observedAt).getTime() - new Date(a.observedAt).getTime();
				}
				const delta = new Date(b.observedAt).getTime() - new Date(a.observedAt).getTime();
				return sort.direction === "DESC" ? delta : -delta;
			});
		return (
			<PracticeAreaDetailPage
				{...args}
				activity={filtered}
				selectedPracticeSlug={selectedPracticeSlug}
				onSelectPractice={setSelectedPracticeSlug}
				activityFilters={filters}
				onActivityFiltersChange={setFilters}
				activitySort={sort}
				onActivitySortChange={setSort}
				openObservationId={openObservationId}
				observationDetail={
					openObservationId
						? { isLoading: false, detail: observationDetails[openObservationId] }
						: undefined
				}
				onToggleObservation={(observationId) =>
					setOpenObservationId((current) => (current === observationId ? undefined : observationId))
				}
			/>
		);
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getAllByRole("button", { name: /Show details/ })[0]);
		await expect(await canvas.findByText("What to do")).toBeVisible();
		await expect(
			canvas.getByText(
				"Split the refactoring from the feature change so each can be reviewed alone.",
			),
		).toBeVisible();
	},
};

/** A practice node is selected: its framing shows inline and the feed is filtered to it. */
export const PracticeSelected: Story = {
	args: {
		...Overview.args,
		selectedPracticeSlug: "scope-one-concern",
		activity: activity.filter((observation) => observation.practiceSlug === "scope-one-concern"),
	},
};

/** A feed entry is expanded inline: delivered guidance plus the model's reasoning, no overlay. */
export const ObservationExpanded: Story = {
	args: {
		...Overview.args,
		openObservationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
		observationDetail: {
			isLoading: false,
			detail: {
				id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
				practiceSlug: "scope-one-concern",
				practiceName: "Scope the change to one concern",
				artifactType: "PULL_REQUEST",
				artifactId: 41,
				title: "Change bundles two unrelated concerns",
				presence: "ABSENT",
				assessment: "BAD",
				severity: "MINOR",
				confidence: 0.9,
				reasoning:
					"The diff mixes a dependency upgrade with a behaviour change across 14 files, so a reviewer cannot approve either part independently.",
				guidance: "Split the refactoring from the feature change so each can be reviewed alone.",
				observedAt: new Date("2026-08-04T09:00:00Z"),
			},
		},
	},
};

/** Only Slack conversations are shown, as picked in the filter popover. */
export const FilteredToSlack: Story = {
	args: {
		...Overview.args,
		activityFilters: { sources: ["CONVERSATION_THREAD"], severities: [] },
		activity: activity.filter((observation) => observation.artifactType === "CONVERSATION_THREAD"),
	},
};

export const ActivityLoading: Story = {
	args: {
		...Overview.args,
		activity: [],
		isActivityLoading: true,
	},
};

/** Ten practices: the flow wraps into a node grid sharing one arrow instead of squeezing a row. */
export const ManyPractices: Story = {
	args: {
		area,
		status: mixedStatus,
		practices: Array.from({ length: 10 }, (_, index) => ({
			slug: `practice-${index + 1}`,
			name: `Practice number ${index + 1} with a reasonably long name`,
			whyItMatters: "Why this matters, in plain language.",
		})),
		practiceStandings: {
			"practice-1": "DEVELOPING" as const,
			"practice-2": "STRENGTH" as const,
			"practice-3": "MIXED" as const,
		},
		activity,
		isLoading: false,
		onSelectPractice: fn(),
		onBack: fn(),
	},
};

/**
 * Practice weights on the flow edges — the UI seam for the planned admin-configurable importance
 * (the server's area aggregation already accepts per-practice weights).
 */
export const WeightedPractices: Story = {
	args: {
		area,
		status: mixedStatus,
		practices,
		practiceStandings,
		practiceWeights: {
			"scope-one-concern": 2,
			"describe-what-changed": 0.5,
		},
		activity,
		isLoading: false,
		onSelectPractice: fn(),
		onBack: fn(),
	},
};

export const NoDataYet: Story = {
	args: {
		area,
		status: {
			areaSlug: area.slug,
			areaName: area.name,
			status: "NO_DATA",
			sources: [],
			items: [],
		},
		practices,
		activity: [],
		isLoading: false,
		onBack: fn(),
	},
};

export const Loading: Story = {
	args: {
		area,
		isLoading: true,
	},
};

export const ErrorState: Story = {
	args: {
		area,
		isLoading: false,
		error: new Error("Request failed"),
		onRetry: fn(),
	},
};
