import { addDays } from "date-fns";
import type {
	AgentJob,
	AgentJobUsage,
	ArtifactTrace,
	AutonomyAssignment,
	DeliveryPolicyTrace,
	EvidenceCitation,
	Observation,
	OpenQuestion,
	Practice,
	PracticeResultExample,
	PracticeReviewBucket,
	PracticeReviewCounts,
	PracticeReviewObservationRow,
	PracticeReviewOverview,
	PracticeReviewQueue,
	PracticeReviewQueueItem,
	PracticeReviewTiming,
	PracticeRevision,
	ReviewedWorkRef,
	ReviewFeedback,
	ReviewFeedbackCounts,
	ReviewFeedbackDetail,
	ReviewFeedbackLocationCounts,
	ReviewFeedbackReasonCount,
	ReviewPracticeScripts,
	ReviewResult,
	ReviewResultCounts,
	ReviewResultDetail,
	ReviewRunSummary,
	ReviewRunTarget,
	ReviewSubject,
	ReviewWorkFacts,
	WorkspaceMembership,
} from "@/api/types.gen";
import { EMPTY_REVIEW_SETTINGS } from "@/components/admin/practice-editor/review-settings";

import {
	REVIEW_RANGE_DEFS,
	reviewRangeStart,
} from "@/components/admin/practice-reviews/review-range";
import {
	artifactTrace,
	deniedDeliveryPolicyEvaluation,
} from "@/components/practice-trace/fixtures";
import { OPERATOR_WITHHELD_TEXT } from "@/components/practice-vocabulary/delivery-place-defs";
import { ARTIFACT_KIND, type KnownArtifactKind } from "@/lib/artifact-kinds";
import { toDayParam } from "@/lib/date-range-search";
import { hasText } from "@/lib/text";
import { minutesAfter, minutesBefore, STORY_NOW } from "@/stories/story-clock";

import type { OutcomeScope } from "./review-outcomes";
import type { OverviewRegionState } from "./review-states";

// The only thing written by hand is REVIEW_FIXTURE: reviews, their results and the feedback
// those drove. Every count, preview, truncation flag and summary is computed from it below, so a
// fixture whose tallies contradict the records they count is not something you can write.

// ---------------------------------------------------------------------------------------------
// People
// ---------------------------------------------------------------------------------------------

const ada: ReviewSubject = { id: 7, login: "ada", name: "Ada Lovelace" };
const grace: ReviewSubject = { id: 9, login: "grace", name: "Grace Hopper" };
const alan: ReviewSubject = { id: 11, login: "alan", name: "Alan Turing" };
const katherine: ReviewSubject = { id: 14, login: "katherine", name: "Katherine Johnson" };
const barbara: ReviewSubject = { id: 18, login: "barbara", name: "Barbara Liskov" };

const membership = {
	createdAt: new Date(STORY_NOW),
	hidden: false,
	eligibleForPracticeReview: true,
};

export const workspaceMembers: WorkspaceMembership[] = [
	{ ...membership, userId: ada.id, userLogin: ada.login, userName: ada.name, role: "MEMBER" },
	{ ...membership, userId: grace.id, userLogin: grace.login, userName: grace.name, role: "ADMIN" },
	{ ...membership, userId: alan.id, userLogin: alan.login, userName: alan.name, role: "MEMBER" },
	{
		...membership,
		userId: katherine.id,
		userLogin: katherine.login,
		userName: katherine.name,
		role: "MEMBER",
	},
	{
		...membership,
		userId: barbara.id,
		userLogin: barbara.login,
		userName: barbara.name,
		role: "MEMBER",
	},
];

// ---------------------------------------------------------------------------------------------
// The work under review
// ---------------------------------------------------------------------------------------------

/**
 * A run's target, written once per piece of work. The ref a result or a piece of feedback
 * carries is `reviewedWork`, labelled the way the server's `ReviewedWorkLabels` labels it: `#22` for
 * a pull request or an issue, `!425` for a merge request, `#channel` for a conversation, its title
 * for a document.
 */
export type ReviewWork = ReviewRunTarget & {
	reviewedWork: ReviewedWorkRef & { kind: KnownArtifactKind };
};

const reviewOf = (
	provider: ReviewedWorkRef["provider"],
	title: string,
	reviewedWork: ReviewWork["reviewedWork"],
): ReviewWork => ({
	type: reviewedWork.kind,
	title,
	// The server names the work on the ref as well, provider included. A conversation thread has no
	// title of its own, and a document's title is already its label, so neither carries one.
	reviewedWork:
		reviewedWork.kind === ARTIFACT_KIND.conversationThread ||
		reviewedWork.kind === ARTIFACT_KIND.document
			? { ...reviewedWork, provider }
			: { ...reviewedWork, provider, title },
});

// Every list row and every detail header draws its glyph from the provider and its words from the
// kind, so a fixture on a single provider would let a wrong mark or a missing label ship.
export const reviewArtifact = reviewOf(
	"GITHUB",
	"Cache the workspace member lookup on the review path",
	{
		id: "42",
		kind: ARTIFACT_KIND.pullRequest,
		label: "#1423",
		container: "ls1intum/Hephaestus",
		url: "https://github.com/ls1intum/Hephaestus/pull/1423",
	},
);

export const gitlabMergeRequest = reviewOf(
	"GITLAB",
	"Move invoice numbering behind the billing boundary",
	{
		id: "43",
		kind: ARTIFACT_KIND.pullRequest,
		label: "!88",
		container: "platform/billing-service",
		url: "https://gitlab.example.com/platform/billing-service/-/merge_requests/88",
	},
);

export const slackConversation = reviewOf(
	"SLACK",
	"How should we roll back the pricing migration?",
	{
		id: "81",
		kind: ARTIFACT_KIND.conversationThread,
		label: "#engineering",
		url: "https://example.slack.com/archives/C01/p1721400000",
	},
);

export const outlineDocument = reviewOf("OUTLINE", "Runbook: restoring a workspace from backup", {
	id: "96",
	kind: ARTIFACT_KIND.document,
	label: "Runbook: restoring a workspace from backup",
	url: "https://docs.example.com/doc/runbook-restore",
});

/**
 * No review in the fixture reaches an issue, but `scm.issue` is a kind this build has copy and a
 * glyph for, so something has to render it.
 */
export const trackerIssue = reviewOf(
	"GITHUB",
	"Reviews of documents do not record which revision they read",
	{
		id: "204",
		kind: ARTIFACT_KIND.issue,
		label: "#204",
		container: "ls1intum/Hephaestus",
		url: "https://github.com/ls1intum/Hephaestus/issues/204",
	},
);

const webhookRetryPullRequest = reviewOf(
	"GITHUB",
	"Retry webhook deliveries with backoff instead of dropping them",
	{
		id: "44",
		kind: ARTIFACT_KIND.pullRequest,
		label: "#1431",
		container: "ls1intum/Hephaestus",
		url: "https://github.com/ls1intum/Hephaestus/pull/1431",
	},
);

const leagueColumnsPullRequest = reviewOf("GITHUB", "Drop the unused league columns", {
	id: "45",
	kind: ARTIFACT_KIND.pullRequest,
	label: "#1436",
	container: "ls1intum/Hephaestus",
	url: "https://github.com/ls1intum/Hephaestus/pull/1436",
});

const invoiceBackfillMergeRequest = reviewOf("GITLAB", "Backfill the invoice sequence table", {
	id: "46",
	kind: ARTIFACT_KIND.pullRequest,
	label: "!91",
	container: "platform/billing-service",
	url: "https://gitlab.example.com/platform/billing-service/-/merge_requests/91",
});

// ---------------------------------------------------------------------------------------------
// The catalogue
// ---------------------------------------------------------------------------------------------

const groupNames = {
	"code-quality": "Code quality",
	architecture: "Architecture",
	testing: "Testing",
	documentation: "Documentation",
	collaboration: "Collaboration",
} as const;

type GroupSlug = keyof typeof groupNames;

const group = (slug: GroupSlug) => ({ slug, name: groupNames[slug] });

export const practiceGroups = Object.entries(groupNames).map(([slug, name], index) => ({
	id: index + 1,
	slug,
	name,
	active: true,
	displayOrder: index,
	createdAt: new Date("2026-01-01T00:00:00Z"),
}));

/**
 * Fills in the governance fields no review surface reads, so a fixture spells out only what it
 * varies. `whyItMatters` and `whatGoodLooksLike` are the prose `PracticeDetailHoverCard` shows: a
 * fixture without them renders a bare link and its story passes while proving nothing.
 */
function practiceFixture(
	practice: Pick<
		Practice,
		"id" | "slug" | "name" | "groupSlug" | "criteria" | "whyItMatters" | "whatGoodLooksLike"
	> & { displayOrder: number; autonomy: AutonomyAssignment["effective"] },
): Practice {
	const { autonomy, ...rest } = practice;
	return {
		...rest,
		artifactKind: "scm.pull_request",
		createdAt: new Date("2026-01-01T00:00:00Z"),
		updatedAt: new Date("2026-06-01T00:00:00Z"),
		...EMPTY_REVIEW_SETTINGS,
		autonomy: { effective: autonomy, inherited: false, source: "PRACTICE" },
		deliveryBehavior: { summaryOnly: false },
		automatedReviewPolicy: {
			automatedReview: {
				evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
				mode: "LANGUAGE_MODEL",
			},
			knownLimitations: [],
			sourceContractVersion: "1.2.0",
			whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
		},
		automatedReviewValidation: {
			policyDigest: "sha256:0",
			reviewRuleFingerprint: "rule:1",
			sourceContractVersion: "1.2.0",
			status: "AUTHOR_DECLARED",
		},
	};
}

export const workspacePractices: Practice[] = [
	practiceFixture({
		id: 1,
		slug: "thin-controllers",
		name: "Thin controllers",
		groupSlug: "code-quality",
		criteria: "A controller validates its input, delegates, and maps the result. Nothing else.",
		whyItMatters:
			"Logic that lives in a controller can only be tested through HTTP, so it tends not to be tested at all.",
		whatGoodLooksLike:
			"The controller method reads as a list of three steps and names a service that does the work.",
		displayOrder: 0,
		autonomy: "HUMAN_APPROVAL",
	}),
	practiceFixture({
		id: 2,
		slug: "product-language",
		name: "Product language",
		groupSlug: "architecture",
		criteria: "Names in the code are the names the people using it would use.",
		whyItMatters:
			"A boundary named after its storage stops matching the product the day the storage changes, and every reader after that has to translate.",
		whatGoodLooksLike:
			"A route, a class or a column could be read aloud in a planning meeting without explanation.",
		displayOrder: 1,
		autonomy: "AUTOMATIC",
	}),
	practiceFixture({
		id: 3,
		slug: "errors-carry-context",
		name: "Errors carry their context",
		groupSlug: "code-quality",
		criteria:
			"A failure reports which operation failed and on what, not only that something went wrong.",
		whyItMatters:
			"An error that says only what went wrong sends the next person to the logs to work out where, and at three in the morning that is the whole incident.",
		whatGoodLooksLike:
			"The message names the thing being acted on and the action attempted, and two different causes do not share one status code.",
		displayOrder: 2,
		autonomy: "AUTOMATIC",
	}),
	practiceFixture({
		id: 4,
		slug: "tests-name-the-behaviour",
		name: "Tests name the behaviour",
		groupSlug: "testing",
		criteria: "A test's name states the behaviour it pins down, not the method it calls.",
		whyItMatters:
			"A red build should say what broke before anyone opens the file; a name like testCache2 makes the reader run the test to find out.",
		whatGoodLooksLike: "The failure line of a broken build reads as a sentence about the system.",
		displayOrder: 3,
		autonomy: "HUMAN_APPROVAL",
	}),
	practiceFixture({
		id: 5,
		slug: "the-change-explains-itself",
		name: "The change explains itself",
		groupSlug: "documentation",
		criteria: "The description says why the change was made, not only what it does.",
		whyItMatters:
			"Six months later the diff still says what changed. Nothing else records why, so the next person reverses the decision without knowing there was one.",
		whatGoodLooksLike:
			"A reviewer who was not in the room can tell what problem the change is answering.",
		displayOrder: 4,
		autonomy: "HUMAN_APPROVAL",
	}),
	practiceFixture({
		id: 6,
		slug: "decisions-are-written-down",
		name: "Decisions are written down",
		groupSlug: "collaboration",
		criteria:
			"A decision reached in a discussion ends up somewhere durable before the thread ends.",
		whyItMatters:
			"A choice that only exists in a chat thread is re-argued every time somebody new joins, and nobody can tell a decision from an opinion.",
		whatGoodLooksLike:
			"The thread ends with what was chosen, who chose it, and a link to where it now lives.",
		displayOrder: 5,
		autonomy: "AUTOMATIC",
	}),
];

