import type { Meta, StoryObj } from "@storybook/react";
import {
	type ReviewedArtifact,
	ReviewHistoryTimeline,
} from "@/components/profile/ReviewHistoryTimeline";

const multiRunPullRequest: ReviewedArtifact = {
	artifactType: "PULL_REQUEST",
	artifactId: 900000004,
	provider: "GITHUB",
	number: 902,
	title: "Split the practice catalog loader per workspace",
	repositoryName: "HephaestusTest/practice-validation",
	url: "https://github.com/HephaestusTest/practice-validation/pull/902",
	runs: [
		{
			reviewId: "rev_3",
			reviewedAt: "2026-08-12T10:26:00Z",
			findings: [
				{
					observationId: "ab000004",
					practiceSlug: "records-significant-decisions-with-rationale",
					practiceName: "Record significant decisions and the reasoning",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning:
						"The pull request explains why the catalog loader is split by workspace and records the trade-off behind the change.",
					evidence: "PracticeCatalogLoader.java:48–76",
					guidance:
						"Keep recording decisions close to the change so a maintainer can understand both the chosen approach and the alternatives you ruled out.",
					recurrenceKey: "demo-multirun-decisions",
				},
				{
					observationId: "ab000005",
					practiceSlug: "documents-public-api-and-behaviour-changes",
					practiceName: "Document public API and behaviour changes",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning:
						"The public behavior change is reflected in the API documentation and the migration note.",
					evidence: "docs/practice-catalog.md:31–44",
					guidance:
						"Continue updating public documentation in the same pull request as the behavior it describes.",
					recurrenceKey: "demo-multirun-api-docs",
				},
				{
					observationId: "ab000006",
					practiceSlug: "change-keeps-linked-docs-consistent",
					practiceName: "Keep linked wiki docs consistent with the change",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning:
						"The implementation uses the new workspace-specific loader name, while the linked wiki page still refers to the previous shared loader.",
					evidence: "docs/wiki/practice-catalog.md:18",
					guidance:
						"Update the linked wiki page and check that its example uses the new workspace-specific loader.",
					recurrenceKey: "demo-multirun-linked-docs",
				},
				{
					observationId: "ab000008",
					practiceSlug: "avoids-unsafe-defaults",
					practiceName: "Avoid unsafe defaults",
					presence: "ABSENT",
					assessment: "GOOD",
					reasoning:
						"The workspace-specific loader requires an explicit catalog and does not fall back to another workspace's configuration.",
					evidence: "PracticeCatalogLoader.java:81–89",
					guidance:
						"Keep invalid configuration explicit so a maintainer can trace the affected workspace.",
					recurrenceKey: "demo-multirun-unsafe-default",
				},
				{
					observationId: "ab000009",
					practiceSlug: "tests-boundary-cases",
					practiceName: "Test boundary cases",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning:
						"The review adds an empty-catalog failure path but contains no test that exercises it.",
					evidence: "PracticeCatalogLoaderTest.java",
					guidance:
						"Add a test for a workspace without catalog configuration and assert the explicit failure.",
					recurrenceKey: "demo-multirun-boundary-test",
				},
			],
		},
		{
			reviewId: "rev_2",
			reviewedAt: "2026-08-09T16:40:00Z",
			findings: [
				{
					observationId: "ab000002",
					practiceSlug: "records-significant-decisions-with-rationale",
					practiceName: "Record significant decisions and the reasoning",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning:
						"The earlier revision introduced the workspace split without explaining why the shared loader was no longer sufficient.",
					evidence: "PracticeCatalogLoader.java:44–70",
					guidance: "Add the reason for the split and briefly note the alternative you considered.",
					recurrenceKey: "demo-multirun-decisions",
				},
				{
					observationId: "ab000003",
					practiceSlug: "documents-public-api-and-behaviour-changes",
					practiceName: "Document public API and behaviour changes",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning: "The API documentation already reflected the changed catalog behavior.",
					evidence: "docs/practice-catalog.md:31–44",
					guidance: "Keep the documentation and implementation changes together.",
					recurrenceKey: "demo-multirun-api-docs",
				},
				{
					observationId: "ab000007",
					practiceSlug: "change-keeps-linked-docs-consistent",
					practiceName: "Keep linked wiki docs consistent with the change",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning:
						"The earlier implementation and its linked wiki page used the same loader name.",
					evidence: "docs/wiki/practice-catalog.md:18",
					guidance: "Continue checking linked documentation when public names change.",
					recurrenceKey: "demo-multirun-linked-docs",
				},
			],
		},
		{
			reviewId: "rev_1",
			reviewedAt: "2026-07-24T08:12:00Z",
			findings: [
				{
					observationId: "ab000001",
					practiceSlug: "records-significant-decisions-with-rationale",
					practiceName: "Record significant decisions and the reasoning",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MAJOR",
					reasoning:
						"The first revision changed the loader lifecycle without recording the reason or expected effect.",
					evidence: "PracticeCatalogLoader.java:42–68",
					guidance:
						"Document the decision before the next review, including the problem the new lifecycle solves.",
					recurrenceKey: "demo-multirun-decisions",
				},
			],
		},
	],
};

const singleRunIssue: ReviewedArtifact = {
	artifactType: "ISSUE",
	artifactId: 900000103,
	provider: "GITHUB",
	number: 704,
	title: "Split the leaderboard sync into per-repository subtasks",
	repositoryName: "HephaestusTest/practice-validation",
	url: "https://github.com/HephaestusTest/practice-validation/issues/704",
	runs: [
		{
			reviewId: "rev_issue_3",
			reviewedAt: "2026-08-11T09:18:00Z",
			findings: [
				{
					observationId: "issue-1",
					practiceSlug: "issue-points-to-relevant-context",
					practiceName: "Point to the context a maintainer needs",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning:
						"The issue links the failing synchronization job and identifies the repositories affected by it.",
					evidence: "Issue description · Context section",
					guidance:
						"Keep linking the concrete failure and affected scope so the next person can start investigating immediately.",
				},
			],
		},
	],
};

