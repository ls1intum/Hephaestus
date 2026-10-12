/**
 * One practice group's practices and the reviews that reached one of them, as the detail levels
 * show them: the standings of the group the practice-profile fixtures open, one result in full with
 * its problems, and three runs on the same practice. Beside them, one result per shape the row
 * draws differently: a search the row shows under "What was checked", open questions on a result
 * that could not be determined, and an example from the work on a result that is met.
 */
import type {
	EvidenceCitation,
	PracticeGroupReviewRun,
	PracticeResultDetail,
	PracticeStanding,
} from "@/api/types.gen";
import { ARTIFACT_KIND } from "@/lib/artifact-kinds";
import { daysBefore } from "@/stories/story-clock";

import { packagingGroup, practicesByGroup, pullRequest } from "./practice-profile-story-mock-data";

const packagingPractices = practicesByGroup[packagingGroup.slug] ?? [];

/**
 * What the detail level shows and the profile's list does not: each practice's prose, and the
 * trend of the one the runs below reviewed. The practices themselves come from
 * `practicesByGroup`, so the group has one practice list across the story fixtures.
 */
const detailDepth: Record<string, Partial<PracticeStanding>> = {
	"scope-one-reviewable-change": {
		whyItMatters:
			"A change that does one thing is faster to understand, safer to revert and easier to review well.",
		whatGoodLooksLike:
			"One concern per pull request; a refactor lands before the behaviour change that needed it.",
		direction: "IMPROVING",
		trendSupport: {
			currentOpportunities: 6,
			previousOpportunities: 5,
			opportunities: 11,
			opportunitiesUntilComparable: 0,
			standingWork: 4,
			reviewedWork: 11,
			reviewedWorkKind: ARTIFACT_KIND.pullRequest,
			calendarSpanDays: 12,
			bundleSize: 4,
			ropeHalfWidth: 0.15,
			credibilityThreshold: 0.9,
		},
	},
	"reviewable-diff-size": {
		whyItMatters: "A diff a reviewer can hold in their head is the one they read line by line.",
		whatGoodLooksLike: "A few hundred lines of real change, with the mechanical parts split out.",
	},
	"describe-what-and-why": {
		whyItMatters: "Reviewers need intent to judge whether the change solves the right problem.",
		whatGoodLooksLike: "A concise summary, the motivation, and how it was verified.",
	},
	"ready-and-traceable-handoff": {
		whyItMatters: "The issue carries the discussion a reviewer would otherwise have to ask for.",
	},
};

export const detailPractices: PracticeStanding[] = packagingPractices.map((practice) => ({
	...practice,
	...detailDepth[practice.slug],
}));

/** The practice the runs below reviewed, as the practice level opens it. */
export const focusedChanges: PracticeStanding = detailPractice("scope-one-reviewable-change");

/** The group's practice no feedback card names: a level with nothing to list. */
export const unwrittenAbout: PracticeStanding = detailPractice(
	"commit-subjects-explain-each-change",
);

function detailPractice(slug: string): PracticeStanding {
	const practice = detailPractices.find((candidate) => candidate.slug === slug);
	if (practice === undefined) {
		throw new Error(`${packagingGroup.slug} has no practice ${slug}.`);
	}
	return practice;
}

/** The pull request the runs below reviewed, as a result names it. */
const reviewedPullRequest = { artifactId: 902, artifactKind: "scm.pull_request" } as const;

/** A result the current rules made as the work arrived — the ordinary case. */
const live = {
	origin: "LIVE",
	claimCurrentness: "CURRENT",
	observations: [],
} satisfies Partial<PracticeResultDetail>;

/**
 * Two quotes of the pull request itself, as the runner records them: offsets into its own
 * serialised context, which is why the row folds them into one block named for the source rather
 * than into two blocks pointing at `metadata.json`.
 */
const pullRequestItself: EvidenceCitation[] = [
	{
		sourceKind: "scm.pull-request.core",
		artifactPath: "HephaestusTest/practice-validation#902",
		path: "inputs/context/metadata.json",
		startLine: 4,
		endLine: 4,
		quote: '"title" : "Page size"',
		quoteRedacted: false,
	},
	{
		sourceKind: "scm.pull-request.core",
		artifactPath: "HephaestusTest/practice-validation#902",
		path: "inputs/context/metadata.json",
		startLine: 9,
		endLine: 9,
		quote: '"body" : ""',
		quoteRedacted: false,
	},
];

const PULL_REQUEST_902 = "HephaestusTest/practice-validation#902";
const LOADER_PATH =
	"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/catalog/PracticeCatalogLoader.java";

/**
 * Not met, with three problems, each at its own place in the diff. The result's own citations are
 * the pull request's title and body, which no single problem owns.
 */