// ---------------------------------------------------------------------------------------------
// The spec
// ---------------------------------------------------------------------------------------------

interface FeedbackSpec {
	id: string;
	/** The composed body exactly as the server stores it: Markdown, headings and all. The list's
	 * preview and truncation flag are derived from it. */
	body?: string;
	channel: ReviewFeedback["channel"];
	/** Where a conversation raised it; absent, delivered conversation feedback reads as the app's. */
	conversation?: "HEPH" | "SLACK";
	outcome: ReviewFeedback["deliveryState"];
	withheldFor?: ReviewFeedback["suppressionReason"];
	recipient: ReviewSubject;
	composedAt: string;
	deliveredAt?: string;
	/** The feedback this one took the place of. */
	replaces?: string;
	/** Where an inline note landed. Absent means it was posted as a summary on the work. */
	anchoredAt?: { path: string; startLine: number; endLine: number };
	/** Results it draws on, in render order; the first leads it. */
	from: string[];
}

/** One located problem under a not met result: an observation on the wire. */
interface ProblemSpec {
	severity: Observation["severity"];
	summary: string;
	explanation: string;
	citations: EvidenceCitation[];
	nextStep?: string;
}

interface ResultSpec {
	outcome: ReviewResult["outcome"];
	id: string;
	summary: string;
	evidenceRationale: string;
	practiceSlug: string;
	group: GroupSlug;
	severity?: ReviewResult["severity"];
	claimCurrentness?: ReviewResult["claimCurrentness"];
	observedAt: string;
	/** The result's own citations; each problem carries its own. */
	evidence?: EvidenceCitation[];
	/** A not met result's problems, in display order. Its severity is at least the worst of them. */
	problems?: ProblemSpec[];
	openQuestions?: OpenQuestion[];
	example?: PracticeResultExample;
}

interface RunSpec {
	id: string;
	work: ReviewWork;
	status: ReviewRunSummary["status"];
	startedAt: string;
	/** Whose work this review was about; every result and every recipient inherits it. */
	developer: ReviewSubject;
	origin?: ReviewResult["origin"];
	results: ResultSpec[];
	feedback: FeedbackSpec[];
}

const diff = (
	path: string,
	startLine: number,
	endLine: number,
	quote: string,
): EvidenceCitation => ({
	sourceKind: "scm.pull-request.diff",
	artifactPath: "inputs/context/change.json",
	path,
	side: "NEW",
	startLine,
	endLine,
	quote,
	quoteRedacted: false,
});

const cited = (sourceKind: string, path: string, quote: string, line = 1): EvidenceCitation => ({
	sourceKind,
	artifactPath: `inputs/context/${sourceKind.replaceAll(".", "-")}.json`,
	path,
	startLine: line,
	endLine: line,
	quote,
	quoteRedacted: false,
});

/**
 * A summary comment exactly as Hephaestus posts it on a pull request
 * (`PracticeFeedbackCommentFormatter`): the hidden marker, the composer's text, a rule, and the
 * `<sub>` footer with its two links. The stored body is this text, so every page that shows it
 * must render the HTML the way GitHub does.
 */
export const POSTED_SUMMARY_COMMENT = [
	"<!-- hephaestus:practice-review:774b9e9b-2c40-4d6b-9e76-7094dda3a7a2 -->",
	"The description needs its purpose and coverage. Use the description to name the need behind this change and identify which of the linked item's Done-when points it covers or defers.",
	"",
	"The title names the app-problem and user-story task, but the authored description only references issue #3 and gives no reason.",
	"",
	"---",
	"<sub>Practice review &middot; gpt-6-luna. This feedback is AI-generated and can be inaccurate. Answer or dispute it in [Hephaestus](https://hephaestus.example/w/aet/feedback/scm.pull_request/42).</sub>",
	"<sub>[Why you see this and how to stop it](https://hephaestus.example/settings#practice-feedback)</sub>",
	"",
].join("\n");

/** Match DeliveryComposer formatting, with the preview cut inside the first code fence. */
const LONG_BODY = [
	"2 issues to tighten in this change, plus one thing worth keeping.",
	"",
	"**🔴 A cache miss and a permission failure come back as the same 404** · `ReviewQueryService.java:118`",
	"",
	"You wrote:",
	"",
	"```java",
	"return repository.findVisible(workspaceId, id)",
	"    .orElseThrow(() -> new NotFoundException());",
	"```",
	"",
	"`findVisible` returns empty both when the row is not there and when the caller may not see it.",
	"Callers get one 404 for two very different situations, so an operator debugging a support ticket",
	"cannot tell a stale link from a missing grant without reading the query.",
	"",
	"Separate the lookup from the check: fetch the row, then decide whether this caller may have it, and",
	"answer 403 when they may not. The extra query costs a millisecond and buys a diagnosable error.",
	"",
	"_Why this matters:_ an error that says only what went wrong sends the next person to the logs to",
	"work out where.",
	"",
	"---",
	"",
	"**🟡 Three of the new tests are named after the method they call** · `ReviewQueryServiceTest.java:44`",
	"",
	"`testCache1`, `testCache2` and `testCache3` pin down three different behaviours — a cold read, a",
	"warm read and an eviction after a membership change — and none of the three names says so. When one",
	"of them goes red in CI the failure line tells the reader nothing they can act on.",
	"",
	"Try `returnsTheCachedMembersOnASecondRead` and its siblings. The build output then reads as a",
	"sentence about the system.",
	"",
	"---",
	"",
	"**Worth keeping:** the controller stays three lines long — parse, delegate, map — and the caching",
	"decision sits entirely inside the service. That is the shape that lets the rest of this be tested",
	"without HTTP.",
].join("\n");

/**
 * Spans every conclusion a review can reach and every outcome a piece of feedback can have, with a
 * withheld piece under each family an operator can filter by. Deliberately ordinary otherwise: a set
 * that is mostly failures and empties teaches a reader that the product mostly does not work.
 */
