import type {
	AgentJob,
	EvidenceCitation,
	Practice,
	ReviewArtifact,
	ReviewFeedback,
	ReviewFeedbackDetail,
	ReviewObservation,
	ReviewObservationDetail,
	ReviewRunSummary,
	ReviewSubject,
	ReviewTierAssignment,
	WorkspaceMembership,
} from "@/api/types.gen";

/**
 * One workspace's worth of review output, written as a spec and derived into the wire shapes.
 *
 * The old fixture wrote every record out by hand, and the counts in them drifted from the records
 * they counted: an observation claimed one delivered piece of feedback while the only feedback
 * linked to it was withheld, and a run's tally was a third hand-kept list. A story asserting against
 * that is asserting against a state the server cannot produce.
 *
 * So the only thing written here is {@link REVIEW_FIXTURE} — seven reviews, each with its
 * observations and the feedback they drove. Every count, every preview, every truncation flag and
 * every summary is computed from it by the derivations below, which follow the server's own rules.
 * An inconsistent fixture is not something you can write; it is something that stops compiling.
 */

// ---------------------------------------------------------------------------------------------
// People
// ---------------------------------------------------------------------------------------------

const ada: ReviewSubject = { id: 7, login: "ada", name: "Ada Lovelace" };
const grace: ReviewSubject = { id: 9, login: "grace", name: "Grace Hopper" };
const alan: ReviewSubject = { id: 11, login: "alan", name: "Alan Turing" };
const katherine: ReviewSubject = { id: 14, login: "katherine", name: "Katherine Johnson" };
const barbara: ReviewSubject = { id: 18, login: "barbara", name: "Barbara Liskov" };

export const workspaceMembers: WorkspaceMembership[] = [
	{ userId: ada.id, userLogin: ada.login, userName: ada.name, role: "MEMBER" },
	{ userId: grace.id, userLogin: grace.login, userName: grace.name, role: "ADMIN" },
	{ userId: alan.id, userLogin: alan.login, userName: alan.name, role: "MEMBER" },
	{ userId: katherine.id, userLogin: katherine.login, userName: katherine.name, role: "MEMBER" },
	{ userId: barbara.id, userLogin: barbara.login, userName: barbara.name, role: "MEMBER" },
];

// ---------------------------------------------------------------------------------------------
// The work under review
// ---------------------------------------------------------------------------------------------

/**
 * The work under review, on the providers it really comes from.
 *
 * Every list row and every detail header draws its glyph from the provider and its words from the
 * kind, so a fixture of seven GitHub pull requests would let a wrong mark or a missing label ship.
 * Five of the seven reviewed here are pull or merge requests because that is the shape of a real
 * workspace — the point is that the thread and the document sit beside them and read as the same
 * language. The eighth, {@link trackerIssue}, is reviewed by nothing and exists only so the fourth
 * kind is rendered somewhere; this comment claimed four kinds for a whole branch while shipping
 * three.
 */
export const reviewArtifact: ReviewArtifact = {
	id: 42,
	type: "scm.pull_request",
	provider: "GITHUB",
	number: 1423,
	repositoryName: "ls1intum/Hephaestus",
	title: "Cache the workspace member lookup on the review path",
	url: "https://github.com/ls1intum/Hephaestus/pull/1423",
};

export const gitlabMergeRequest: ReviewArtifact = {
	id: 43,
	type: "scm.pull_request",
	provider: "GITLAB",
	number: 88,
	repositoryName: "platform/billing-service",
	title: "Move invoice numbering behind the billing boundary",
	url: "https://gitlab.example.com/platform/billing-service/-/merge_requests/88",
};

export const slackConversation: ReviewArtifact = {
	id: 81,
	type: "chat.conversation_thread",
	provider: "SLACK",
	channelName: "engineering",
	title: "How should we roll back the pricing migration?",
	url: "https://example.slack.com/archives/C01/p1721400000",
};

export const outlineDocument: ReviewArtifact = {
	id: 96,
	type: "docs.document",
	provider: "OUTLINE",
	title: "Runbook: restoring a workspace from backup",
	url: "https://docs.example.com/doc/runbook-restore",
};

/**
 * An issue, which no review in the fixture reaches but every list row can be asked to name.
 *
 * `scm.issue` is one of the four kinds this build has copy and a glyph for, and it was the only one
 * no story rendered — so `Issue #204` and its `CircleDotIcon` shipped a whole branch without once
 * being looked at. It is a fixture rather than a seventh review because an issue review is not
 * something this workspace's practices do yet; the label still has to be right the day it is.
 */
export const trackerIssue: ReviewArtifact = {
	id: 204,
	type: "scm.issue",
	provider: "GITHUB",
	number: 204,
	repositoryName: "ls1intum/Hephaestus",
	title: "Reviews of documents do not record which revision they read",
	url: "https://github.com/ls1intum/Hephaestus/issues/204",
};