const conversation: ReviewedArtifact = {
	artifactType: "CONVERSATION_THREAD",
	artifactId: 900000203,
	provider: "SLACK",
	channelName: "dev-hephaestus",
	messageCount: 12,
	url: "https://hephaestus.slack.com/archives/C12345678/p1786640400000000",
	runs: [
		{
			reviewId: "rev_slack_3",
			reviewedAt: "2026-08-13T16:04:00Z",
			findings: [
				{
					observationId: "slack-1",
					practiceSlug: "asks-answerable-questions",
					practiceName: "Ask questions a teammate can answer",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning:
						"The question names the failing task, the attempted fix, and the specific decision that needs input.",
					evidence: "#dev-hephaestus · thread started at 17:41",
					guidance:
						"Continue including what you already tried and the decision you need help with.",
				},
				{
					observationId: "slack-2",
					practiceSlug: "gives-actionable-answers",
					practiceName: "Give actionable answers",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning:
						"One reply confirms the problem but does not identify a concrete next action or owner.",
					evidence: "#dev-hephaestus · reply at 17:48",
					guidance:
						"End the reply with the next action and who will take it, so the thread can move forward.",
				},
			],
		},
	],
};

const assessmentMatrixArtifact: ReviewedArtifact = {
	artifactType: "PULL_REQUEST",
	artifactId: 900000304,
	provider: "GITLAB",
	number: 118,
	title: "Show the complete observation assessment matrix",
	repositoryName: "HephaestusTest/assessment-matrix",
	url: "https://gitlab.com/HephaestusTest/assessment-matrix/-/merge_requests/118",
	runs: [
		{
			reviewId: "rev_matrix",
			reviewedAt: "2026-08-14T08:30:00Z",
			findings: [
				{
					observationId: "matrix-present-good",
					practiceSlug: "explains-decisions",
					practiceName: "Explain significant decisions",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning: "A helpful behavior was visible in the reviewed work.",
					evidence: "PracticeCatalogLoader.java:48–76",
					guidance: "Continue making the reasoning visible close to the change.",
				},
				{
					observationId: "matrix-absent-good",
					practiceSlug: "avoids-unsafe-defaults",
					practiceName: "Avoid unsafe defaults",
					presence: "ABSENT",
					assessment: "GOOD",
					reasoning: "The risky fallback was not used in the reviewed work.",
					evidence: "WorkspaceCatalogResolver.java:29–36",
					guidance: "Keep requiring an explicit value at this boundary.",
				},
				{
					observationId: "matrix-present-bad",
					practiceSlug: "does-not-swallow-errors",
					practiceName: "Do not swallow recoverable errors",
					presence: "PRESENT",
					assessment: "BAD",
					severity: "MAJOR",
					reasoning: "An exception is caught and discarded without recovery or propagation.",
					evidence: "CatalogLoader.java:64",
					guidance: "Propagate the failure or recover explicitly instead of continuing silently.",
				},
				{
					observationId: "matrix-absent-bad",
					practiceSlug: "tests-boundary-cases",
					practiceName: "Test boundary cases",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning: "The change introduces a new boundary case but no corresponding test.",
					evidence: "CatalogLoaderTest.java",
					guidance: "Add a test for a workspace without a configured catalog.",
				},
				{
					observationId: "matrix-not-applicable",
					practiceSlug: "documents-network-timeouts",
					practiceName: "Document network timeout behavior",
					presence: "NOT_APPLICABLE",
					reasoning: "The change does not make or modify a network request.",
					evidence: "PracticeCatalogLoader.java:41–89",
					guidance: "No action is needed for this practice in this review.",
				},
			],
		},
	],
};

const meta = {
	title: "Profile/Review history/Timeline",
	component: ReviewHistoryTimeline,
	parameters: { layout: "padded" },
} satisfies Meta<typeof ReviewHistoryTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default area view: every review moment remains in one strict newest-first sequence. */
export const AllPracticesChronological: Story = {
	args: { artifacts: [multiRunPullRequest, singleRunIssue, conversation] },
};

/** Mirrors selecting one practice on the left: the right side becomes its evidence over time. */
export const FilteredToOnePractice: Story = {
	args: {
		artifacts: [multiRunPullRequest, singleRunIssue, conversation],
		selectedPracticeSlug: "records-significant-decisions-with-rationale",
	},
};

/** Sparse events stay discrete; no line chart invents values between the observed dates. */
export const SparsePracticeHistory: Story = {
	args: {
		artifacts: [multiRunPullRequest],
		selectedPracticeSlug: "records-significant-decisions-with-rationale",
	},
};

/** The common current case still reads naturally without special timeline scaffolding. */
export const SingleFeedbackMoment: Story = {
	args: { artifacts: [singleRunIssue] },
};

/** Shows the complete presence × assessment matrix without collapsing it to GOOD/BAD alone. */
export const AssessmentMatrix: Story = {
	args: { artifacts: [assessmentMatrixArtifact] },
};

/** Narrow view keeps date, source, status, and artifact link in the same reading order. */
export const Mobile: Story = {
	args: { artifacts: [multiRunPullRequest, conversation] },
	parameters: { viewport: { defaultViewport: "mobile1" } },
};