export const REVIEW_FIXTURE: RunSpec[] = [
	{
		id: "11111111-1111-1111-1111-111111111111",
		work: reviewArtifact,
		status: "COMPLETED",
		startedAt: "2026-07-28T13:35:00Z",
		developer: ada,
		results: [
			{
				id: "55555555-5555-5555-5555-555555555555",
				summary: "The controller delegates before it does anything else",
				evidenceRationale:
					"The handler is three statements long: it binds the request, calls ReviewQueryService and maps the result. The caching decision that arrived with this change sits entirely inside the service, so it can be exercised without standing up HTTP.",
				practiceSlug: "thin-controllers",
				group: "code-quality",
				outcome: "MET",
				observedAt: "2026-07-28T13:40:00Z",
				evidence: [
					diff(
						"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryController.java",
						61,
						64,
						"var page = reviewQueryService.results(workspaceSlug, filter, pageable);\nreturn ResponseEntity.ok(page.map(ReviewResultDTO::from));",
					),
				],
			},
			{
				id: "66666666-6666-6666-6666-666666666666",
				summary: "A cache miss and a permission failure come back as the same 404",
				evidenceRationale:
					"findVisible returns an empty Optional both when the row does not exist and when the caller has no grant on the workspace, and the only caller turns either into a NotFoundException. Two situations that need different answers — retry the link, or ask for access — arrive as one.",
				practiceSlug: "errors-carry-context",
				group: "code-quality",
				outcome: "NOT_MET",
				severity: "MAJOR",
				observedAt: "2026-07-28T13:39:00Z",
				evidence: [
					cited(
						"scm.review-threads",
						"Review thread on ReviewQueryService.java",
						"Do we lose the difference between 'gone' and 'not yours' here?",
					),
				],
				problems: [
					{
						severity: "MAJOR",
						summary: "A missing row and a missing grant both become a 404",
						explanation:
							"`findVisible` returns an empty `Optional` in both cases, and `orElseThrow` turns either into a `NotFoundException`.",
						citations: [
							diff(
								"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryService.java",
								116,
								119,
								"return repository.findVisible(workspaceId, id)\n    .orElseThrow(() -> new NotFoundException());",
							),
						],
						nextStep:
							"Fetch the row first, then check the grant, and answer 403 when the caller may not read it.",
					},
					{
						severity: "MINOR",
						summary: "The guard’s 403 never reaches this endpoint",
						explanation:
							"`WorkspaceGuard` already throws a `ForbiddenException` with the workspace in its message, but this lookup runs before the guard is asked.",
						citations: [
							cited(
								"scm.repository.tree",
								"server/application/src/main/java/de/tum/cit/aet/hephaestus/core/security/WorkspaceGuard.java",
								'throw new ForbiddenException("No grant on workspace " + slug);',
								88,
							),
						],
					},
					{
						severity: "INFO",
						summary: "The NotFoundException carries no message",
						explanation: "The log line says only that something was not found, not which review.",
						citations: [
							diff(
								"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryService.java",
								119,
								119,
								"    .orElseThrow(() -> new NotFoundException());",
							),
						],
						nextStep: "Name the review id in the exception.",
					},
				],
				example: {
					note: "The controller maps every error in one handler, so the split costs one new branch there.",
					citation: diff(
						"server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryController.java",
						88,
						90,
						"@ExceptionHandler(NotFoundException.class)\nResponseEntity<ProblemDetail> notFound(NotFoundException e) {",
					),
				},
			},
			{
				id: "77777777-7777-7777-7777-777777777777",
				summary: "Three of the new tests are named after the method they call",
				evidenceRationale:
					"testCache1, testCache2 and testCache3 pin down a cold read, a warm read and an eviction after a membership change. The names carry none of that, so a red build names a file and a number rather than the behaviour that broke.",
				practiceSlug: "tests-name-the-behaviour",
				group: "testing",
				outcome: "NOT_MET",
				severity: "MINOR",
				observedAt: "2026-07-28T13:38:00Z",
				problems: [
					{
						severity: "MINOR",
						summary: "testCache1 and testCache2 name the method, not the read they pin down",
						explanation:
							"One is a cold read and one a warm read. A red build names a number, not the behaviour that broke.",
						citations: [
							diff(
								"server/application/src/test/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryServiceTest.java",
								44,
								46,
								"@Test\nvoid testCache1() {",
							),
						],
						nextStep: "Rename them for the read: `returnsTheCachedMembersOnASecondRead`.",
					},
					{
						severity: "INFO",
						summary: "testCache3 does not say that a membership change evicts",
						explanation:
							"The eviction is the most surprising rule of the three, and its name hides it.",
						citations: [
							diff(
								"server/application/src/test/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryServiceTest.java",
								71,
								72,
								"@Test\nvoid testCache3() {",
							),
						],
					},
				],
			},
			{
				id: "88888888-8888-8888-8888-888888888888",
				summary: "The description lists the files touched and stops there",
				evidenceRationale:
					"The body of the pull request is a bullet per changed file. Nothing in it says what was slow, how slow, or why a cache was the answer rather than a narrower query — the questions a reviewer who was not in that conversation has to ask before they can agree.",
				practiceSlug: "the-change-explains-itself",
				group: "documentation",
				outcome: "NOT_MET",
				severity: "INFO",
				observedAt: "2026-07-28T13:37:00Z",
				problems: [
					{
						severity: "INFO",
						summary: "The description does not say what was slow",
						explanation:
							"It lists the changed files. A reviewer cannot tell why a cache was the answer rather than a narrower query.",
						citations: [
							cited(
								"scm.pull-request.core",
								"Pull request description",
								"- ReviewQueryService.java\n- ReviewQueryController.java\n- ReviewQueryServiceTest.java",
							),
						],
						nextStep: "Add one sentence on what was slow and how slow.",
					},
				],
			},
		],
		feedback: [
			{
				id: "33333333-3333-3333-3333-333333333333",
				body: "You kept the controller focused and moved the caching decision into the service, which is what lets the rest of this be tested without HTTP.",
				channel: "IN_CONTEXT",
				outcome: "DELIVERED",
				recipient: ada,
				composedAt: "2026-07-28T13:42:00Z",
				deliveredAt: "2026-07-28T13:43:00Z",
				from: ["55555555-5555-5555-5555-555555555555"],
			},
			{
				id: "44444444-4444-4444-4444-444444444444",
				body: LONG_BODY,
				channel: "IN_CONTEXT",
				outcome: "DELIVERED",
				recipient: ada,
				composedAt: "2026-07-28T13:41:00Z",
				deliveredAt: "2026-07-28T13:41:30Z",
				replaces: "99999999-3333-3333-3333-333333333333",
				from: ["66666666-6666-6666-6666-666666666666", "77777777-7777-7777-7777-777777777777"],
			},
			{
				id: "99999999-3333-3333-3333-333333333333",
				body: "The lookup collapses two different failures into one 404. Worth splitting before this merges.",
				channel: "IN_CONTEXT",
				outcome: "SUPERSEDED",
				recipient: ada,
				composedAt: "2026-07-28T09:12:00Z",
				deliveredAt: "2026-07-28T09:12:20Z",
				from: ["66666666-6666-6666-6666-666666666666"],
			},
			{
				id: "aaaaaaaa-3333-3333-3333-333333333333",
				body: "The description could say what was slow before the cache went in.",
				channel: "IN_CONTEXT",
				outcome: "SUPPRESSED",
				withheldFor: "COMPOSER_DEDUPED",
				recipient: ada,
				composedAt: "2026-07-28T13:41:00Z",
				from: ["88888888-8888-8888-8888-888888888888"],
			},
		],
	},
	{
		id: "22222222-2222-2222-2222-222222222222",
		work: gitlabMergeRequest,
		status: "COMPLETED",
		startedAt: "2026-07-27T09:10:00Z",
		developer: alan,
		// A confirmed campaign over work that already existed, so nothing from it is spoken aloud.
		origin: "BACKFILL",
		results: [
			{
				id: "bbbbbbbb-2222-2222-2222-222222222222",
				summary: "Invoice numbering leaks the ledger’s table name into the public API",
				evidenceRationale:
					"The response field is called ledgerSeqNo, which is the column the number is stored in. Callers outside billing have to learn the storage layout to read an invoice, and the day the ledger is replaced the field is either wrong or frozen.",
				practiceSlug: "product-language",
				group: "architecture",
				outcome: "NOT_MET",
				severity: "CRITICAL",
				claimCurrentness: "STALE",
				observedAt: "2026-07-27T09:15:00Z",
				problems: [
					{
						severity: "CRITICAL",
						summary: "The public invoice response names a ledger column",
						explanation:
							"Callers outside billing must learn the storage layout to read an invoice.",
						citations: [
							diff(
								"src/main/java/platform/billing/api/InvoiceResponse.java",
								22,
								22,
								"private Long ledgerSeqNo;",
							),
						],
						nextStep: "Call the field `invoiceNumber` before any caller depends on it.",
					},
					{
						severity: "MINOR",
						summary: "The changelog repeats the column name",
						explanation:
							"The release note tells callers to read `ledgerSeqNo`, which fixes the name in their code.",
						citations: [diff("CHANGELOG.md", 7, 7, "- Invoices now expose `ledgerSeqNo`.")],
					},
				],
				evidence: [
					cited(
						"outline.documents",
						"Naming guideline",
						"A field on a public response is product copy. It is read, shared and quoted more often than a heading.",
						22,
					),
				],
			},
			{
				id: "cccccccc-2222-2222-2222-222222222222",
				summary: "The change touches no controller",
				evidenceRationale:
					"Everything in the merge request sits under the billing domain package. There is no request handler in the diff, so this practice has nothing to look at here.",
				practiceSlug: "thin-controllers",
				group: "code-quality",
				outcome: "NOT_APPLICABLE",
				observedAt: "2026-07-27T09:14:00Z",
				evidence: [
					cited(
						"scm.repository.tree",
						"src/main/java/platform/billing/",
						"InvoiceNumbering.java, InvoiceResponse.java, LedgerSequence.java",
						1,
					),
				],
			},
		],
		feedback: [
			{
				id: "dddddddd-2222-2222-2222-222222222222",
				body: "The public response exposes a name from the ledger schema. Renaming it now costs one migration; later it costs every caller, and the callers are other teams.",
				channel: "IN_CONTEXT",
				outcome: "SUPPRESSED",
				withheldFor: "BACKFILL_QUIET",
				recipient: alan,
				composedAt: "2026-07-27T09:20:00Z",
				from: ["bbbbbbbb-2222-2222-2222-222222222222"],
			},
		],
	},
	{
		id: "33333333-3333-3333-3333-333333333333",
		work: slackConversation,
		status: "COMPLETED",
		startedAt: "2026-07-26T16:00:00Z",
		developer: grace,
		origin: "MANUAL",
		results: [
			{
				id: "eeeeeeee-3333-3333-3333-333333333333",
				summary: "The thread ends without naming what was chosen",
				evidenceRationale:
					"Four people weighed two rollback strategies over eleven messages and the last one is a thumbs-up. Nothing in the thread states which strategy won, so a reader arriving tomorrow cannot tell agreement from the end of the working day.",
				practiceSlug: "decisions-are-written-down",
				group: "collaboration",
				outcome: "NOT_MET",
				severity: "MINOR",
				claimCurrentness: "UNVERIFIABLE",
				observedAt: "2026-07-26T16:02:00Z",
				problems: [
					{
						severity: "MINOR",
						summary: "The last message leaves the choice to the on-call",
						explanation: "Nobody names the strategy that won, so the thread reads as unfinished.",
						citations: [
							cited(
								"slack.conversation.thread",
								"Message from Grace Hopper, 16:41",
								"either works honestly, whatever the on-call prefers",
								11,
							),
						],
						nextStep: "Post the chosen strategy and who chose it.",
					},
				],
				evidence: [
					cited(
						"hephaestus.observation-history",
						"Earlier result on this practice",
						"An earlier thread in the same channel also closed without a written decision.",
					),
				],
			},
			{
				id: "ffffffff-3333-3333-3333-333333333333",
				summary: "The rollback is described in the words the on-call would use",
				evidenceRationale:
					"Every message names the customer-visible effect — prices reverting, invoices reissuing — rather than the tables involved. Somebody paged at two in the morning could act on this thread without opening the schema.",
				practiceSlug: "product-language",
				group: "architecture",
				outcome: "MET",
				observedAt: "2026-07-26T16:01:00Z",
				evidence: [
					cited(
						"slack.conversation.thread",
						"Message from Alan Turing, 16:12",
						"customers on the old plan would see their price revert at the next invoice, not immediately",
						4,
					),
				],
			},
		],
		feedback: [
			{
				id: "11111111-4444-4444-4444-444444444444",
				body: "Before this thread scrolls away, drop the chosen strategy and who chose it into the runbook — a reader tomorrow cannot tell the thumbs-up from the end of the day.",
				channel: "IN_CHAT",
				outcome: "PREPARED",
				recipient: grace,
				composedAt: "2026-07-26T16:05:00Z",
				from: ["eeeeeeee-3333-3333-3333-333333333333"],
			},
			{
				id: "22222222-4444-4444-4444-444444444444",
				body: "You described the rollback in terms of what a customer would see rather than which tables move. That is the version somebody paged at 2am can act on.",
				channel: "IN_CHAT",
				conversation: "SLACK",
				outcome: "DELIVERED",
				recipient: grace,
				composedAt: "2026-07-26T16:06:00Z",
				deliveredAt: "2026-07-26T18:40:00Z",
				from: ["ffffffff-3333-3333-3333-333333333333"],
			},
		],
	},
	{
		id: "44444444-4444-4444-4444-444444444444",
		work: outlineDocument,
		status: "COMPLETED",
		startedAt: "2026-07-25T11:25:00Z",
		developer: barbara,
		results: [
			{
				id: "11111111-5555-5555-5555-555555555555",
				summary: "The runbook opens with the one step that cannot be undone",
				evidenceRationale:
					"Step one puts the workspace into maintenance before anything is restored, and the page says so in the first line rather than in a note at the bottom. A reader following the page top to bottom does the irreversible thing at the point where it is still safe.",
				practiceSlug: "the-change-explains-itself",
				group: "documentation",
				outcome: "MET",
				observedAt: "2026-07-25T11:30:00Z",
				evidence: [
					cited(
						"docs.document.core",
						"Runbook: restoring a workspace from backup",
						"Before anything else: put the workspace into maintenance. Restores started while writes are landing cannot be replayed.",
						3,
					),
				],
			},
			{
				id: "22222222-5555-5555-5555-555555555555",
				summary: "Whether the restore order was agreed cannot be told from the page",
				evidenceRationale:
					"The runbook states an order of steps, and nothing on the page or linked from it records who chose that order or why.",
				practiceSlug: "decisions-are-written-down",
				group: "collaboration",
				outcome: "UNDETERMINED",
				observedAt: "2026-07-25T11:29:00Z",
				openQuestions: [
					{
						openQuestion: "Was the restore order agreed with the on-call team?",
						wouldSettleIt: "A link to the discussion or incident that chose this order.",
						citation: cited(
							"docs.document.core",
							"Runbook: restoring a workspace from backup",
							"1. Put the workspace into maintenance.",
							5,
						),
					},
					{
						openQuestion: "Does the runbook replace the older restore page?",
						wouldSettleIt: "A note on either page that names the one in force.",
					},
				],
			},
		],
		feedback: [
			{
				id: "33333333-5555-5555-5555-555555555555",
				body: "The runbook puts the irreversible step first and says why, which is the order somebody under pressure will actually follow.",
				channel: "IN_CONTEXT",
				outcome: "FAILED",
				recipient: barbara,
				composedAt: "2026-07-25T11:32:00Z",
				from: ["11111111-5555-5555-5555-555555555555"],
			},
			{
				// A card on the developer's own practice pages. The server never sends its text to an
				// operator; it is written here so a story can prove no surface shows it.
				id: "44444444-5555-5555-5555-555555555555",
				body: "Private to Barbara: the runbook's first step is the one a tired reader most needs.",
				channel: "IN_APP",
				outcome: "DELIVERED",
				recipient: barbara,
				composedAt: "2026-07-25T11:33:00Z",
				deliveredAt: "2026-07-25T11:33:00Z",
				from: ["11111111-5555-5555-5555-555555555555"],
			},
		],
	},
	{
		id: "55555555-6666-6666-6666-666666666666",
		work: webhookRetryPullRequest,
		status: "COMPLETED",
		startedAt: "2026-07-29T08:05:00Z",
		developer: katherine,
		results: [
			{
				id: "44444444-6666-6666-6666-666666666666",
				summary: "A dropped delivery is logged at debug and never counted",
				evidenceRationale:
					"When the backoff gives up, the handler writes a debug line and returns. Nothing increments a counter and nothing reaches the dead-letter subject, so a provider outage looks identical to a quiet afternoon on every dashboard the team has.",
				practiceSlug: "errors-carry-context",
				group: "code-quality",
				outcome: "NOT_MET",
				severity: "MAJOR",
				observedAt: "2026-07-29T08:12:00Z",
				problems: [
					{
						severity: "MAJOR",
						summary: "Giving up on a delivery increments no counter",
						explanation: "A provider outage looks like a quiet afternoon on every dashboard.",
						citations: [
							diff(
								"server/application/src/main/java/de/tum/cit/aet/hephaestus/integration/core/webhook/WebhookRetryPolicy.java",
								74,
								75,
								'log.debug("giving up on {} after {} attempts", subject, attempts);\nreturn;',
							),
						],
						nextStep: "Count the dropped delivery with a Micrometer counter.",
					},
					{
						severity: "MINOR",
						summary: "The dropped delivery is logged at debug",
						explanation:
							"Production logs at info, so the line is never written where anyone reads it.",
						citations: [
							diff(
								"server/application/src/main/java/de/tum/cit/aet/hephaestus/integration/core/webhook/WebhookRetryPolicy.java",
								74,
								74,
								'log.debug("giving up on {} after {} attempts", subject, attempts);',
							),
						],
					},
				],
				evidence: [
					cited(
						"workspace.project-inventory",
						"Observability stack",
						"Micrometer counters are exported to Prometheus; there is no log-based alerting.",
					),
				],
			},
			{
				id: "55555555-7777-7777-7777-777777777777",
				summary: "The retry tests read as sentences about the backoff",
				evidenceRationale:
					"Each of the four new tests is named for the behaviour it fixes in place — that the delay doubles, that it stops at the ceiling, that a success resets it, that a 4xx is not retried. A red build points straight at which of the four rules broke.",
				practiceSlug: "tests-name-the-behaviour",
				group: "testing",
				outcome: "MET",
				observedAt: "2026-07-29T08:11:00Z",
				evidence: [
					diff(
						"server/application/src/test/java/de/tum/cit/aet/hephaestus/integration/core/webhook/WebhookRetryPolicyTest.java",
						31,
						32,
						"@Test\nvoid doublesTheDelayUntilItReachesTheCeiling() {",
					),
				],
			},
			{
				id: "66666666-7777-7777-7777-777777777777",
				summary: "The queue is called the outbox everywhere except in the config",
				evidenceRationale:
					"The class, the metric and the log lines all say outbox. The property is hephaestus.webhook.retry-buffer.*, so an operator reading a dashboard and an operator editing configuration are looking for two different words for one thing.",
				practiceSlug: "product-language",
				group: "architecture",
				outcome: "NOT_MET",
				severity: "MINOR",
				observedAt: "2026-07-29T08:10:00Z",
				problems: [
					{
						severity: "MINOR",
						summary: "The property says retry-buffer where everything else says outbox",
						explanation:
							"An operator reading a dashboard and one editing configuration look for two words.",
						citations: [
							diff(
								"server/application/src/main/resources/application.yml",
								118,
								118,
								"retry-buffer:\n  capacity: 5000",
							),
						],
						nextStep: "Rename the property to `hephaestus.webhook.outbox.*`.",
					},
				],
			},
		],
		feedback: [
			{
				id: "77777777-6666-6666-6666-666666666666",
				body: "Giving up on a delivery is the one moment worth counting. A debug line means a provider outage and a quiet afternoon look the same on every dashboard you have.",
				channel: "IN_CONTEXT",
				outcome: "SUPPRESSED",
				withheldFor: "ARTIFACT_MERGED",
				recipient: katherine,
				composedAt: "2026-07-29T08:20:00Z",
				from: ["44444444-6666-6666-6666-666666666666"],
			},
			{
				id: "88888888-6666-6666-6666-666666666666",
				body: "Each retry test is named for the rule it pins down, so a red build points at which of the four broke.",
				channel: "IN_CONTEXT",
				outcome: "SUPPRESSED",
				withheldFor: "RECIPIENT_OPTED_OUT",
				recipient: katherine,
				composedAt: "2026-07-29T08:19:00Z",
				from: ["55555555-7777-7777-7777-777777777777"],
			},
			{
				id: "99999999-6666-6666-6666-666666666666",
				body: "The dashboard says outbox and the config says retry-buffer. Picking one and using it in both places would save the next operator a search.",
				channel: "IN_CONTEXT",
				outcome: "DELIVERED",
				recipient: katherine,
				composedAt: "2026-07-29T08:18:00Z",
				deliveredAt: "2026-07-29T08:18:40Z",
				anchoredAt: {
					path: "server/application/src/main/resources/application.yml",
					startLine: 118,
					endLine: 120,
				},
				from: ["66666666-7777-7777-7777-777777777777"],
			},
		],
	},
	{
		id: "aaaaaaaa-8888-8888-8888-888888888888",
		work: leagueColumnsPullRequest,
		status: "RUNNING",
		startedAt: "2026-07-29T09:40:00Z",
		developer: grace,
		results: [],
		feedback: [],
	},
	{
		id: "bbbbbbbb-8888-8888-8888-888888888888",
		work: invoiceBackfillMergeRequest,
		status: "FAILED",
		startedAt: "2026-07-24T15:02:00Z",
		developer: alan,
		results: [],
		feedback: [],
	},
];