const webhookRetryPullRequest: ReviewArtifact = {
	id: 44,
	type: "scm.pull_request",
	provider: "GITHUB",
	number: 1431,
	repositoryName: "ls1intum/Hephaestus",
	title: "Retry webhook deliveries with backoff instead of dropping them",
	url: "https://github.com/ls1intum/Hephaestus/pull/1431",
};

const leagueColumnsPullRequest: ReviewArtifact = {
	id: 45,
	type: "scm.pull_request",
	provider: "GITHUB",
	number: 1436,
	repositoryName: "ls1intum/Hephaestus",
	title: "Drop the unused league columns",
	url: "https://github.com/ls1intum/Hephaestus/pull/1436",
};

const invoiceBackfillMergeRequest: ReviewArtifact = {
	id: 46,
	type: "scm.pull_request",
	provider: "GITLAB",
	number: 91,
	repositoryName: "platform/billing-service",
	title: "Backfill the invoice sequence table",
	url: "https://gitlab.example.com/platform/billing-service/-/merge_requests/91",
};

// ---------------------------------------------------------------------------------------------
// The catalogue
// ---------------------------------------------------------------------------------------------

const areaNames = {
	"code-quality": "Code quality",
	architecture: "Architecture",
	testing: "Testing",
	documentation: "Documentation",
	collaboration: "Collaboration",
} as const;

type AreaSlug = keyof typeof areaNames;

const area = (slug: AreaSlug) => ({ slug, name: areaNames[slug] });

export const practiceAreas = (Object.keys(areaNames) as AreaSlug[]).map((slug, index) => ({
	id: index + 1,
	slug,
	name: areaNames[slug],
	active: true,
	displayOrder: index,
	createdAt: new Date("2026-01-01T00:00:00Z"),
}));

/**
 * A practice as the catalogue holds it, with only the fields a fixture varies spelled out.
 *
 * The wire type carries a dozen governance fields that no review surface reads; repeating them in
 * every fixture would bury the three that matter here. `whyItMatters` and `whatGoodLooksLike` are the
 * prose `PracticeDetailHoverCard` shows, and a fixture without them renders the bare link — the story
 * would pass while proving nothing.
 */
function practiceFixture(
	practice: Pick<
		Practice,
		"id" | "slug" | "name" | "areaSlug" | "criteria" | "whyItMatters" | "whatGoodLooksLike"
	> & { displayOrder: number; tier: ReviewTierAssignment["effective"] },
): Practice {
	const { tier, ...rest } = practice;
	return {
		...rest,
		artifactKind: "scm.pull_request",
		createdAt: new Date("2026-01-01T00:00:00Z"),
		updatedAt: new Date("2026-06-01T00:00:00Z"),
		bindings: [],
		reviewTier: { effective: tier, inherited: false, source: "PRACTICE" },
		automatedReviewPolicy: {
			automatedReview: {
				evidenceSufficiency: "SUFFICIENT_WHEN_REQUIREMENTS_MET",
				mode: "LANGUAGE_MODEL",
			},
			knownLimitations: [],
			sourceContractVersion: "1.0.0",
			whenEvidenceIsInsufficient: "SKIP_AUTOMATED_REVIEW",
		},
		automatedReviewValidation: {
			policyDigest: "sha256:0",
			reviewRuleFingerprint: "rule:1",
			sourceContractVersion: "1.0.0",
			status: "AUTHOR_DECLARED",
		},
	};
}