export const detailResult: PracticeResultDetail = {
	id: "00000000-0000-0000-0000-000000000102",
	feedbackResponse: { feedbackId: "00000000-0000-0000-0000-000000000103" },
	practiceSlug: focusedChanges.slug,
	practiceName: focusedChanges.name,
	summary: "The refactor and the fix arrived together",
	outcome: "NOT_MET",
	severity: "MAJOR",
	observedAt: daysBefore(2),
	...live,
	...reviewedPullRequest,
	artifactUrl: "https://github.com/HephaestusTest/practice-validation/pull/902",
	evidenceRationale:
		"The diff renames the loader's package and changes its caching in the same commit, so a reviewer cannot tell which hunk carries the behaviour change.",
	deliveredFeedback:
		"Land the rename on its own first; the caching change then reads as the small diff it is.",
	nextStep:
		"Land the rename on its own first; the caching change then reads as the small diff it is.",
	observations: [
		{
			observationId: "00000000-0000-0000-0000-0000000001b1",
			position: 0,
			severity: "MAJOR",
			summary: "The caching change hides inside the package move",
			explanation:
				"`PracticeCatalogLoader` gains `@Cacheable` in the same hunk that moves it, so the diff shows the behaviour change as a moved file.",
			nextStep: "Move the class in one commit and add the cache in the next.",
			citations: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: PULL_REQUEST_902,
					path: LOADER_PATH,
					side: "NEW",
					startLine: 41,
					endLine: 46,
					quote:
						'@Cacheable(cacheNames = "practice-catalog", key = "#workspaceId")\n' +
						"public PracticeCatalog load(long workspaceId) {\n" +
						"    var practices = repository.findAllByWorkspaceId(workspaceId);\n" +
						"    return PracticeCatalog.of(practices);\n" +
						"}\n" +
						"// moved from de.tum.cit.aet.hephaestus.practices.PracticeLoader",
					quoteRedacted: false,
				},
			],
		},
		{
			observationId: "00000000-0000-0000-0000-0000000001b2",
			position: 1,
			severity: "MINOR",
			summary: "The old loader is deleted rather than moved",
			explanation:
				"Git records a delete and an add, so the history of the old file stops at this commit.",
			citations: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: PULL_REQUEST_902,
					path: "server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/PracticeLoader.java",
					side: "OLD",
					startLine: 1,
					endLine: 1,
					quoteRedacted: false,
				},
			],
		},
		{
			observationId: "00000000-0000-0000-0000-0000000001b3",
			position: 2,
			severity: "MINOR",
			summary: "The description names only the move",
			explanation: "A reviewer reading the description would not look for a behaviour change.",
			nextStep: "Say in the description that the loader now caches per workspace.",
			citations: [],
		},
	],
	evidence: { citations: pullRequestItself, undecidability: [] },
};

export const detailRun: PracticeGroupReviewRun = {
	reviewId: "00000000-0000-0000-0000-000000000101",
	reviewedAt: daysBefore(2),
	reviewedWork: pullRequest(902),
	practiceResults: [
		detailResult,
		{
			id: "00000000-0000-0000-0000-000000000104",
			practiceSlug: "describe-what-and-why",
			practiceName: "Describe what changed and why",
			summary: "The description names the motivation",
			outcome: "MET",
			observedAt: daysBefore(2),
			...live,
			...reviewedPullRequest,
			evidenceRationale:
				"The description opens with why the loader moved, before it says what moved.",
		},
	],
};

/** Three reviews that reached the practice, newest first, as the level's timeline shows them. */
export const detailRuns: PracticeGroupReviewRun[] = [
	detailRun,
	{
		reviewId: "00000000-0000-0000-0000-000000000111",
		reviewedAt: daysBefore(4),
		reviewedWork: pullRequest(898),
		practiceResults: [
			{
				id: "00000000-0000-0000-0000-000000000112",
				feedbackResponse: { feedbackId: "00000000-0000-0000-0000-000000000113" },
				practiceSlug: focusedChanges.slug,
				practiceName: focusedChanges.name,
				summary: "A dependency bump was carried alongside a behaviour change",
				outcome: "NOT_MET",
				severity: "MINOR",
				observedAt: daysBefore(4),
				...live,
				artifactId: 898,
				artifactKind: "scm.pull_request",
				evidenceRationale:
					"The lockfile change and the retry loop landed in one commit, so the bump cannot be reverted alone.",
				deliveredFeedback: "Bump dependencies in a pull request of their own.",
			},
		],
	},
	{
		reviewId: "00000000-0000-0000-0000-000000000121",
		reviewedAt: daysBefore(11),
		reviewedWork: pullRequest(884),
		practiceResults: [
			{
				id: "00000000-0000-0000-0000-000000000122",
				feedbackResponse: { feedbackId: "00000000-0000-0000-0000-000000000123" },
				practiceSlug: focusedChanges.slug,
				practiceName: focusedChanges.name,
				summary: "One concern, one pull request: the export and nothing else",
				outcome: "MET",
				observedAt: daysBefore(11),
				...live,
				artifactId: 884,
				artifactKind: "scm.pull_request",
				evidenceRationale: "Every hunk serves the export; nothing else moved.",
			},
		],
	},
];