// ---------------------------------------------------------------------------------------------
// Derivations
// ---------------------------------------------------------------------------------------------

/** The cut the server applies to `bodyPreview`, mirrored here so `bodyTruncated` cannot claim
 * something the preview beside it contradicts. */
const BODY_PREVIEW_LENGTH = 320;

/**
 * The text the server lets an operator read: none for a private place (`OPERATOR_WITHHELD_TEXT`),
 * whose text the fixture still writes so a story can prove it never reaches the screen.
 */
function operatorBody(spec: FeedbackSpec): string | undefined {
	return OPERATOR_WITHHELD_TEXT[spec.channel] === undefined ? spec.body : undefined;
}

function preview(spec: FeedbackSpec) {
	const body = operatorBody(spec);
	return {
		bodyPreview: body?.slice(0, BODY_PREVIEW_LENGTH),
		bodyTruncated: (body?.length ?? 0) > BODY_PREVIEW_LENGTH,
	};
}

const allRuns = REVIEW_FIXTURE;
const allResultSpecs = allRuns.flatMap((run) => run.results.map((result) => ({ run, result })));
const allFeedbackSpecs = allRuns.flatMap((run) => run.feedback.map((item) => ({ run, item })));

/** The ids of the feedback written from one result, as the feedback list filters by it. */
export function feedbackIdsFrom(practiceResultId: string): ReadonlySet<string> {
	return new Set(feedbackFor(practiceResultId).map(({ item }) => item.id));
}

/** The feedback drawing on one result, newest first, as the detail surfaces list it. */
function feedbackFor(practiceResultId: string) {
	return allFeedbackSpecs
		.filter(({ item }) => item.from.includes(practiceResultId))
		.sort((a, b) => b.item.composedAt.localeCompare(a.item.composedAt));
}

/** One count per delivery state, as the server reports a review's or a result's feedback. */
const COUNT_OF: Record<ReviewFeedback["deliveryState"], keyof ReviewFeedbackCounts> = {
	AWAITING_APPROVAL: "awaitingApproval",
	DELIVERED: "delivered",
	DISCARDED: "discarded",
	FAILED: "failed",
	PARTIALLY_DELIVERED: "partiallyDelivered",
	PARTIALLY_FAILED: "partiallyFailed",
	PREPARED: "prepared",
	SUPERSEDED: "superseded",
	SUPPRESSED: "suppressed",
	UNCONFIRMED: "unconfirmed",
};

export function feedbackCounts(
	states: readonly ReviewFeedback["deliveryState"][],
): ReviewFeedbackCounts {
	const counts: ReviewFeedbackCounts = {
		awaitingApproval: 0,
		delivered: 0,
		discarded: 0,
		failed: 0,
		partiallyDelivered: 0,
		partiallyFailed: 0,
		prepared: 0,
		superseded: 0,
		suppressed: 0,
		unconfirmed: 0,
	};
	for (const state of states) {
		counts[COUNT_OF[state]] += 1;
	}
	return counts;
}

/** What the counts by reason return for these rows: the rows with a reason, by it. */
export function reasonCounts(rows: readonly ReviewFeedback[]): ReviewFeedbackReasonCount[] {
	const counts = new Map<NonNullable<ReviewFeedback["suppressionReason"]>, number>();
	for (const row of rows) {
		if (row.suppressionReason !== undefined) {
			counts.set(row.suppressionReason, (counts.get(row.suppressionReason) ?? 0) + 1);
		}
	}
	return [...counts].map(([reason, count]) => ({ reason, count }));
}

/** What the counts endpoint returns for these rows: by location, in the order the server sorts them. */
export function locationCounts(rows: readonly ReviewFeedback[]): ReviewFeedbackLocationCounts[] {
	const order: ReviewFeedback["location"][] = [
		"GITHUB",
		"GITLAB",
		"OTHER_WORK",
		"HEPH",
		"SLACK",
		"NEXT_CONVERSATION",
		"PRACTICE_PAGES",
	];
	return order.flatMap((at) => {
		const states = rows.filter((row) => row.location === at).map((row) => row.deliveryState);
		return states.length === 0 ? [] : [{ location: at, feedback: feedbackCounts(states) }];
	});
}

function disposition(practiceResultId: string): ReviewFeedbackCounts {
	return feedbackCounts(feedbackFor(practiceResultId).map(({ item }) => item.outcome));
}