export const workspacePractices: Practice[] = [
	practiceFixture({
		id: 1,
		slug: "thin-controllers",
		name: "Thin controllers",
		areaSlug: "code-quality",
		criteria: "A controller validates its input, delegates, and maps the result. Nothing else.",
		whyItMatters:
			"Logic that lives in a controller can only be tested through HTTP, so it tends not to be tested at all.",
		whatGoodLooksLike:
			"The controller method reads as a list of three steps and names a service that does the work.",
		displayOrder: 0,
		tier: "PROPOSE",
	}),
	practiceFixture({
		id: 2,
		slug: "product-language",
		name: "Product language",
		areaSlug: "architecture",
		criteria: "Names in the code are the names the people using it would use.",
		whyItMatters:
			"A boundary named after its storage stops matching the product the day the storage changes, and every reader after that has to translate.",
		whatGoodLooksLike:
			"A route, a class or a column could be read aloud in a planning meeting without explanation.",
		displayOrder: 1,
		tier: "DELIVER",
	}),
	practiceFixture({
		id: 3,
		slug: "errors-carry-context",
		name: "Errors carry their context",
		areaSlug: "code-quality",
		criteria:
			"A failure reports which operation failed and on what, not only that something went wrong.",
		whyItMatters:
			"An error that says only what went wrong sends the next person to the logs to work out where, and at three in the morning that is the whole incident.",
		whatGoodLooksLike:
			"The message names the thing being acted on and the action attempted, and two different causes do not share one status code.",
		displayOrder: 2,
		tier: "DELIVER",
	}),
	practiceFixture({
		id: 4,
		slug: "tests-name-the-behaviour",
		name: "Tests name the behaviour",
		areaSlug: "testing",
		criteria: "A test's name states the behaviour it pins down, not the method it calls.",
		whyItMatters:
			"A red build should say what broke before anyone opens the file; a name like testCache2 makes the reader run the test to find out.",
		whatGoodLooksLike: "The failure line of a broken build reads as a sentence about the system.",
		displayOrder: 3,
		tier: "PROPOSE",
	}),
	practiceFixture({
		id: 5,
		slug: "the-change-explains-itself",
		name: "The change explains itself",
		areaSlug: "documentation",
		criteria: "The description says why the change was made, not only what it does.",
		whyItMatters:
			"Six months later the diff still says what changed. Nothing else records why, so the next person reverses the decision without knowing there was one.",
		whatGoodLooksLike:
			"A reviewer who was not in the room can tell what problem the change is answering.",
		displayOrder: 4,
		tier: "PROPOSE",
	}),
	practiceFixture({
		id: 6,
		slug: "decisions-are-written-down",
		name: "Decisions are written down",
		areaSlug: "collaboration",
		criteria:
			"A decision reached in a discussion ends up somewhere durable before the thread ends.",
		whyItMatters:
			"A choice that only exists in a chat thread is re-argued every time somebody new joins, and nobody can tell a decision from an opinion.",
		whatGoodLooksLike:
			"The thread ends with what was chosen, who chose it, and a link to where it now lives.",
		displayOrder: 5,
		tier: "DELIVER",
	}),
];

// ---------------------------------------------------------------------------------------------
// The spec
// ---------------------------------------------------------------------------------------------

interface FeedbackSpec {
	id: string;
	/**
	 * The composed body exactly as the server stores it: Markdown, headings and all. The preview the
	 * list shows and the truncation flag beside it are derived from this, never written by hand.
	 */
	body?: string;
	channel: ReviewFeedback["channel"];
	outcome: ReviewFeedback["deliveryState"];
	withheldFor?: ReviewFeedback["suppressionReason"];
	recipient: ReviewSubject;
	composedAt: string;
	deliveredAt?: string;
	/** The feedback this one took the place of. */
	replaces?: string;
	/** Where an inline note landed. Absent means it was posted as a summary on the work. */
	anchoredAt?: { path: string; startLine: number; endLine: number };
	/** Observations it draws on, in render order; the first leads it. */
	from: string[];
}

interface ObservationSpec {
	id: string;
	title: string;
	reasoning: string;
	practiceSlug: string;
	area: AreaSlug;
	presence: ReviewObservation["presence"];
	assessment?: ReviewObservation["assessment"];
	severity?: ReviewObservation["severity"];
	confidence: number;
	claimCurrentness?: ReviewObservation["claimCurrentness"];
	observedAt: string;
	evidence?: EvidenceCitation[];
}

interface RunSpec {
	id: string;
	work: ReviewArtifact;
	status: ReviewRunSummary["status"];
	startedAt: string;
	/** Whose work this review was about; every observation and every recipient inherits it. */
	developer: ReviewSubject;
	origin?: ReviewObservation["origin"];
	observations: ObservationSpec[];
	feedback: FeedbackSpec[];
}

const diff = (
	path: string,
	startLine: number,
	endLine: number,
	quote: string,
): EvidenceCitation => ({
	sourceKind: "scm.pull-request.diff",
	artifactPath: "inputs/context/diff.patch",
	path,
	side: "NEW",
	startLine,
	endLine,
	quote,
	quoteRedacted: false,
});

const cited = (sourceKind: string, path: string, quote: string, line = 1): EvidenceCitation => ({
	sourceKind,
	artifactPath: `inputs/context/${sourceKind.replace(/\./g, "-")}.json`,
	path,
	startLine: line,
	endLine: line,
	quote,
	quoteRedacted: false,
});

/**
 * The one long body in the set, because "does long feedback survive the screen" is a question no
 * fixture of one-sentence previews can answer.
 *
 * Shaped the way `DeliveryComposer` shapes a real note: a lead line, bold finding headings with an
 * inline-code locator, a fenced quote of the code, an italic why-this-matters, and a rule between
 * findings. At roughly 1,700 characters it is a middling real note, not an outlier — and it is over
 * five times the 320 characters the list preview gets.
 */
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
 * Seven reviews of five people's work, spanning every conclusion a review can reach and every
 * outcome a piece of feedback can have.
 *
 * <p>Withheld feedback appears once under each of the four families an operator can filter by, so
 * the reason column is never one sentence repeated. The set is deliberately ordinary: most of it was
 * delivered, one review is still running and one failed, which is roughly what a workspace looks
 * like on a Tuesday.
 */