/**
 * A review the reader asked for, newer than the three above, that found no problem on the practice
 * the live reviews still read as needing attention.
 */
export const requestedCleanRun: PracticeGroupReviewRun = {
	reviewId: "00000000-0000-0000-0000-000000000141",
	reviewedAt: daysBefore(1),
	reviewedWork: pullRequest(902),
	practiceResults: [
		{
			id: "00000000-0000-0000-0000-000000000142",
			practiceSlug: focusedChanges.slug,
			practiceName: focusedChanges.name,
			summary: "The rename and the caching change are now separate commits",
			outcome: "MET",
			observedAt: daysBefore(1),
			origin: "MANUAL",
			claimCurrentness: "CURRENT",
			observations: [],
			...reviewedPullRequest,
			evidenceRationale: "The rename lands in its own commit; the caching change is a small diff.",
		},
	],
};

/** What every warrant fixture below shares: the practice level's own practice, on the same work. */
const onThatPullRequest = {
	practiceSlug: focusedChanges.slug,
	practiceName: focusedChanges.name,
	observedAt: daysBefore(2),
	...live,
	...reviewedPullRequest,
	artifactUrl: "https://github.com/HephaestusTest/practice-validation/pull/902",
} satisfies Partial<PracticeResultDetail>;

/** Nothing found where the review looked, with the reach of that absence written down. */
export const searchedAndFoundNothing: PracticeResultDetail = {
	...onThatPullRequest,
	id: "00000000-0000-0000-0000-000000000131",
	summary: "No test covers the new caching branch",
	outcome: "NOT_MET",
	severity: "MAJOR",
	evidenceRationale:
		"The branch is new in this change: `loadFromCache` is called in `DocumentLoader`, and no test file in the diff names it at all.",
	observations: [
		{
			observationId: "00000000-0000-0000-0000-000000000132",
			position: 0,
			severity: "MAJOR",
			summary: "The cache hit path has no test",
			explanation: "Every test in the diff starts from an empty cache.",
			citations: [],
		},
	],
	evidence: {
		citations: [],
		undecidability: [],
		search: {
			lookedFor: "a test exercising the new caching branch of the loader",
			consulted: ["scm.pull-request.diff", "scm.repository.tree"],
			boundary:
				"every test file the diff touches and the repository's own test tree; I did not read test sources outside it",
		},
	},
};

/**
 * A next step written about this work while nothing was delivered: the row shows the reviewer's
 * own sentence, which is the one the reader can act on.
 */
export const nextStepWithoutDelivery: PracticeResultDetail = {
	...onThatPullRequest,
	id: "00000000-0000-0000-0000-000000000161",
	summary: "The rename and the caching change share one commit",
	outcome: "NOT_MET",
	severity: "MINOR",
	nextStep: "Split the commit so the rename can be reverted without the caching change.",
};

/**
 * The review could not settle the practice: two questions stayed open, each with what would settle
 * it, and one with the place it was asked about.
 */
export const undeterminedResult: PracticeResultDetail = {
	...onThatPullRequest,
	id: "00000000-0000-0000-0000-000000000171",
	summary: "Whether the rename was asked for is not on the record",
	outcome: "UNDETERMINED",
	evidenceRationale:
		"The description links an issue the review could not read, and the diff alone does not say whether the move was requested.",
	evidence: {
		citations: [],
		undecidability: [
			{
				openQuestion: "Was the package move asked for in the linked issue?",
				wouldSettleIt: "The text of issue #880, which the description links.",
				citation: {
					sourceKind: "scm.pull-request.core",
					artifactPath: PULL_REQUEST_902,
					path: "inputs/context/metadata.json",
					startLine: 9,
					endLine: 9,
					quote: '"body" : "Closes #880"',
					quoteRedacted: false,
				},
			},
			{
				openQuestion: "Does anything outside this repository import the old package?",
				wouldSettleIt: "A search of the other services that depend on the catalog.",
			},
		],
	},
};

/** Met, with the one place in the work the review points at as it reads. */
export const metWithExample: PracticeResultDetail = {
	...onThatPullRequest,
	id: "00000000-0000-0000-0000-000000000181",
	summary: "Each commit carries one concern",
	outcome: "MET",
	evidenceRationale: "The rename and the caching change land in separate commits.",
	example: {
		note: "This commit moves the loader and changes nothing else, so it can be reverted on its own.",
		citation: {
			sourceKind: "scm.pull-request.diff",
			artifactPath: PULL_REQUEST_902,
			path: LOADER_PATH,
			side: "NEW",
			startLine: 1,
			endLine: 1,
			quote: "package de.tum.cit.aet.hephaestus.practices.catalog;",
			quoteRedacted: false,
		},
	},
	evidence: { citations: [], undecidability: [] },
};