function toResult(run: RunSpec, spec: ResultSpec): ReviewResult {
	const practice = workspacePractices.find((entry) => entry.slug === spec.practiceSlug);
	if (!practice) {
		throw new Error(`No practice named ${spec.practiceSlug} in the fixture catalogue`);
	}
	return {
		id: spec.id,
		agentJobId: run.id,
		reviewedWork: run.work.reviewedWork,
		group: group(spec.group),
		claimCurrentness: spec.claimCurrentness ?? "CURRENT",
		outcome: spec.outcome,
		feedback: disposition(spec.id),
		observedAt: new Date(spec.observedAt),
		origin: run.origin ?? "LIVE",
		practiceName: practice.name,
		practiceSlug: practice.slug,
		problemCount: spec.problems?.length ?? 0,
		severity: spec.severity,
		subject: run.developer,
		summary: spec.summary,
	};
}

/** The location the server derives: the channel, split by the work's provider or the conversation's. */
function locationOf(run: RunSpec, spec: FeedbackSpec): ReviewFeedback["location"] {
	switch (spec.channel) {
		case "IN_APP": {
			return "PRACTICE_PAGES";
		}
		case "IN_CONTEXT": {
			const { provider } = run.work.reviewedWork;
			return provider === "GITHUB" || provider === "GITLAB" ? provider : "OTHER_WORK";
		}
		case "IN_CHAT": {
			return spec.outcome === "DELIVERED" || spec.outcome === "UNCONFIRMED"
				? (spec.conversation ?? "HEPH")
				: "NEXT_CONVERSATION";
		}
	}
}

function toFeedback(run: RunSpec, spec: FeedbackSpec): ReviewFeedback {
	return {
		id: spec.id,
		agentJobId: run.id,
		reviewedWork: run.work.reviewedWork,
		...preview(spec),
		channel: spec.channel,
		location: locationOf(run, spec),
		createdAt: new Date(spec.composedAt),
		deliveredAt: hasText(spec.deliveredAt) ? new Date(spec.deliveredAt) : undefined,
		deliveryState: spec.outcome,
		practiceResultCount: spec.from.length,
		recipient: spec.recipient,
		replacesId: spec.replaces,
		subject: run.developer,
		suppressionReason: spec.withheldFor,
	};
}

/** Newest first, as the list endpoint returns them. */
export const reviewResults: ReviewResult[] = allResultSpecs
	.map(({ run, result }) => toResult(run, result))
	.sort((a, b) => b.observedAt.getTime() - a.observedAt.getTime());

/**
 * The problems of the not met results, newest first as the list endpoint returns them, each placed
 * at its first citation.
 */
export const reviewObservations: PracticeReviewObservationRow[] = allResultSpecs
	.flatMap(({ run, result: spec }) => {
		const result = toResult(run, spec);
		return (spec.problems ?? []).map((problem, index) => {
			const [place] = problem.citations;
			return {
				id: `${result.id.slice(0, -2)}${String(index).padStart(2, "0")}`,
				practiceResultId: result.id,
				practiceSlug: result.practiceSlug,
				practiceName: result.practiceName,
				group: result.group,
				severity: problem.severity,
				summary: problem.summary,
				sourceKind: place?.sourceKind,
				path: place?.path,
				startLine: place?.startLine,
				endLine: place?.endLine,
				side: place?.side,
				evidenceHidden: false,
				claimCurrentness: result.claimCurrentness,
				invalidatedAt: result.invalidatedAt,
				disputedAt: result.disputedAt,
				reviewedWork: result.reviewedWork,
				subject: result.subject,
				observedAt: result.observedAt,
				agentJobId: result.agentJobId,
			};
		});
	})
	.sort((a, b) => b.observedAt.getTime() - a.observedAt.getTime());

/** Newest first, as the list endpoint returns them. */
export const reviewFeedback: ReviewFeedback[] = allFeedbackSpecs
	.map(({ run, item }) => toFeedback(run, item))
	.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

/** Both tallies counted from the records they summarise, so a run card cannot claim a number the
 * list under it contradicts. */
export const reviewRuns: ReviewRunSummary[] = allRuns
	.map((run) => ({
		id: run.id,
		status: run.status,
		reviewOutcome: "REVIEWED" as const,
		target: run.work,
		createdAt: new Date(run.startedAt),
		practiceResults: {
			met: run.results.filter((r) => r.outcome === "MET").length,
			notMet: run.results.filter((r) => r.outcome === "NOT_MET").length,
			notApplicable: run.results.filter((r) => r.outcome === "NOT_APPLICABLE").length,
			undetermined: run.results.filter((r) => r.outcome === "UNDETERMINED").length,
		},
		feedback: feedbackCounts(run.feedback.map((f) => f.outcome)),
		origin: run.origin ?? "LIVE",
		developers: [run.developer],
		...practiceVerdicts(run),
		positionOnWork: allRuns.filter(
			(other) =>
				other.work.reviewedWork.kind === run.work.reviewedWork.kind &&
				other.work.reviewedWork.id === run.work.reviewedWork.id &&
				other.startedAt <= run.startedAt,
		).length,
		reviewsOnWork: allRuns.filter(
			(other) =>
				other.work.reviewedWork.kind === run.work.reviewedWork.kind &&
				other.work.reviewedWork.id === run.work.reviewedWork.id,
		).length,
		finishedAfterMerge: false,
		finishedAfterMergeStartedByMerge: false,
	}))
	.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

/**
 * One verdict per practice, as the server tallies a run: any not met outweighs a met, and a
 * practice whose every result did not apply did not apply. A finished run reached every
 * practice it recorded a result about.
 */
function practiceVerdicts(
	run: RunSpec,
): Pick<ReviewRunSummary, "practices" | "slippedPractices" | "practicesEvaluated"> {
	const bySlug = new Map<string, ResultSpec[]>();
	for (const result of run.results) {
		bySlug.set(result.practiceSlug, [...(bySlug.get(result.practiceSlug) ?? []), result]);
	}
	const practices = { notMet: 0, met: 0, notApplicable: 0, undetermined: 0 };
	const slippedPractices: ReviewRunSummary["slippedPractices"] = [];
	for (const [practiceSlug, recorded] of bySlug) {
		const outcomes = recorded.map((result) => result.outcome);
		if (outcomes.includes("NOT_MET")) {
			practices.notMet += 1;
			const practiceName =
				workspacePractices.find((practice) => practice.slug === practiceSlug)?.name ?? practiceSlug;
			slippedPractices.push({ practiceSlug, practiceName });
		} else if (outcomes.includes("MET")) {
			practices.met += 1;
		} else if (outcomes.every((outcome) => outcome === "NOT_APPLICABLE")) {
			practices.notApplicable += 1;
		} else {
			practices.undetermined += 1;
		}
	}
	slippedPractices.sort((a, b) => a.practiceName.localeCompare(b.practiceName));
	return {
		practices,
		slippedPractices,
		practicesEvaluated: run.status === "COMPLETED" ? bySlug.size : undefined,
	};
}

const JOB_TYPE_BY_ARTIFACT: Record<KnownArtifactKind, AgentJob["jobType"]> = {
	[ARTIFACT_KIND.pullRequest]: "PULL_REQUEST_REVIEW",
	[ARTIFACT_KIND.issue]: "ISSUE_REVIEW",
	[ARTIFACT_KIND.conversationThread]: "CONVERSATION_REVIEW",
	[ARTIFACT_KIND.document]: "DOCUMENT_REVIEW",
};

/**
 * The job record behind one review, derived from the same spec as its rows. A review detail page
 * takes its heading from the *job's* target and its rows from the review endpoints, so a job served
 * from an unrelated fixture makes the header name one pull request and every row underneath another.
 */
export function reviewJob(runId: string): AgentJob {
	const run = allRuns.find((entry) => entry.id === runId);
	if (!run) {
		throw new Error(`No review ${runId} in the fixture`);
	}
	const started = new Date(run.startedAt);
	const finished = run.status === "RUNNING" ? undefined : new Date(started.getTime() + 5 * 60_000);
	return {
		id: run.id,
		jobType: JOB_TYPE_BY_ARTIFACT[run.work.reviewedWork.kind],
		reviewOutcome: "REVIEWED",
		target: run.work,
		status: run.status,
		model: "gpt-5.4-mini",
		configSnapshot: { name: "Default reviewer", llmProvider: "OPENAI" },
		createdAt: started,
		availableAt: started,
		startedAt: started,
		// Forty seconds preparing, the rest of the five minutes with the model, a minute until the
		// first comment was posted, and three hours until the developer read the feedback.
		executionStartedAt: new Date(started.getTime() + 40_000),
		completedAt: finished,
		firstFeedbackPostedAt:
			run.status === "COMPLETED" && finished !== undefined
				? new Date(finished.getTime() + 60_000)
				: undefined,
		firstFeedbackReadAt:
			run.status === "COMPLETED" && finished !== undefined
				? new Date(finished.getTime() + 3 * 3_600_000)
				: undefined,
		deliveryStatus: run.status === "COMPLETED" ? "DELIVERED" : undefined,
		llmModel: "openai/gpt-oss-120b",
		llmTotalInputTokens: 24_000,
		llmTotalOutputTokens: 914,
		llmTotalReasoningTokens: 120,
		llmTotalCalls: 7,
		retryCount: 0,
		exitCode: run.status === "FAILED" ? 1 : 0,
		errorMessage:
			run.status === "FAILED"
				? "Cannot compute diff: all resolution strategies failed for commit 27f4e88c."
				: undefined,
	};
}

export function resultDetail(practiceResultId: string): ReviewResultDetail {
	const found = allResultSpecs.find(({ result }) => result.id === practiceResultId);
	if (!found) {
		throw new Error(`No result ${practiceResultId} in the fixture`);
	}
	const { run, result } = found;
	// The detail replaces the tallies with the records they were counting.
	const { feedback: _tally, problemCount: _problems, ...shared } = toResult(run, result);
	return {
		...shared,
		evidence: {
			citations: result.evidence ?? [],
			undecidability: result.openQuestions ?? [],
		},
		observations: (result.problems ?? []).map((problem, position) => ({
			...problem,
			observationId: `${result.id}-problem-${position}`,
			position,
		})),
		example: result.example,
		feedback: feedbackFor(practiceResultId).map(({ item }) => ({
			feedbackId: item.id,
			agentJobId: run.id,
			channel: item.channel,
			createdAt: new Date(item.composedAt),
			deliveredAt: hasText(item.deliveredAt) ? new Date(item.deliveredAt) : undefined,
			deliveryState: item.outcome,
			role: item.from[0] === practiceResultId ? "PRIMARY" : "SUPPORTING",
			suppressionReason: item.withheldFor,
		})),
		evidenceRationale: result.evidenceRationale,
		evidenceHidden: false,
		invalidations: [],
		disputes: [],
	};
}

function feedbackPlacements(item: FeedbackSpec): ReviewFeedbackDetail["placements"] {
	if (item.outcome !== "DELIVERED" || item.channel !== "IN_CONTEXT") {
		return [];
	}
	if (item.anchoredAt) {
		return [
			{
				id: `${item.id}-inline`,
				placementType: "INLINE",
				anchorKind: "RANGE",
				anchorSide: "NEW",
				anchorPath: item.anchoredAt.path,
				anchorStartLine: item.anchoredAt.startLine,
				anchorEndLine: item.anchoredAt.endLine,
				postedCommentRef: "2481944",
			},
		];
	}
	return [{ id: `${item.id}-summary`, placementType: "SUMMARY", postedCommentRef: "2481933" }];
}

