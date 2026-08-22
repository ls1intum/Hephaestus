import type { Meta, StoryObj } from "@storybook/react";
import { ReviewHistoryMoment } from "@/components/profile/ReviewHistoryMoment";
import type { ReviewedArtifact } from "@/components/profile/review-history";

const artifact: ReviewedArtifact = {
	artifactType: "PULL_REQUEST",
	artifactId: 902,
	provider: "GITHUB",
	number: 902,
	title: "Split the practice catalog loader per workspace",
	repositoryName: "HephaestusTest/practice-validation",
	url: "https://github.com/HephaestusTest/practice-validation/pull/902",
	runs: [
		{
			reviewId: "review-moment",
			reviewedAt: "2026-08-12T10:26:00Z",
			findings: [
				{
					observationId: "moment-strength",
					practiceSlug: "records-decisions",
					practiceName: "Record significant decisions and the reasoning",
					presence: "PRESENT",
					assessment: "GOOD",
					reasoning: "The change records the reason for separating catalog loading by workspace.",
					evidence: "PracticeCatalogLoader.java:48–76",
					guidance: "Keep recording important trade-offs close to the change.",
				},
				{
					observationId: "moment-problem",
					practiceSlug: "keeps-docs-current",
					practiceName: "Keep linked documentation consistent",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning: "The linked documentation still uses the previous component name.",
					evidence: "docs/practice-catalog.md:31–44",
					guidance: "Update the linked page together with the renamed component.",
				},
				{
					observationId: "moment-risk-avoided",
					practiceSlug: "avoids-unsafe-defaults",
					practiceName: "Avoid unsafe defaults",
					presence: "ABSENT",
					assessment: "GOOD",
					reasoning:
						"The workspace lookup fails explicitly when no catalog is configured instead of silently selecting a global default.",
					evidence: "PracticeCatalogLoader.java:81–89",
					guidance:
						"Keep configuration failures explicit so maintainers can trace an invalid workspace setup.",
				},
				{
					observationId: "moment-missing-test",
					practiceSlug: "tests-boundaries",
					practiceName: "Test boundary cases",
					presence: "ABSENT",
					assessment: "BAD",
					severity: "MINOR",
					reasoning:
						"The loader now handles a workspace without a catalog, but the review contains no test for that path.",
					evidence: "PracticeCatalogLoaderTest.java",
					guidance:
						"Add a test that verifies the explicit failure when the workspace has no catalog configuration.",
				},
				{
					observationId: "moment-not-applicable",
					practiceSlug: "documents-network-timeouts",
					practiceName: "Document network timeout behavior",
					presence: "NOT_APPLICABLE",
					reasoning:
						"This change performs no network request, so timeout behavior is outside its scope.",
					evidence: "PracticeCatalogLoader.java:48–89",
					guidance: "No action is needed for this practice in this review.",
				},
			],
		},
	],
};

const meta = {
	title: "Profile/Review history/Review moment",
	component: ReviewHistoryMoment,
	parameters: { layout: "padded" },
	decorators: [
		(Story) => (
			<ol>
				<Story />
			</ol>
		),
	],
} satisfies Meta<typeof ReviewHistoryMoment>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {
		artifact,
		run: artifact.runs[0],
	},
};

/** Useful for reviewing the full information density without interacting with the expander. */
export const AllFindingsVisible: Story = {
	args: {
		artifact,
		run: artifact.runs[0],
		initialFindingCount: 10,
	},
};