export const REVIEW_FIXTURE: RunSpec[] = [
	{
		id: "11111111-1111-1111-1111-111111111111",
		work: reviewArtifact,
		status: "COMPLETED",
		startedAt: "2026-07-28T13:35:00Z",
		developer: ada,
		observations: [
			{
				id: "55555555-5555-5555-5555-555555555555",
				title: "The controller delegates before it does anything else",
				reasoning:
					"The handler is three statements long: it binds the request, calls ReviewQueryService and maps the result. The caching decision that arrived with this change sits entirely inside the service, so it can be exercised without standing up HTTP.",
				practiceSlug: "thin-controllers",
				area: "code-quality",
				presence: "PRESENT",
				assessment: "GOOD",
				confidence: 0.94,
				observedAt: "2026-07-28T13:40:00Z",
				evidence: [
					diff(
						"server/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryController.java",
						61,
						64,
						"var page = reviewQueryService.observations(workspaceSlug, filter, pageable);\nreturn ResponseEntity.ok(page.map(ReviewObservationDTO::from));",
					),
				],
			},
			{
				id: "66666666-6666-6666-6666-666666666666",
				title: "A cache miss and a permission failure come back as the same 404",
				reasoning:
					"findVisible returns an empty Optional both when the row does not exist and when the caller has no grant on the workspace, and the only caller turns either into a NotFoundException. Two situations that need different answers — retry the link, or ask for access — arrive as one.",
				practiceSlug: "errors-carry-context",
				area: "code-quality",
				presence: "PRESENT",
				assessment: "BAD",
				severity: "MAJOR",
				confidence: 0.81,
				observedAt: "2026-07-28T13:39:00Z",
				evidence: [
					diff(
						"server/src/main/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryService.java",
						116,
						119,
						"return repository.findVisible(workspaceId, id)\n    .orElseThrow(() -> new NotFoundException());",
					),
					cited(
						"scm.repository.tree",
						"server/src/main/java/de/tum/cit/aet/hephaestus/core/security/WorkspaceGuard.java",
						'throw new ForbiddenException("No grant on workspace " + slug);',
						88,
					),
					cited(
						"scm.review-threads",
						"Review thread on ReviewQueryService.java",
						"Do we lose the difference between 'gone' and 'not yours' here?",
					),
				],
			},
			{
				id: "77777777-7777-7777-7777-777777777777",
				title: "Three of the new tests are named after the method they call",
				reasoning:
					"testCache1, testCache2 and testCache3 pin down a cold read, a warm read and an eviction after a membership change. The names carry none of that, so a red build names a file and a number rather than the behaviour that broke.",
				practiceSlug: "tests-name-the-behaviour",
				area: "testing",
				presence: "PRESENT",
				assessment: "BAD",
				severity: "MINOR",
				confidence: 0.76,
				observedAt: "2026-07-28T13:38:00Z",
				evidence: [
					diff(
						"server/src/test/java/de/tum/cit/aet/hephaestus/practices/reviewoutput/ReviewQueryServiceTest.java",
						44,
						46,
						"@Test\nvoid testCache1() {",
					),
				],
			},
			{
				id: "88888888-8888-8888-8888-888888888888",
				title: "The description lists the files touched and stops there",
				reasoning:
					"The body of the pull request is a bullet per changed file. Nothing in it says what was slow, how slow, or why a cache was the answer rather than a narrower query — the questions a reviewer who was not in that conversation has to ask before they can agree.",
				practiceSlug: "the-change-explains-itself",
				area: "documentation",
				presence: "ABSENT",
				assessment: "BAD",
				severity: "INFO",
				confidence: 0.68,
				observedAt: "2026-07-28T13:37:00Z",
				evidence: [
					cited(
						"scm.pull-request.core",
						"Pull request description",
						"- ReviewQueryService.java\n- ReviewQueryController.java\n- ReviewQueryServiceTest.java",
					),
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
		observations: [
			{
				id: "bbbbbbbb-2222-2222-2222-222222222222",
				title: "Invoice numbering leaks the ledger's table name into the public API",
				reasoning:
					"The response field is called ledgerSeqNo, which is the column the number is stored in. Callers outside billing have to learn the storage layout to read an invoice, and the day the ledger is replaced the field is either wrong or frozen.",
				practiceSlug: "product-language",
				area: "architecture",
				presence: "PRESENT",
				assessment: "BAD",
				severity: "CRITICAL",
				confidence: 0.71,
				claimCurrentness: "STALE",
				observedAt: "2026-07-27T09:15:00Z",
				evidence: [
					diff(
						"src/main/java/platform/billing/api/InvoiceResponse.java",
						22,
						22,
						"private Long ledgerSeqNo;",
					),
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
				title: "The change touches no controller",
				reasoning:
					"Everything in the merge request sits under the billing domain package. There is no request handler in the diff, so this practice has nothing to look at here.",
				practiceSlug: "thin-controllers",
				area: "code-quality",
				presence: "NOT_APPLICABLE",
				confidence: 0.62,
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
		observations: [
			{
				id: "eeeeeeee-3333-3333-3333-333333333333",
				title: "The thread ends without naming what was chosen",
				reasoning:
					"Four people weighed two rollback strategies over eleven messages and the last one is a thumbs-up. Nothing in the thread states which strategy won, so a reader arriving tomorrow cannot tell agreement from the end of the working day.",
				practiceSlug: "decisions-are-written-down",
				area: "collaboration",
				presence: "INCONCLUSIVE",
				confidence: 0.4,
				claimCurrentness: "UNVERIFIABLE",
				observedAt: "2026-07-26T16:02:00Z",
				evidence: [
					cited(
						"slack.conversation.thread",
						"Message from Grace Hopper, 16:41",
						"either works honestly, whatever the on-call prefers",
						11,
					),
					cited(
						"hephaestus.observation-history",
						"Earlier observation on this practice",
						"An earlier thread in the same channel also closed without a written decision.",
					),
				],
			},
			{
				id: "ffffffff-3333-3333-3333-333333333333",
				title: "The rollback is described in the words the on-call would use",
				reasoning:
					"Every message names the customer-visible effect — prices reverting, invoices reissuing — rather than the tables involved. Somebody paged at two in the morning could act on this thread without opening the schema.",
				practiceSlug: "product-language",
				area: "architecture",
				presence: "PRESENT",
				assessment: "GOOD",
				confidence: 0.83,
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
				channel: "CONVERSATION",
				outcome: "PREPARED",
				recipient: grace,
				composedAt: "2026-07-26T16:05:00Z",
				from: ["eeeeeeee-3333-3333-3333-333333333333"],
			},
			{
				id: "22222222-4444-4444-4444-444444444444",
				body: "You described the rollback in terms of what a customer would see rather than which tables move. That is the version somebody paged at 2am can act on.",
				channel: "CONVERSATION",
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
		observations: [
			{
				id: "11111111-5555-5555-5555-555555555555",
				title: "The runbook opens with the one step that cannot be undone",
				reasoning:
					"Step one puts the workspace into maintenance before anything is restored, and the page says so in the first line rather than in a note at the bottom. A reader following the page top to bottom does the irreversible thing at the point where it is still safe.",
				practiceSlug: "the-change-explains-itself",
				area: "documentation",
				presence: "PRESENT",
				assessment: "GOOD",
				confidence: 0.91,
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
		],
	},
	{
		id: "55555555-6666-6666-6666-666666666666",
		work: webhookRetryPullRequest,
		status: "COMPLETED",
		startedAt: "2026-07-29T08:05:00Z",
		developer: katherine,
		observations: [
			{
				id: "44444444-6666-6666-6666-666666666666",
				title: "A dropped delivery is logged at debug and never counted",
				reasoning:
					"When the backoff gives up, the handler writes a debug line and returns. Nothing increments a counter and nothing reaches the dead-letter subject, so a provider outage looks identical to a quiet afternoon on every dashboard the team has.",
				practiceSlug: "errors-carry-context",
				area: "code-quality",
				presence: "PRESENT",
				assessment: "BAD",
				severity: "MAJOR",
				confidence: 0.88,
				observedAt: "2026-07-29T08:12:00Z",
				evidence: [
					diff(
						"server/src/main/java/de/tum/cit/aet/hephaestus/integration/core/webhook/WebhookRetryPolicy.java",
						74,
						75,
						'log.debug("giving up on {} after {} attempts", subject, attempts);\nreturn;',
					),
					cited(
						"workspace.project-inventory",
						"Observability stack",
						"Micrometer counters are exported to Prometheus; there is no log-based alerting.",
					),
				],
			},
			{
				id: "55555555-7777-7777-7777-777777777777",
				title: "The retry tests read as sentences about the backoff",
				reasoning:
					"Each of the four new tests is named for the behaviour it fixes in place — that the delay doubles, that it stops at the ceiling, that a success resets it, that a 4xx is not retried. A red build points straight at which of the four rules broke.",
				practiceSlug: "tests-name-the-behaviour",
				area: "testing",
				presence: "PRESENT",
				assessment: "GOOD",
				confidence: 0.92,
				observedAt: "2026-07-29T08:11:00Z",
				evidence: [
					diff(
						"server/src/test/java/de/tum/cit/aet/hephaestus/integration/core/webhook/WebhookRetryPolicyTest.java",
						31,
						32,
						"@Test\nvoid doublesTheDelayUntilItReachesTheCeiling() {",
					),
				],
			},
			{
				id: "66666666-7777-7777-7777-777777777777",
				title: "The queue is called the outbox everywhere except in the config",
				reasoning:
					"The class, the metric and the log lines all say outbox. The property is hephaestus.webhook.retry-buffer.*, so an operator reading a dashboard and an operator editing configuration are looking for two different words for one thing.",
				practiceSlug: "product-language",
				area: "architecture",
				presence: "PRESENT",
				assessment: "BAD",
				severity: "MINOR",
				confidence: 0.79,
				observedAt: "2026-07-29T08:10:00Z",
				evidence: [
					diff(
						"server/src/main/resources/application.yml",
						118,
						118,
						"retry-buffer:\n  capacity: 5000",
					),
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
					path: "server/src/main/resources/application.yml",
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
		observations: [],
		feedback: [],
	},
	{
		id: "bbbbbbbb-8888-8888-8888-888888888888",
		work: invoiceBackfillMergeRequest,
		status: "FAILED",
		startedAt: "2026-07-24T15:02:00Z",
		developer: alan,
		observations: [],
		feedback: [],
	},
];

// ---------------------------------------------------------------------------------------------
// Derivations
// ---------------------------------------------------------------------------------------------

/**
 * What the server puts in `bodyPreview` and `bodyTruncated`: the leading 320 characters of the
 * stored body, and whether there was more.
 *
 * Written out here rather than hand-typed onto each record, because the previous fixture set
 * `bodyTruncated: false` on every row and gave each one a tidy one-sentence preview. Real bodies are
 * Markdown a couple of thousand characters long, so the row never met the case it exists to show —
 * which is how "what about longer feedback?" went unanswered for a whole review round.
 */
const BODY_PREVIEW_LENGTH = 320;

function preview(body: string | undefined) {
	return {
		bodyPreview: body?.slice(0, BODY_PREVIEW_LENGTH),
		bodyTruncated: (body?.length ?? 0) > BODY_PREVIEW_LENGTH,
	};
}

const allRuns = REVIEW_FIXTURE;
const allObservationSpecs = allRuns.flatMap((run) =>
	run.observations.map((observation) => ({ run, observation })),
);
const allFeedbackSpecs = allRuns.flatMap((run) => run.feedback.map((item) => ({ run, item })));

/** The feedback drawing on one observation, newest first, as the detail surfaces list it. */
function feedbackFor(observationId: string) {
	return allFeedbackSpecs
		.filter(({ item }) => item.from.includes(observationId))
		.sort((a, b) => b.item.composedAt.localeCompare(a.item.composedAt));
}

function disposition(observationId: string) {
	const counts = { delivered: 0, failed: 0, prepared: 0, superseded: 0, suppressed: 0 };
	for (const { item } of feedbackFor(observationId)) {
		const key = {
			DELIVERED: "delivered",
			FAILED: "failed",
			PREPARED: "prepared",
			SUPERSEDED: "superseded",
			SUPPRESSED: "suppressed",
		}[item.outcome] as keyof typeof counts;
		counts[key] += 1;
	}
	return counts;
}

function toObservation(run: RunSpec, spec: ObservationSpec): ReviewObservation {
	const practice = workspacePractices.find((entry) => entry.slug === spec.practiceSlug);
	if (!practice) throw new Error(`No practice named ${spec.practiceSlug} in the fixture catalogue`);
	return {
		id: spec.id,
		agentJobId: run.id,
		artifact: run.work,
		area: area(spec.area),
		assessment: spec.assessment,
		claimCurrentness: spec.claimCurrentness ?? "CURRENT",
		confidence: spec.confidence,
		feedbackDisposition: disposition(spec.id),
		observedAt: new Date(spec.observedAt),
		origin: run.origin ?? "LIVE",
		practiceName: practice.name,
		practiceSlug: practice.slug,
		presence: spec.presence,
		severity: spec.severity,
		subject: run.developer,
		title: spec.title,
	};
}

function toFeedback(run: RunSpec, spec: FeedbackSpec): ReviewFeedback {
	return {
		id: spec.id,
		agentJobId: run.id,
		artifact: run.work,
		...preview(spec.body),
		channel: spec.channel,
		createdAt: new Date(spec.composedAt),
		deliveredAt: spec.deliveredAt ? new Date(spec.deliveredAt) : undefined,
		deliveryState: spec.outcome,
		observationCount: spec.from.length,
		recipient: spec.recipient,
		replacesId: spec.replaces,
		subject: run.developer,
		suppressionReason: spec.withheldFor,
	};
}

/** Twelve observations, newest first, as the list endpoint returns them. */
export const reviewObservations: ReviewObservation[] = allObservationSpecs
	.map(({ run, observation }) => toObservation(run, observation))
	.sort((a, b) => b.observedAt.getTime() - a.observedAt.getTime());

/** Eleven pieces of feedback, newest first, as the list endpoint returns them. */
export const reviewFeedback: ReviewFeedback[] = allFeedbackSpecs
	.map(({ run, item }) => toFeedback(run, item))
	.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

/**
 * The run summaries, with both tallies counted from the records they summarise.
 *
 * A run card that says "3 problems" beside a list of two is the drift this derivation removes.
 */
export const reviewRuns: ReviewRunSummary[] = allRuns
	.map((run) => ({
		id: run.id,
		status: run.status,
		target: run.work,
		createdAt: new Date(run.startedAt),
		observations: {
			strengths: run.observations.filter((o) => o.assessment === "GOOD").length,
			problems: run.observations.filter((o) => o.assessment === "BAD").length,
			notApplicable: run.observations.filter((o) => o.presence === "NOT_APPLICABLE").length,
			inconclusive: run.observations.filter((o) => o.presence === "INCONCLUSIVE").length,
		},
		feedback: {
			delivered: run.feedback.filter((f) => f.outcome === "DELIVERED").length,
			failed: run.feedback.filter((f) => f.outcome === "FAILED").length,
			prepared: run.feedback.filter((f) => f.outcome === "PREPARED").length,
			superseded: run.feedback.filter((f) => f.outcome === "SUPERSEDED").length,
			suppressed: run.feedback.filter((f) => f.outcome === "SUPPRESSED").length,
		},
	}))
	.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());

const JOB_TYPE_BY_ARTIFACT: Record<string, AgentJob["jobType"]> = {
	"scm.pull_request": "PULL_REQUEST_REVIEW",
	"scm.issue": "ISSUE_REVIEW",
	"chat.conversation_thread": "CONVERSATION_REVIEW",
	"docs.document": "DOCUMENT_REVIEW",
};

/**
 * The job record behind one review, for the screen that reports how the review itself went.
 *
 * The review detail page takes its heading from the *job's* target and its rows from the review
 * endpoints. Its stories used to take the job from the agent fixtures and the rows from these, so
 * the header named one pull request while every row underneath named another — on the one screen
 * whose job is to show a single review whole.
 */
export function reviewJob(runId: string): AgentJob {
	const run = allRuns.find((entry) => entry.id === runId);
	if (!run) throw new Error(`No review ${runId} in the fixture`);
	const started = new Date(run.startedAt);
	const finished = run.status === "RUNNING" ? undefined : new Date(started.getTime() + 5 * 60_000);
	const delivered = run.feedback.some(
		(item) => item.outcome === "DELIVERED" && item.channel === "IN_CONTEXT",
	);
	return {
		id: run.id,
		jobType: JOB_TYPE_BY_ARTIFACT[run.work.type] ?? "PULL_REQUEST_REVIEW",
		reviewOutcome: "REVIEWED",
		target: run.work,
		status: run.status,
		model: "gpt-5.4-mini",
		configSnapshot: { name: "Default reviewer", llmProvider: "OPENAI" },
		createdAt: started,
		availableAt: started,
		startedAt: started,
		completedAt: finished,
		deliveryStatus: run.status !== "COMPLETED" ? undefined : delivered ? "DELIVERED" : "PENDING",
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

/** The full record behind one observation, including the evidence and the feedback it drove. */
export function observationDetail(observationId: string): ReviewObservationDetail {
	const found = allObservationSpecs.find(({ observation }) => observation.id === observationId);
	if (!found) throw new Error(`No observation ${observationId} in the fixture`);
	const { run, observation } = found;
	// The detail carries the same fields as the list row minus the tally, which it replaces with the
	// feedback records the tally was counting.
	const { feedbackDisposition: _tally, ...shared } = toObservation(run, observation);
	return {
		...shared,
		evidence: observation.evidence ? { citations: observation.evidence } : undefined,
		feedback: feedbackFor(observationId).map(({ item }) => ({
			feedbackId: item.id,
			agentJobId: run.id,
			channel: item.channel,
			createdAt: new Date(item.composedAt),
			deliveredAt: item.deliveredAt ? new Date(item.deliveredAt) : undefined,
			deliveryState: item.outcome,
			role: item.from[0] === observationId ? "PRIMARY" : "SUPPORTING",
			suppressionReason: item.withheldFor,
		})),
		reasoning: observation.reasoning,
	};
}

/** The full record behind one piece of feedback, including its body and its source observations. */
export function feedbackDetail(feedbackId: string): ReviewFeedbackDetail {
	const found = allFeedbackSpecs.find(({ item }) => item.id === feedbackId);
	if (!found) throw new Error(`No feedback ${feedbackId} in the fixture`);
	const { run, item } = found;
	return {
		id: item.id,
		agentJobId: run.id,
		artifact: run.work,
		body: item.body,
		channel: item.channel,
		createdAt: new Date(item.composedAt),
		deliveredAt: item.deliveredAt ? new Date(item.deliveredAt) : undefined,
		deliveryState: item.outcome,
		observations: item.from.map((observationId, ordinal) => {
			const source = observationDetail(observationId);
			return {
				observationId,
				area: source.area,
				assessment: source.assessment,
				claimCurrentness: source.claimCurrentness,
				confidence: source.confidence,
				observedAt: source.observedAt,
				ordinal,
				practiceName: source.practiceName,
				practiceSlug: source.practiceSlug,
				presence: source.presence,
				role: ordinal === 0 ? "PRIMARY" : "SUPPORTING",
				severity: source.severity,
				title: source.title,
			};
		}),
		// Only feedback that reached the work has a placement, and an inline one carries the anchor the
		// detail page prints as `path:12–18`. Deriving it from the outcome keeps a withheld record from
		// claiming a place it never occupied.
		placements:
			item.outcome !== "DELIVERED" || item.channel !== "IN_CONTEXT"
				? []
				: item.anchoredAt
					? [
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
						]
					: [{ id: `${item.id}-summary`, placementType: "SUMMARY", postedCommentRef: "2481933" }],
		recipient: item.recipient,
		replacesId: item.replaces,
		subject: run.developer,
		suppressionReason: item.withheldFor,
	};
}

/** The withheld piece of feedback the detail stories open by default. */
export const reviewFeedbackDetail: ReviewFeedbackDetail = feedbackDetail(
	"dddddddd-2222-2222-2222-222222222222",
);

/** The long, multi-observation note — the one that answers "what happens to longer feedback?". */
export const longFeedbackDetail: ReviewFeedbackDetail = feedbackDetail(
	"44444444-4444-4444-4444-444444444444",
);

/** An observation whose evidence spans a diff, a repository file and a human review thread. */
export const reviewObservationDetail: ReviewObservationDetail = observationDetail(
	"66666666-6666-6666-6666-666666666666",
);

// ---------------------------------------------------------------------------------------------
// A page that does not fit on one page
// ---------------------------------------------------------------------------------------------

/**
 * The same twelve observations restated across as many days as it takes to fill `count` rows.
 *
 * A screen with a page size of 25 has no pagination until it has more than 25 rows, and a filter is
 * hard to believe on a list short enough to check by eye. Cycling the real specs keeps every row a
 * record the server could have produced, unlike a generated `Observation #17`.
 */
export function manyObservations(count: number): ReviewObservation[] {
	const base = reviewObservations;
	return Array.from({ length: count }, (_, index) => {
		const source = base[index % base.length];
		const cycle = Math.floor(index / base.length);
		if (cycle === 0) return source;
		return {
			...source,
			id: `${source.id.slice(0, -2)}${(10 + cycle).toString(36)}`,
			observedAt: new Date(source.observedAt.getTime() - cycle * 86_400_000),
		};
	});
}

/**
 * A workspace with more people in it than the members endpoint returns in one page.
 *
 * The person facet asks for 100 and filters what came back in the browser, because the endpoint
 * takes no name. At exactly this size the facet has to say so — a search box that answers "No
 * matches" for a colleague who is simply the 140th member is a screen telling an operator something
 * untrue about their own workspace.
 */
export function manyMembers(count: number): WorkspaceMembership[] {
	return Array.from({ length: count }, (_, index) => {
		const source = workspaceMembers[index % workspaceMembers.length];
		const cycle = Math.floor(index / workspaceMembers.length);
		if (cycle === 0) return source;
		return {
			...source,
			userId: (source.userId ?? 0) + cycle * 100,
			userLogin: `${source.userLogin}-${cycle}`,
			userName: `${source.userName} ${cycle}`,
		};
	});
}

export function manyFeedback(count: number): ReviewFeedback[] {
	const base = reviewFeedback;
	return Array.from({ length: count }, (_, index) => {
		const source = base[index % base.length];
		const cycle = Math.floor(index / base.length);
		if (cycle === 0) return source;
		return {
			...source,
			id: `${source.id.slice(0, -2)}${(10 + cycle).toString(36)}`,
			createdAt: new Date(source.createdAt.getTime() - cycle * 86_400_000),
		};
	});
}