/**
 * The deliveries a piece of feedback on the work went out in: one sent, one being retried after a
 * provider error, one that gave up. Feedback that waits, was withheld or goes elsewhere has none.
 */
function feedbackDispatches(run: RunSpec, item: FeedbackSpec): ReviewFeedbackDetail["dispatches"] {
	if (item.channel !== "IN_CONTEXT") {
		return [];
	}
	const createdAt = new Date(item.composedAt);
	const base = {
		id: `${item.id.slice(0, 8)}-0000-4000-8000-00000000d15c`,
		destination: "AUTOMATIC_REVIEW_PACKAGE" as const,
		createdAt,
	};
	switch (item.outcome) {
		case "DELIVERED":
		case "SUPERSEDED": {
			return [
				{
					...base,
					state: "SENT",
					attemptCount: 1,
					updatedAt: hasText(item.deliveredAt) ? new Date(item.deliveredAt) : createdAt,
					deliveredExternalRef: "2481933",
					deliveredExternalUrl: `${run.work.reviewedWork.url ?? "https://github.com/acme/app/pull/1423"}#issuecomment-2481933`,
				},
			];
		}
		case "PREPARED":
		case "PARTIALLY_DELIVERED":
		case "UNCONFIRMED": {
			return [
				{
					...base,
					state: item.outcome === "UNCONFIRMED" ? "UNCERTAIN" : "PENDING",
					attemptCount: 2,
					updatedAt: new Date(createdAt.getTime() + 2 * 60_000),
					nextAttemptAt: new Date(createdAt.getTime() + 10 * 60_000),
					lastError: "GitHub answered 502 Bad Gateway while the summary comment was posted.",
				},
			];
		}
		case "FAILED":
		case "PARTIALLY_FAILED": {
			return [
				{
					...base,
					state: "FAILED",
					attemptCount: 5,
					updatedAt: new Date(createdAt.getTime() + 40 * 60_000),
					lastError: "GitHub answered 403: Resource not accessible by integration.",
				},
			];
		}
		case "AWAITING_APPROVAL":
		case "DISCARDED":
		case "SUPPRESSED": {
			return [];
		}
	}
}

export function feedbackDetail(feedbackId: string): ReviewFeedbackDetail {
	const found = allFeedbackSpecs.find(({ item }) => item.id === feedbackId);
	if (!found) {
		throw new Error(`No feedback ${feedbackId} in the fixture`);
	}
	const { run, item } = found;
	return {
		id: item.id,
		agentJobId: run.id,
		reviewedWork: run.work.reviewedWork,
		body: operatorBody(item),
		channel: item.channel,
		location: locationOf(run, item),
		createdAt: new Date(item.composedAt),
		deliveredAt: hasText(item.deliveredAt) ? new Date(item.deliveredAt) : undefined,
		deliveryState: item.outcome,
		practiceResults: item.from.map((practiceResultId, ordinal) => {
			const source = resultDetail(practiceResultId);
			return {
				practiceResultId,
				outcome: source.outcome,
				group: source.group,
				claimCurrentness: source.claimCurrentness,
				observedAt: source.observedAt,
				ordinal,
				practiceName: source.practiceName,
				practiceSlug: source.practiceSlug,
				role: ordinal === 0 ? "PRIMARY" : "SUPPORTING",
				severity: source.severity,
				summary: source.summary,
			};
		}),
		placements: feedbackPlacements(item),
		dispatches: feedbackDispatches(run, item),
		proposedPlacements:
			item.outcome === "AWAITING_APPROVAL" && item.channel === "IN_CONTEXT"
				? [
						{ type: "SUMMARY", body: item.body ?? "" },
						{
							type: "INLINE",
							body: "This fallback catches every exception and hides whether the retry is safe. Catch the expected transport error and let programming errors surface.",
							path: "src/main/java/example/RetryService.java",
							startLine: 48,
						},
						{
							type: "INLINE",
							body: "The new branch changes behavior but the description does not explain why. Add the reason so a future maintainer can distinguish intent from accident.",
							path: "src/main/java/example/ReviewHandler.java",
							startLine: 91,
							endLine: 94,
						},
					]
				: [],
		recipient: item.recipient,
		replacesId: item.replaces,
		reviewedRevision: item.outcome === "AWAITING_APPROVAL" ? "27f4e88c9f5a" : undefined,
		subject: run.developer,
		suppressionReason: item.withheldFor,
		deliveryPolicy: [],
		withdrawals: [],
	};
}

/** The withheld piece of feedback the detail stories open by default. */
export const reviewFeedbackDetail: ReviewFeedbackDetail = feedbackDetail(
	"dddddddd-2222-2222-2222-222222222222",
);

/** The long note, drawn from two results. */
export const longFeedbackDetail: ReviewFeedbackDetail = feedbackDetail(
	"44444444-4444-4444-4444-444444444444",
);

/**
 * A not met result with three problems of mixed severity across a diff and a repository file, its own
 * citation from a human review thread, and an example that admin surfaces never render.
 */
export const reviewResultDetail: ReviewResultDetail = resultDetail(
	"66666666-6666-6666-6666-666666666666",
);

// ---------------------------------------------------------------------------------------------
// A page that does not fit on one page
// ---------------------------------------------------------------------------------------------

function cycled<T>(specs: readonly T[], index: number): T {
	const spec = specs[index % specs.length];
	if (spec === undefined) {
		throw new Error("A page cannot be filled from an empty list of specs.");
	}
	return spec;
}

/**
 * The fixture's results restated across as many days as it takes to fill `count` rows. Cycling the
 * real specs keeps every row a record the server could have produced, unlike a generated
 * `Result #17`.
 */
export function manyResults(count: number): ReviewResult[] {
	const base = reviewResults;
	const rows = Array.from({ length: count }, (_, index) => {
		const source = cycled(base, index);
		const cycle = Math.floor(index / base.length);
		if (cycle === 0) {
			return source;
		}
		return {
			...source,
			id: `${source.id.slice(0, -2)}${(10 + cycle).toString(36)}`,
			observedAt: new Date(source.observedAt.getTime() - cycle * 86_400_000),
		};
	});
	// Newest first, as the server sends them, so each day's rows stay together under one heading.
	return rows.sort((a, b) => b.observedAt.getTime() - a.observedAt.getTime());
}

/**
 * A workspace large enough that the person facet, which filters one fetched page in the browser
 * because the endpoint takes no name, cannot reach everybody.
 */
export function manyMembers(count: number): WorkspaceMembership[] {
	return Array.from({ length: count }, (_, index) => {
		const source = cycled(workspaceMembers, index);
		const cycle = Math.floor(index / workspaceMembers.length);
		if (cycle === 0) {
			return source;
		}
		return {
			...source,
			userId: source.userId + cycle * 100,
			userLogin: `${source.userLogin}-${cycle}`,
			userName: source.userName === undefined ? undefined : `${source.userName} ${cycle}`,
		};
	});
}

export function manyFeedback(count: number): ReviewFeedback[] {
	const base = reviewFeedback;
	const rows = Array.from({ length: count }, (_, index) => {
		const source = cycled(base, index);
		const cycle = Math.floor(index / base.length);
		if (cycle === 0) {
			return source;
		}
		return {
			...source,
			id: `${source.id.slice(0, -2)}${(10 + cycle).toString(36)}`,
			createdAt: new Date(source.createdAt.getTime() - cycle * 86_400_000),
		};
	});
	// Newest first, as the server sends them, so each day's rows stay together under one heading.
	return rows.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
}

const pullRequestTarget: AgentJob["target"] = {
	type: "scm.pull_request",
	title: "Make practice review output visible",
	reviewedWork: {
		id: "42",
		kind: "scm.pull_request",
		provider: "GITHUB",
		label: "#1423",
		container: "ls1intum/Hephaestus",
		url: "https://github.com/ls1intum/Hephaestus/pull/1423",
	},
};
const issueTarget: AgentJob["target"] = {
	type: "scm.issue",
	title: "Admin read surface for results and prepared feedback",
	reviewedWork: {
		id: "43",
		kind: "scm.issue",
		provider: "GITHUB",
		label: "#1420",
		container: "ls1intum/Hephaestus",
	},
};

export const mockJobCompleted: AgentJob = {
	id: "job-completed-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "COMPLETED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T10:00:00Z"),
	availableAt: new Date("2026-05-20T10:00:00Z"),
	completedAt: new Date("2026-05-20T10:05:00Z"),
	deliveryStatus: "DELIVERED",
	llmModel: "openai/gpt-oss-120b",
	llmTotalInputTokens: 24_000,
	llmTotalOutputTokens: 914,
	llmTotalReasoningTokens: 120,
	llmTotalCalls: 7,
	retryCount: 0,
	exitCode: 0,
	traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
};

/** A review the server stopped before it started, because its budget was used up. */
export const mockJobCancelledOnBudget: AgentJob = {
	id: "job-cancelled-budget-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "CANCELLED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T07:00:00Z"),
	availableAt: new Date("2026-05-20T07:00:00Z"),
	completedAt: new Date("2026-05-20T07:00:04Z"),
	cancellationReason: "BUDGET_EXHAUSTED",
	retryCount: 0,
	traceId: "0af7651916cd43dd8448eb211c80319c",
};

/** What a review spent on the model: a first attempt that failed and the retry that finished. */
export const mockJobUsageTwoAttempts: AgentJobUsage = {
	attempts: [
		{
			attempt: 0,
			occurredAt: new Date("2026-05-20T10:02:00Z"),
			model: "openai/gpt-oss-120b",
			pricingState: "PRICED",
			fundingSource: "INSTANCE",
			costUsd: 0.0125,
			totalCalls: 3,
			inputTokens: 11_000,
			outputTokens: 300,
			reasoningTokens: 40,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
		},
		{
			attempt: 1,
			occurredAt: new Date("2026-05-20T10:05:00Z"),
			model: "openai/gpt-oss-120b",
			pricingState: "PRICED",
			fundingSource: "INSTANCE",
			costUsd: 0.03,
			totalCalls: 4,
			inputTokens: 13_000,
			outputTokens: 614,
			reasoningTokens: 80,
			cacheReadTokens: 2000,
			cacheWriteTokens: 0,
		},
	],
	totalCostUsd: 0.0425,
	unpricedAttempts: 0,
};

/** One attempt on a model the workspace set up without a price. */
export const mockJobUsageUnpriced: AgentJobUsage = {
	attempts: [
		{
			attempt: 0,
			occurredAt: new Date("2026-05-20T10:05:00Z"),
			model: "acme/house-model",
			pricingState: "UNPRICED",
			fundingSource: "WORKSPACE",
			totalCalls: 7,
			inputTokens: 24_000,
			outputTokens: 914,
			reasoningTokens: 120,
			cacheReadTokens: 0,
			cacheWriteTokens: 0,
		},
	],
	unpricedAttempts: 1,
};

export const mockJobRunning: AgentJob = {
	id: "job-running-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "RUNNING",
	model: "openai/gpt-oss-120b",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T11:58:00Z"),
	availableAt: new Date("2026-05-20T11:58:00Z"),
	// Running means it left the queue: a running review without a start would contradict itself.
	startedAt: new Date("2026-05-20T11:58:20Z"),
	executionStartedAt: new Date("2026-05-20T11:59:00Z"),
	retryCount: 0,
};

export const mockJobFailedResultProcessing: AgentJob = {
	id: "job-failed-delivery-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "COMPLETED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T09:00:00Z"),
	availableAt: new Date("2026-05-20T09:02:00Z"),
	completedAt: new Date("2026-05-20T09:05:00Z"),
	deliveryStatus: "FAILED",
	errorMessage: "Review result processing failed.",
	llmModel: "openai/gpt-oss-120b",
	llmTotalInputTokens: 31_000,
	llmTotalOutputTokens: 1200,
	retryCount: 1,
	exitCode: 0,
};

export const mockJobQueued: AgentJob = {
	id: "job-queued-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "QUEUED",
	model: "gpt-5.4-mini",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T12:00:00Z"),
	availableAt: new Date("2026-05-20T12:00:00Z"),
	retryCount: 0,
};

/**
 * A run waiting on the clock anchors `availableAt` to the story clock, not to the otherwise fixed
 * scene date: an `availableAt` in the past renders the "due …" phrase as history.
 */
export const mockJobHeldOnBudget: AgentJob = {
	id: "job-held-budget-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "QUEUED",
	model: "gpt-5.4-nano",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T12:01:00Z"),
	availableAt: minutesAfter(5),
	holdReason: "BUDGET",
	retryCount: 0,
};

/** A reason this client has never heard of, which still has to read as English. */
export const mockJobHeldForUnknownReason: AgentJob = {
	id: "job-held-unknown-1",
	jobType: "ISSUE_REVIEW",
	reviewOutcome: "REVIEWED",
	target: issueTarget,
	status: "QUEUED",
	model: "gpt-5.4-nano",
	configSnapshot: { name: "Default reviewer" },
	createdAt: new Date("2026-05-20T12:02:00Z"),
	availableAt: minutesAfter(9),
	holdReason: "MODEL_UNAVAILABLE",
	retryCount: 0,
};

export const mockJobBackingOff: AgentJob = {
	id: "job-backoff-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "QUEUED",
	model: "openai/gpt-oss-20b",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T11:00:00Z"),
	availableAt: minutesAfter(3),
	errorMessage: "Runner exited with code 137 (out of memory).",
	retryCount: 2,
};

export const mockJobTimedOut: AgentJob = {
	id: "job-timed-out-1",
	jobType: "PULL_REQUEST_REVIEW",
	reviewOutcome: "REVIEWED",
	target: pullRequestTarget,
	status: "TIMED_OUT",
	model: "openai/gpt-oss-120b",
	configSnapshot: { name: "GPU gateway (OpenAI)", llmProvider: "OPENAI" },
	createdAt: new Date("2026-05-20T08:00:00Z"),
	availableAt: new Date("2026-05-20T08:03:00Z"),
	completedAt: new Date("2026-05-20T08:20:00Z"),
	errorMessage: "Agent exceeded the 1200s timeout and was stopped.",
	llmModel: "gpt-oss-120b",
	retryCount: 1,
	exitCode: 124,
};

export const mockJobs: AgentJob[] = [
	mockJobCompleted,
	mockJobRunning,
	mockJobQueued,
	mockJobHeldOnBudget,
	mockJobFailedResultProcessing,
	mockJobTimedOut,
];

/**
 * A story applies the server's facet filtering over its pool of rows; an unset or empty facet
 * selects every row.
 */
export function selects(selected: string[] | undefined, actual: string | undefined): boolean {
	return (
		selected === undefined ||
		selected.length === 0 ||
		(actual !== undefined && selected.includes(actual))
	);
}

// ---------------------------------------------------------------------------------------------
// The overview: thirty days of reviews, counted
// ---------------------------------------------------------------------------------------------

const ZERO_FEEDBACK = feedbackCounts([]);

const resultCounts = (
	met: number,
	notMet: number,
	notApplicable: number,
	undetermined: number,
): ReviewResultCounts => ({ met, notMet, notApplicable, undetermined });

const countsOf = (
	practiceSlug: string,
	practiceResults: ReviewResultCounts,
	feedback: Partial<ReviewFeedbackCounts>,
	practiceResultsInvalidated = 0,
): PracticeReviewCounts => {
	const practice = workspacePractices.find((entry) => entry.slug === practiceSlug);
	const practiceGroup = practiceGroups.find((entry) => entry.slug === practice?.groupSlug);
	if (!practice || !practiceGroup) {
		throw new Error(`No grouped practice named ${practiceSlug} in the fixture catalogue`);
	}
	return {
		practiceSlug,
		practiceName: practice.name,
		group: { slug: practiceGroup.slug, name: practiceGroup.name },
		practiceResults,
		feedback: { ...ZERO_FEEDBACK, ...feedback },
		practiceResultsInvalidated,
	};
};

/**
 * Skewed the way a real workspace is: one practice carries most of the volume and most of the
 * strengths, one is mostly problems, one fires twice and says nothing useful, and "Decisions are
 * written down" is not checked at all — so the busiest-first ranking and the not-checked level both
 * have something to show.
 */
export const practiceCounts: PracticeReviewCounts[] = [
	countsOf(
		"thin-controllers",
		resultCounts(18, 9, 4, 2),
		{ delivered: 7, awaitingApproval: 2, suppressed: 2, failed: 1 },
		1,
	),
	countsOf(
		"errors-carry-context",
		resultCounts(6, 11, 1, 3),
		{ delivered: 6, prepared: 1, discarded: 1, superseded: 1 },
		2,
	),
	countsOf("tests-name-the-behaviour", resultCounts(9, 2, 3, 0), { delivered: 2 }),
	countsOf("the-change-explains-itself", resultCounts(2, 4, 1, 1), {
		delivered: 1,
		awaitingApproval: 1,
		suppressed: 1,
	}),
	countsOf("product-language", resultCounts(0, 1, 1, 0), {}),
];

const OVERVIEW_DAYS = REVIEW_RANGE_DEFS["30d"].days;
const OVERVIEW_FROM = reviewRangeStart(STORY_NOW, "30d");

/**
 * Day by day, oldest first, with quiet weekends and a busy stretch so the bars look like a team's.
 * Each row adds up to its stage's total below, as the server's buckets do.
 */
const PER_DAY = {
	reviews: [
		0, 2, 0, 3, 2, 0, 0, 3, 0, 2, 1, 3, 0, 0, 2, 3, 0, 1, 4, 0, 2, 0, 0, 1, 3, 1, 0, 3, 0, 2,
	],
	practiceResults: [
		0, 5, 3, 0, 0, 6, 1, 4, 3, 7, 0, 0, 4, 5, 0, 2, 8, 1, 4, 0, 0, 2, 6, 2, 1, 5, 0, 4, 1, 4,
	],
	feedback: [
		0, 1, 0, 2, 1, 0, 0, 2, 1, 1, 1, 2, 0, 0, 1, 2, 0, 1, 2, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1,
	],
} as const;

function overviewBuckets(perDay: Record<keyof typeof PER_DAY, readonly number[]>) {
	return Array.from({ length: OVERVIEW_DAYS }, (_, day): PracticeReviewBucket => ({
		start: addDays(OVERVIEW_FROM, day),
		reviews: perDay.reviews[day] ?? 0,
		practiceResults: perDay.practiceResults[day] ?? 0,
		feedback: perDay.feedback[day] ?? 0,
	}));
}

const NOTHING_PER_DAY = Array.from({ length: OVERVIEW_DAYS }, () => 0);

/** Thirty days of a busy workspace, day by day, as `GET …/practices/reviews/overview` returns it. */
export const practiceReviewOverview: PracticeReviewOverview = {
	from: OVERVIEW_FROM,
	to: new Date(STORY_NOW),
	bucket: "DAY",
	buckets: overviewBuckets(PER_DAY),
	reviews: { completed: 31, failed: 2, timedOut: 1, cancelled: 1, running: 1, queued: 2 },
	// Every result is of one practice, so the practices' counts add up to these.
	practiceResults: resultCounts(35, 27, 10, 6),
	practiceResultsInvalidated: 3,
	// One piece of feedback citing two practices counts for each, so these are not the practices' sum.
	feedback: {
		...ZERO_FEEDBACK,
		delivered: 14,
		awaitingApproval: 3,
		suppressed: 3,
		failed: 1,
		prepared: 1,
		discarded: 1,
		superseded: 1,
	},
	practices: practiceCounts,
};

/**
 * The overview's 14 delivered pieces by channel, as the counts by location return them for the
 * delivered states: on GitHub, on the developers' practice pages, and with Heph in Slack.
 */
export const deliveredByChannelCounts: ReviewFeedbackLocationCounts[] = [
	{ location: "GITHUB", feedback: { ...ZERO_FEEDBACK, delivered: 7, partiallyDelivered: 1 } },
	{ location: "SLACK", feedback: { ...ZERO_FEEDBACK, delivered: 2 } },
	{ location: "PRACTICE_PAGES", feedback: { ...ZERO_FEEDBACK, delivered: 4 } },
];

/** The overview's 3 withheld pieces by reason: 2 as the work was merged, 1 over the limit. */
export const withheldByReasonCounts: ReviewFeedbackReasonCount[] = [
	{ reason: "VOLUME_CAPPED", count: 1 },
	{ reason: "ARTIFACT_MERGED", count: 2 },
];

/** Thirty days in which nothing was reviewed: every bucket present, and every one of them zero. */
export const quietPracticeReviewOverview: PracticeReviewOverview = {
	...practiceReviewOverview,
	buckets: overviewBuckets({
		reviews: NOTHING_PER_DAY,
		practiceResults: NOTHING_PER_DAY,
		feedback: NOTHING_PER_DAY,
	}),
	reviews: { completed: 0, failed: 0, timedOut: 0, cancelled: 0, running: 0, queued: 0 },
	practiceResults: resultCounts(0, 0, 0, 0),
	practiceResultsInvalidated: 0,
	feedback: ZERO_FEEDBACK,
	practices: [],
};

/** The overview once it is in and current. */
export function readyReviewOverview(
	overview: PracticeReviewOverview = practiceReviewOverview,
): OverviewRegionState {
	return { status: "ready", overview, stale: false };
}

/** The same thirty days as the lists' day filters, so a count's link can be read in a story. */
export const reviewOverviewScope: OutcomeScope = {
	from: toDayParam(OVERVIEW_FROM),
	to: toDayParam(new Date(STORY_NOW)),
};

// ---------------------------------------------------------------------------------------------
// Where the time of the overview's reviews went
// ---------------------------------------------------------------------------------------------

/**
 * The thirty days' reviews as the local data had them: a typical review took 9 min 20 s from the
 * request to its comment, almost all of it with the model, and the slowest 1 in 10 took 19 min or
 * longer. Nine reviews started before the merge and
 * finished after it.
 */
export const practiceReviewTiming: PracticeReviewTiming = {
	from: OVERVIEW_FROM,
	to: new Date(STORY_NOW),
	asked: { count: 40, medianSeconds: 120, p90Seconds: 600 },
	queue: { count: 258, medianSeconds: 26, p90Seconds: 60 },
	preparation: { count: 51, medianSeconds: 20, p90Seconds: 40 },
	model: { count: 51, medianSeconds: 700, p90Seconds: 1080 },
	posted: { count: 14, medianSeconds: 12, p90Seconds: 30 },
	// Until asked 2 min, Queue 26 s, Run 12 min, To developer 12 s: they add up to the median.
	total: { count: 14, medianSeconds: 878, p90Seconds: 1500 },
	totalShares: {
		asked: 120 / 878,
		queue: 26 / 878,
		preparation: 20 / 878,
		model: 700 / 878,
		runUnsplit: 0,
		posted: 12 / 878,
	},
	finished: { count: 51, medianSeconds: 866, p90Seconds: 1490 },
	finishedShares: {
		asked: 120 / 866,
		queue: 26 / 866,
		preparation: 20 / 866,
		model: 700 / 866,
		runUnsplit: 0,
	},
	read: { count: 11, medianSeconds: 67_020, p90Seconds: 90_000 },
	waitingLong: 0,
	finishedAfterMerge: 9,
	finishedAfterMergeStartedByMerge: 0,
	feedbackWithheldAsMerged: 0,
};

// ---------------------------------------------------------------------------------------------
// The queue right now
// ---------------------------------------------------------------------------------------------

const queueItem = (
	id: string,
	state: "running" | "waiting" | "held",
	work: ReviewWork,
	developer: ReviewSubject,
	minutes: number,
): PracticeReviewQueueItem => ({
	id,
	status: state === "running" ? "RUNNING" : "QUEUED",
	heldForBudget: state === "held",
	target: work,
	developer,
	since: minutesBefore(minutes),
});

/** The busy queue's head, by name, for a story to rearrange. */
export const reviewQueueHead = {
	runningLong: queueItem("queue-run-1", "running", reviewArtifact, ada, 3),
	runningShort: queueItem("queue-run-2", "running", gitlabMergeRequest, grace, 1),
	waitingLong: queueItem("queue-wait-1", "waiting", webhookRetryPullRequest, alan, 4),
	waitingShort: queueItem("queue-wait-2", "waiting", leagueColumnsPullRequest, katherine, 2),
	waitingNew: queueItem("queue-wait-3", "waiting", invoiceBackfillMergeRequest, barbara, 1),
};

/** Nothing waits or runs; twelve reviews finished in the last hour. */
export const idleReviewQueue: PracticeReviewQueue = {
	asOf: new Date(STORY_NOW),
	running: 0,
	activeWorkers: 0,
	waiting: 0,
	heldForBudget: 0,
	head: [],
	finishedLastHour: 12,
	finishedLastDay: 96,
	finishedPerHourLastDay: 4,
};

/** Two running on two workers, five waiting, the oldest for four minutes: busy, not stuck. */
export const busyReviewQueue: PracticeReviewQueue = {
	...idleReviewQueue,
	running: 2,
	activeWorkers: 2,
	waiting: 5,
	oldestWaitingSince: minutesBefore(4),
	head: Object.values(reviewQueueHead),
};

// ---------------------------------------------------------------------------------------------
// What the overview says needs an admin// ---------------------------------------------------------------------------------------------
// What the overview says needs an admin
// ---------------------------------------------------------------------------------------------

/**
 * Feedback held for a decision. Restated from delivered and withheld rows rather than written
 * afresh, so each one still names a real review, work and developer.
 */
export const awaitingApprovalFeedback: ReviewFeedback[] = reviewFeedback
	.filter((item) => item.deliveryState === "DELIVERED" || item.deliveryState === "SUPPRESSED")
	.slice(0, 3)
	.map((item) => ({
		...item,
		deliveryState: "AWAITING_APPROVAL",
		deliveredAt: undefined,
		suppressionReason: undefined,
	}));

/**
 * The trace of the work a review fixture looked at, told so it agrees with that review: the moment
 * that started it happened two minutes before it was asked for, the other moments before that, and
 * the practices it answered name it. A trace taken from another review would put the moment that
 * started this one after it finished.
 */
export function reviewTraceOf(job: AgentJob): ArtifactTrace {
	const asked = job.createdAt.getTime();
	const starting = artifactTrace.signals.find((signal) => signal.reviewId === LINEAGE_TRACE_REVIEW);
	return {
		...artifactTrace,
		signals: artifactTrace.signals.map((signal, index) =>
			signal === starting
				? { ...signal, reviewId: job.id, occurredAt: new Date(asked - 2 * 60_000) }
				: {
						...signal,
						reviewId: signal.reviewId === undefined ? undefined : `earlier-${index}`,
						occurredAt: new Date(asked - (index + 1) * 3_600_000),
					},
		),
		practices: artifactTrace.practices.map((entry) =>
			entry.reviewId === LINEAGE_TRACE_REVIEW ? { ...entry, reviewId: job.id } : entry,
		),
	};
}

/** The review whose answers the shared work trace fixture lists. */
const LINEAGE_TRACE_REVIEW = "11111111-1111-1111-1111-111111111111";

/** A check the review ran for itself, which its sheet lists and a piece of feedback does not. */
export function reviewDeliveryChecksOf(job: AgentJob): DeliveryPolicyTrace[] {
	return [
		{
			...deniedDeliveryPolicyEvaluation,
			reviewId: job.id,
			evaluatedAt: new Date((job.completedAt ?? job.createdAt).getTime() + 30_000),
		},
	];
}

// ---------------------------------------------------------------------------------------------
// What the provider reported about a piece of work, and a practice's versions
// ---------------------------------------------------------------------------------------------

/** A merged pull request whose head failed its checks. */
export const mergedPullRequestFacts: ReviewWorkFacts = {
	artifactKind: "scm.pull_request",
	artifactId: 1423,
	author: { id: 7, login: "jdoe", name: "Jane Doe" },
	state: "MERGED",
	draft: false,
	sourceBranch: "feature/retry-service",
	targetBranch: "main",
	headRevision: "27f4e88c9f5a1b2c3d4e5f60718293a4b5c6d7e8",
	createdAt: new Date("2026-08-05T09:12:00Z"),
	updatedAt: new Date("2026-08-07T10:05:00Z"),
	mergedAt: new Date("2026-08-07T10:02:00Z"),
	closedAt: new Date("2026-08-07T10:02:00Z"),
	additions: 120,
	deletions: 30,
	changedFiles: 7,
	commits: 3,
	checkState: "FAILURE",
	checkObservedAt: new Date("2026-08-07T09:58:00Z"),
	lastSyncAt: new Date("2026-08-07T10:06:00Z"),
};

/** An issue still open, with no branches, size or checks. */
export const openIssueFacts: ReviewWorkFacts = {
	artifactKind: "scm.issue",
	artifactId: 1430,
	author: { id: 8, login: "asmith", name: "Alex Smith" },
	state: "OPEN",
	createdAt: new Date("2026-08-03T14:00:00Z"),
	updatedAt: new Date("2026-08-06T08:30:00Z"),
	lastSyncAt: new Date("2026-08-06T08:31:00Z"),
};

/** Three versions of a practice: the first, a wording change, and a change to what it checks. */
export const practiceRevisions: PracticeRevision[] = [
	{
		id: 303,
		revisionNumber: 3,
		createdAt: new Date("2026-08-02T09:00:00Z"),
		changed: ["CRITERIA", "EVIDENCE_REQUIREMENTS"],
		reviewRulesChanged: true,
	},
	{
		id: 302,
		revisionNumber: 2,
		createdAt: new Date("2026-07-20T09:00:00Z"),
		changed: ["WHY_IT_MATTERS"],
		reviewRulesChanged: false,
	},
	{
		id: 301,
		revisionNumber: 1,
		createdAt: new Date("2026-07-01T09:00:00Z"),
		changed: [],
		reviewRulesChanged: false,
	},
];

/** The card on a developer's own practice pages, whose text the fixture writes and no surface may show. */
export const PRIVATE_FEEDBACK_TEXT =
	"Private to Barbara: the runbook's first step is the one a tired reader most needs.";

// ---------------------------------------------------------------------------------------------
// How each practice's program ran in one review: one entry per way the section words it.

const decision = { purpose: "PRACTICE_DECISION", need: "REQUIRED", bound: true } as const;
const custom = { programKind: "CUSTOM" } as const;

export const reviewPracticeScripts = {
	fromCode: {
		practiceSlug: "thin-controllers",
		practiceName: "Thin controllers",
		run: { ...custom, status: "OK", path: "CODE", models: [{ ...decision, notRated: [] }] },
		durationMs: 4200,
		models: [
			{
				purpose: "PRACTICE_DECISION",
				tier: "CLOUD",
				calls: 12,
			},
		],
	},
	callsNotRated: {
		practiceSlug: "errors-carry-context",
		practiceName: "Errors carry their context",
		durationMs: 12_840,
		run: {
			...custom,
			status: "OK",
			path: "MODELS",
			models: [
				{
					...decision,
					notRated: [
						{ reason: "DEADLINE", count: 5 },
						{ reason: "OFF_FORMAT", count: 1 },
					],
				},
				{ purpose: "PRACTICE_EMBEDDING", need: "OPTIONAL", bound: true, notRated: [] },
			],
		},
		models: [
			{
				purpose: "PRACTICE_DECISION",
				tier: "IN_HOUSE",
				calls: 9,
			},
			{
				purpose: "PRACTICE_EMBEDDING",
				tier: "IN_HOUSE",
				calls: 2,
			},
		],
	},
	limitReached: {
		practiceSlug: "small-pull-requests",
		practiceName: "Small pull requests",
		durationMs: 2100,
		run: {
			...custom,
			status: "OK",
			path: "MIXED",
			models: [{ ...decision, notRated: [{ reason: "BUDGET", count: 3 }] }],
		},
		models: [
			{
				purpose: "PRACTICE_DECISION",
				tier: "CLOUD",
				calls: 4,
			},
		],
	},
	// A practice without a script of its own gets the built-in review, which declares no models.
	builtIn: {
		practiceSlug: "the-change-explains-itself",
		practiceName: "The change explains itself",
		run: { programKind: "BUILTIN", status: "OK", path: "REVIEW", models: [] },
		durationMs: 900,
		models: [],
	},
	skipped: {
		practiceSlug: "product-language",
		practiceName: "Product language",
		run: { ...custom, status: "SKIPPED", models: [{ ...decision, bound: false, notRated: [] }] },
		models: [{ purpose: "PRACTICE_DECISION", calls: 0 }],
	},
	failed: {
		practiceSlug: "tests-name-the-behaviour",
		practiceName: "Tests name the behaviour",
		run: { ...custom, status: "FAILED", stopReason: "ERROR", models: [] },
		durationMs: 40,
		models: [],
		error: "TypeError: change.files is not iterable at run (tests-name-the-behaviour.ts:14:23)",
	},
	// The program stopped before it reported, so the run names no models and their tiers are
	// unknown, while the proxy still counted the calls it forwarded.
	notFinished: {
		practiceSlug: "decisions-are-written-down",
		practiceName: "Decisions are written down",
		run: { ...custom, status: "NOT_FINISHED", models: [] },
		models: [{ purpose: "PRACTICE_DECISION", calls: 9 }],
	},
	// The review ended before this program's turn came, so it asked nothing and took no time.
	notReached: {
		practiceSlug: "security-review",
		practiceName: "Security review",
		run: { ...custom, status: "NOT_REACHED", models: [] },
		models: [],
	},
	removedPractice: {
		practiceSlug: "retired-practice",
		run: { ...custom, status: "FAILED", stopReason: "DEADLINE", models: [] },
		durationMs: 15_000,
		models: [],
	},
} satisfies Record<string, ReviewPracticeScripts>;
